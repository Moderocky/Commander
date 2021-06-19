package mx.kenzie.commander;

import mx.kenzie.commander.arg.ArgLiteral;
import mx.kenzie.commander.arg.ArgLiteralPlural;
import mx.kenzie.commander.arg.Argument;
import mx.kenzie.commander.exception.CommandParseError;
import mx.kenzie.commander.exception.CommandRuntimeError;
import mx.kenzie.commander.exception.IllegalCommandException;
import mx.kenzie.magic.collection.MagicList;
import mx.kenzie.magic.collection.MagicMap;
import mx.kenzie.magic.collection.MagicStringList;
import mx.kenzie.magic.magic.StringReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class Commander<S> {
    
    protected final MagicStringList aliases = new MagicStringList();
    final MagicStringList patterns = new MagicStringList();
    final MagicMap<String, @Nullable String> patternDescriptions = new MagicMap<>();
    final ArgumentTree tree = new ArgumentTree();
    protected CommandSingleAction<S> defaultAction;
    protected Function<CommandContext<S>, Boolean> predicate;
    protected Consumer<CommandContext<S>> failureAction;
    protected BiConsumer<CommandContext<S>, Throwable> error = null;
    protected ExecutorService pool;
    protected String description;
    private String namespace;
    private volatile String input;
    
    {
        pool = null;
        predicate = s -> true;
        defaultAction = getDefault();
        failureAction = context -> defaultAction.accept(context.sender());
        compile();
    }
    
    final void compile() {
        patterns.clear();
        patternDescriptions.clear();
        aliases.clear();
        tree.clear();
        create();
        final String regex = "^[^\\[<\\n]+((?> [<\\[]\\S+[]>])+)$";
        Pattern pattern = Pattern.compile(regex);
        {
            MagicList<String> list = new MagicList<>(getPossibleArguments());
            MagicList<String> catcher = new MagicList<>(list);
            catcher.removeIf(s -> !s.contains("<") && !s.contains("["));
            catcher.removeIf(s -> !s.matches(regex));
            for (String string : new ArrayList<>(catcher)) {
                Matcher matcher = pattern.matcher(string);
                if (!matcher.find()) continue;
                String group = matcher.group(1).trim();
                String test = string.replace(group, "").trim();
                boolean matches = false;
                for (String str : new ArrayList<>(list)) {
                    if (str.trim().equalsIgnoreCase(test)) {
                        matches = true;
                        list.remove(str);
                    }
                }
                if (matches) {
                    list.remove(string);
                    list.add(group.contains("<") ? (test + " [" + group + "]") : (test + " " + group));
                }
            }
            patterns.addAll(list);
            Collections.sort(patterns);
        }
        {
            MagicMap<String, String> map = new MagicMap<>(getPatternDesc());
            MagicMap<String, String> catcher = new MagicMap<>(map);
            catcher.removeIf(s -> !s.contains("<") && !s.contains("["));
            catcher.removeIf(s -> !s.matches(regex));
            for (String string : new ArrayList<>(catcher.keySet())) {
                Matcher matcher = pattern.matcher(string);
                if (!matcher.find()) continue;
                String group = matcher.group(1).trim();
                String test = string.replace(group, "").trim();
                boolean matches = false;
                for (String str : new ArrayList<>(map.keySet())) {
                    if (str.trim().equalsIgnoreCase(test)) {
                        matches = true;
                        map.remove(str);
                    }
                }
                if (matches) {
                    String desc = map.remove(string);
                    map.put(group.contains("<") ? (test + " [" + group + "]") : (test + " " + group), desc);
                }
            }
            patternDescriptions.putAll(map);
        }
    }
    
    public boolean canExecute(CommandContext<S> context) {
        return predicate.apply(context);
    }
    
    public CompletableFuture<Boolean> canExecuteAsync(CommandContext<S> context) {
        return CompletableFuture.completedFuture(canExecute(context));
    }
    
    protected abstract CommandImpl create();
    
    public MagicList<String> getPossibleArguments(String... inputs) {
        if (inputs.length == 0)
            return new MagicList<>(tree.keySet()).collect(entry -> entry.args);
        String input = String.join(" ", inputs);
        MagicList<ArgumentEntry> list = new MagicList<>(tree.keySet());
        list.removeIf(entry -> !entry.matches(input));
        return list.collect(entry -> entry.args);
    }
    
    private @NotNull Map<@NotNull String, @Nullable String> getPatternDesc() {
        Map<@NotNull String, @Nullable String> map = new MagicMap<>();
        for (ArgumentEntry entry : tree.keySet()) {
            map.put(entry.args, entry.description);
        }
        return map;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean matchesLabel(final CommandContext<S> context) {
        return namespace.equalsIgnoreCase(context.command()) || aliases.containsIgnoreCase(context.command());
    }
    
    public boolean execute(S sender, String command, String... args) {
        return execute(new CommandContext<>(sender, command, args));
    }
    
    public boolean execute(final CommandContext<S> context) {
        final boolean async = (pool != null);
        final Runnable executor = prepareCommandExecution(context);
        try {
            if (async) canExecuteAsync(context)
                .thenAcceptAsync(boo -> {
                    if (boo) executor.run();
                    else failureAction.accept(context);
                }, pool);
            else if (canExecute(context))
                executor.run();
            else failureAction.accept(context);
            return true;
        } catch (Throwable throwable) {
            if (error == null) throw new CommandParseError(throwable);
            else error.accept(context, throwable);
            return false;
        }
    }
    
    protected Runnable prepareCommandExecution(final CommandContext<S> context) {
        final S sender = context.sender();
        final String[] inputs = context.arguments();
        try {
            input = String.join(" ", inputs);
            final MagicList<Map.Entry<ArgumentEntry, CommandAction<S>>> list = new MagicList<>(tree.entrySet());
            final MagicMap<Map.Entry<ArgumentEntry, CommandAction<S>>, ArgumentEntry.Result> map = new MagicMap<>();
            for (Map.Entry<ArgumentEntry, CommandAction<S>> entry : list) {
                final ArgumentEntry.Result result = entry.getKey().matchesEntry(input);
                if (result == ArgumentEntry.Result.FALSE) continue;
                map.put(entry, result);
            }
            final Runnable executor;
            if (!map.isEmpty()) {
                final ArgumentEntry entry;
                final CommandAction<S> action;
                final MagicMap<Map.Entry<ArgumentEntry, CommandAction<S>>, ArgumentEntry.Result> perfect = new MagicMap<>(map);
                perfect.entrySet().removeIf(e -> e.getValue() == ArgumentEntry.Result.TRAILING);
                if (perfect.isEmpty()) {
                    entry = map.getFirst().getKey().getKey();
                    action = map.getFirst().getKey().getValue();
                } else {
                    entry = perfect.getFirst().getKey().getKey();
                    action = perfect.getFirst().getKey().getValue();
                }
                final Object[] values = (action instanceof CommandSingleAction) ? new Object[0]
                    : entry.compileEntry(input);
                executor = () -> {
                    try {
                        action.accept(sender, values);
                    } catch (Throwable ex) {
                        throw new CommandRuntimeError(ex);
                    }
                };
            } else {
                executor = () -> {
                    try {
                        defaultAction.accept(sender);
                    } catch (Throwable ex) {
                        throw new CommandRuntimeError(ex);
                    }
                };
            }
            return executor;
        } catch (Throwable throwable) {
            if (error == null) throw new CommandParseError(throwable);
            else error.accept(context, throwable);
            return null;
        }
    }
    
    public CompletableFuture<Void> executeAsync(S sender, String command, String... args) {
        return executeAsync(new CommandContext<>(sender, command, args));
    }
    
    public CompletableFuture<Void> executeAsync(final CommandContext<S> context) {
        final boolean async = (pool != null);
        if (!async)
            throw new IllegalCommandException("Cannot dispatch asynchronous command execution without a thread pool!");
        final Runnable executor = prepareCommandExecution(context);
        try {
            return canExecuteAsync(context)
                .thenAcceptAsync(boo -> {
                    if (boo) executor.run();
                    else failureAction.accept(context);
                }, pool);
        } catch (Throwable throwable) {
            if (error == null) throw new CommandParseError(throwable);
            else error.accept(context, throwable);
            return CompletableFuture.completedFuture(null);
        }
    }
    
    public @NotNull Collection<String> getPatterns() {
        return new ArrayList<>(patterns);
    }
    
    public @NotNull Map<@NotNull String, @Nullable String> getPatternDescriptions() {
        return new HashMap<>(patternDescriptions);
    }
    
    public MagicList<String> getNextArguments(String... inputs) {
        if (inputs.length == 0)
            return new MagicList<>(tree.keySet()).collect(entry -> entry.getArgs(inputs.length + 1));
        String input = String.join(" ", inputs);
        MagicList<ArgumentEntry> list = new MagicList<>(tree.keySet());
        list.removeIf(entry -> !entry.matches(input));
        return list.collect(entry -> entry.getArgs(inputs.length + 1));
    }
    
    public MagicList<String> getNextCompletions(String... inputs) {
        final MagicList<String> completions = new MagicList<>();
        if (inputs.length == 0)
            for (ArgumentEntry arguments : tree.keySet()) {
                completions.addAll(arguments.getCompletion(inputs.length));
            }
        else {
            MagicList<ArgumentEntry> list = new MagicList<>(tree.keySet());
            for (ArgumentEntry entry : list) {
                completions.addAll(entry.getCompletions(inputs));
            }
        }
        completions.removeDuplicates();
        return completions;
    }
    
    public String getInput() {
        return input;
    }
    
    protected CommandImpl command(String namespace, String... aliases) {
        this.namespace = namespace;
        this.aliases.addAll(aliases);
        return new CommandImpl();
    }
    
    public CommandSingleAction<S> getDefault() {
        return sender -> {
        };
    }
    
    public Description desc(@Nullable String string) {
        return string != null ? new Description(string) : null;
    }
    
    public SubArg arg(String arg, CommandSingleAction<S> action) {
        return new SubArg(new ArgLiteral(arg), action);
    }
    
    public SubArg arg(String arg, Description description, CommandSingleAction<S> action) {
        SubArg subArg = new SubArg(new ArgLiteral(arg), action);
        subArg.description = description;
        return subArg;
    }
    
    @SafeVarargs
    public final SubArg arg(String arg, SubArg... args) {
        return new SubArg(new ArgLiteral(arg), null, args);
    }
    
    @SafeVarargs
    public final SubArg arg(String @NotNull [] arg, SubArg... args) {
        return new SubArg(new ArgLiteralPlural(arg), null, args);
    }
    
    @SafeVarargs
    public final SubArg arg(String @NotNull [] arg, CommandSingleAction<S> action, SubArg... args) {
        return new SubArg(new ArgLiteralPlural(arg), action, args);
    }
    
    @SafeVarargs
    public final SubArg arg(String @NotNull [] arg, Description description, CommandSingleAction<S> action, SubArg... args) {
        SubArg subArg = new SubArg(new ArgLiteralPlural(arg), action, args);
        subArg.description = description;
        return subArg;
    }
    
    @SafeVarargs
    public final SubArg arg(String arg, CommandSingleAction<S> action, SubArg... args) {
        return new SubArg(new ArgLiteral(arg), action, args);
    }
    
    @SafeVarargs
    public final SubArg arg(String arg, Description description, CommandSingleAction<S> action, SubArg... args) {
        SubArg subArg = new SubArg(new ArgLiteral(arg), action, args);
        subArg.description = description;
        return subArg;
    }
    
    public SubArg arg(CommandSingleAction<S> action, String... arg) {
        return new SubArg(new ArgLiteralPlural(arg), action);
    }
    
    public SubArg arg(CommandSingleAction<S> action, Description description, String... arg) {
        SubArg subArg = new SubArg(new ArgLiteralPlural(arg), action);
        subArg.description = description;
        return subArg;
    }
    
    public SubArg arg(CommandBiAction<S> action, @NotNull Argument<?>... arguments) {
        return arg(null, action, arguments);
    }
    
    public SubArg arg(Description description, CommandBiAction<S> action, @NotNull Argument<?>... arguments) {
        if (arguments.length == 0) throw new IllegalArgumentException("No arguments were provided!");
        SubArg top = null;
        SubArg arg = null;
        for (Argument<?> argument : arguments) {
            SubArg current = new SubArg(argument, (CommandAction<S>) null);
            if (top == null) top = current;
            if (arg != null) {
                arg.children.add(current);
            }
            arg = current;
        }
        arg.action = action;
        arg.description = description;
        return top;
    }
    
    public @NotNull String getCommand() {
        return namespace;
    }
    
    public @NotNull List<String> getAliases() {
        return aliases;
    }
    
    interface CommandAction<S> {
        default void execute(S sender, Object[] inputs) {
            if (isSingle()) accept(sender);
            else accept(sender, inputs);
        }
        
        boolean isSingle();
        
        void accept(S sender);
        
        void accept(S sender, Object[] object);
        
    }
    
    @FunctionalInterface
    public interface CommandSingleAction<S> extends CommandAction<S> {
        default boolean isSingle() {
            return true;
        }
        
        void accept(S sender);
        
        @Override
        default void accept(S sender, Object[] inputs) {
            accept(sender);
        }
    }
    
    @FunctionalInterface
    public interface CommandBiAction<S> extends CommandAction<S> {
        default boolean isSingle() {
            return false;
        }
        
        @Override
        default void accept(S sender) {
            accept(sender, new Object[0]);
        }
        
        void accept(S sender, Object[] inputs);
    }
    
    private static class Description {
        protected final @Nullable String string;
        
        public Description(@Nullable String string) {
            this.string = string;
        }
    }
    
    static class ArgumentEntry extends MagicList<Argument<?>> {
        
        final Pattern pattern;
        final String args;
        String description;
        
        {
            StringBuilder builder = new StringBuilder();
            MagicList<String> strings = new MagicList<>();
            for (Argument<?> argument : this) {
                builder.append(argument.getPattern().toString().replace("^", "").replace("$", "")).append(" ");
                if (argument.isLiteral()) {
                    strings.add(argument.getName());
                } else {
                    strings.add((argument.isRequired() ? "<" : "[") + (argument.isPlural() ? "*" : "") + argument
                        .getName() + (argument.isFinal() ? "..." : "") + (argument.isRequired() ? ">" : "]"));
                }
            }
            pattern = Pattern.compile("^" + builder.toString().trim());
            args = String.join(" ", strings);
        }
        
        public ArgumentEntry(Argument<?>... arguments) {
            super(arguments);
        }
        
        public ArgumentEntry(Collection<Argument<?>> arguments) {
            super(arguments);
        }
        
        public String getArgs(int depth) {
            MagicList<String> strings = new MagicList<>();
            for (Argument<?> argument : this.subList(0, depth)) {
                if (argument.isLiteral()) {
                    strings.add(argument.getName());
                } else {
                    strings.add((argument.isRequired() ? "<" : "[") + (argument.isPlural() ? "*" : "") + argument
                        .getName() + (argument.isFinal() ? "..." : "") + (argument.isRequired() ? ">" : "]"));
                }
            }
            return String.join(" ", strings);
        }
        
        public String getArg(int depth) {
            final Argument<?> argument = this.get(depth);
            if (argument.isLiteral()) {
                return argument.getName();
            } else {
                return (argument.isRequired() ? "<" : "[") + (argument.isPlural() ? "*" : "") + argument
                    .getName() + (argument.isFinal() ? "..." : "") + (argument.isRequired() ? ">" : "]");
            }
        }
        
        public MagicList<String> getCompletion(int depth) {
            final MagicList<String> strings = new MagicList<>();
            if (this.size() <= depth) return strings;
            final Argument<?> argument = this.get(depth);
            if (argument.isLiteral()) {
                strings.add(argument.getName());
            } else {
                final List<String> completions = argument.getCompletions();
                if (completions != null)
                    strings.addAll(completions);
            }
            return strings;
        }
        
        public MagicList<String> getCompletions(String... inputs) {
            final MagicList<String> strings = new MagicList<>();
            if (this.size() < inputs.length) return strings;
            int i = 0;
            String input = "";
            Argument<?> argument = null;
            for (; i < inputs.length; i++) {
                input = inputs[i];
                argument = this.get(i);
                if (!argument.matches(input)) break;
            }
            if (i != inputs.length - 1) {
                return strings;
            }
            if (argument.isLiteral()) {
                strings.add(argument.getName());
            } else {
                final List<String> completions = argument.getCompletions();
                if (completions != null)
                    strings.addAll(completions);
            }
            String finalInput = input;
            strings.removeIf(s -> !s.startsWith(finalInput));
            return strings;
        }
        
        Pattern getPattern() {
            return pattern;
        }
        
        boolean matches(String input) {
            Matcher matcher = pattern.matcher(input.trim());
            return matcher.matches();
        }
        
        public Result matchesEntry(String input) {
            StringReader reader = new StringReader(input);
            for (Argument<?> argument : this) {
                final String segment;
                if (argument.isFinal()) {
                    segment = reader.readRest();
                } else if (argument.acceptSpaces()) {
                    segment = reader.readUntilMatchesAfter(argument.getPattern(), ' ');
                } else {
                    segment = reader.readUntil(' ');
                }
                if (argument.isRequired()) {
                    if (!argument.getPattern().matcher(segment).matches()) return Result.FALSE;
                    if (!argument.matches(segment)) return Result.FALSE;
                }
                reader.skip();
            }
            return reader.readRest().trim().isEmpty() ? Result.TRUE : Result.TRAILING;
        }
        
        public Object[] compileEntry(String input) {
            MagicList<Object> objects = new MagicList<>();
            StringReader reader = new StringReader(input);
            for (Argument<?> argument : this) {
                final String segment;
                if (argument.isFinal()) {
                    segment = reader.readRest();
                } else if (argument.acceptSpaces()) {
                    segment = reader.readUntilMatchesAfter(argument.getPattern(), ' ');
                } else {
                    segment = reader.readUntil(' ');
                }
                if (!argument.isLiteral())
                    objects.add(argument.matches(segment) ? argument.serialise(segment) : null);
                reader.skip();
            }
            return objects.toArray(new Object[0]);
        }
        
        Collection<String> getCompletions(int position, String[] inputs) {
            List<String> list = new ArrayList<>();
            if (position < 2) {
                List<String> strings = get(0).getCompletions();
                if (strings != null)
                    list.addAll(strings);
            } else if (size() > position - 1) check:{
                if (!inputMatches(inputs)) break check;
                List<String> strings = get(position - 1).getCompletions();
                if (strings != null)
                    list.addAll(strings);
            }
            return list;
        }
        
        boolean inputMatches(String[] inputs) {
            StringBuilder builder = new StringBuilder();
            int i = 1;
            for (Argument<?> argument : this) {
                i++;
                if (i == inputs.length) break;
                if (argument instanceof ArgLiteralPlural) {
                    builder
                        .append("(")
                        .append(String.join("|", ((ArgLiteralPlural) argument).aliases))
                        .append(")")
                        .append(" ");
                } else if (argument instanceof ArgLiteral) {
                    builder
                        .append(argument.getName())
                        .append(" ");
                } else if (argument.isRequired()) {
                    if (argument.isFinal())
                        builder
                            .append("(.+)")
                            .append(" ");
                    else
                        builder
                            .append("(\\S+)")
                            .append(" ");
                } else {
                    if (argument.isFinal())
                        builder
                            .append("(.*)")
                            .append(" ");
                    else
                        builder
                            .append("(\\S*)")
                            .append(" ?");
                }
            }
            Pattern pattern = Pattern.compile("^" + builder.toString().toLowerCase().trim() + ".*");
            return pattern.matcher(String.join(" ", inputs).toLowerCase().trim()).matches();
        }
        
        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof ArgumentEntry)) return false;
            ArgumentEntry arguments = (ArgumentEntry) object;
            return Objects.equals(args, arguments.args);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), pattern, args, description);
        }
        
        public enum Result {
            FALSE,
            TRAILING,
            TRUE
        }
        
    }
    
    public class CommandImpl {
        
        private CommandImpl() {
        }
        
        public CommandImpl setDescription(String description) {
            Commander.this.description = description;
            return this;
        }
        
        public CommandImpl setPredicate(Function<CommandContext<S>, Boolean> check) {
            predicate = check;
            return this;
        }
        
        public CommandImpl setFailureBehaviour(Consumer<CommandContext<S>> action) {
            failureAction = action;
            return this;
        }
        
        public CommandImpl allowAsyncExecution() {
            pool = Executors.newCachedThreadPool();
            return this;
        }
        
        public CommandImpl allowAsyncExecution(ExecutorService dispatcher) {
            pool = dispatcher;
            return this;
        }
        
        public CommandImpl defaultAction(CommandSingleAction<S> action) {
            defaultAction = action;
            return this;
        }
        
        public CommandImpl arg(String arg, CommandSingleAction<S> action) {
            return arg(arg, null, action);
        }
        
        public CommandImpl arg(String arg, Description description, CommandSingleAction<S> action) {
            ArgumentEntry entry = new ArgumentEntry(new ArgLiteral(arg));
            if (description != null) entry.description = description.string;
            tree.put(entry, action);
            return this;
        }
        
        public CommandImpl arg(CommandSingleAction<S> action, String arg) {
            return arg(action, arg, (Description) null);
        }
        
        public CommandImpl arg(CommandSingleAction<S> action, String arg, Description description) {
            ArgumentEntry entry = new ArgumentEntry(new ArgLiteral(arg));
            if (description != null) entry.description = description.string;
            tree.put(entry, action);
            return this;
        }
        
        public CommandImpl arg(CommandSingleAction<S> action, String... args) {
            ArgumentEntry entry = new ArgumentEntry(new MagicList<>(args).collect(ArgLiteral::new));
            tree.put(entry, action);
            return this;
        }
        
        public CommandImpl arg(CommandSingleAction<S> action, Description description, String... args) {
            ArgumentEntry entry = new ArgumentEntry(new MagicList<>(args).collect(ArgLiteral::new));
            if (description != null) entry.description = description.string;
            tree.put(entry, action);
            return this;
        }
        
        @SafeVarargs
        public final CommandImpl arg(String @NotNull [] args, SubArg... subArguments) {
            MagicList<Argument<?>> list = new MagicList<>(args).collect(ArgLiteral::new);
            for (SubArg subArg : subArguments) {
                subArg.compile(list);
            }
            return this;
        }
        
        @SafeVarargs
        public final CommandImpl arg(String arg, SubArg... subArguments) {
            MagicList<Argument<?>> list = new MagicList<>(new ArgLiteral(arg));
            for (SubArg subArg : subArguments) {
                subArg.compile(list);
            }
            return this;
        }
        
        @SafeVarargs
        public final CommandImpl arg(String arg, CommandSingleAction<S> action, SubArg... subArguments) {
            return arg(arg, null, action, subArguments);
        }
        
        @SafeVarargs
        public final CommandImpl arg(String arg, Description description, CommandSingleAction<S> action, SubArg... subArguments) {
            ArgumentEntry entry = new ArgumentEntry(new ArgLiteral(arg));
            if (description != null) entry.description = description.string;
            tree.put(entry, action);
            MagicList<Argument<?>> list = new MagicList<>(new ArgLiteral(arg));
            for (SubArg subArg : subArguments) {
                subArg.compile(list);
            }
            return this;
        }
        
        @SafeVarargs
        public final CommandImpl arg(CommandSingleAction<S> action, String arg, SubArg... subArguments) {
            ArgumentEntry entry = new ArgumentEntry(new ArgLiteral(arg));
            tree.put(entry, action);
            MagicList<Argument<?>> list = new MagicList<>(new ArgLiteral(arg));
            for (SubArg subArg : subArguments) {
                subArg.compile(list);
            }
            return this;
        }
        
        @SafeVarargs
        public final CommandImpl arg(CommandSingleAction<S> action, String arg, Description description, SubArg... subArguments) {
            return arg(arg, description, action, subArguments);
        }
        
        @SafeVarargs
        public final CommandImpl arg(CommandSingleAction<S> action, String @NotNull [] arg, SubArg... subArguments) {
            return arg(action, arg, null, subArguments);
        }
        
        @SafeVarargs
        public final CommandImpl arg(CommandSingleAction<S> action, String @NotNull [] arg, Description description, SubArg... subArguments) {
            ArgumentEntry entry = new ArgumentEntry(new ArgLiteralPlural(arg));
            if (description != null) entry.description = description.string;
            tree.put(entry, action);
            MagicList<Argument<?>> list = new MagicList<>(new ArgLiteralPlural(arg));
            for (SubArg subArg : subArguments) {
                subArg.compile(list);
            }
            return this;
        }
        
        public final CommandImpl arg(CommandBiAction<S> action, @NotNull Argument<?>... arguments) {
            return arg(null, action, arguments);
        }
        
        public final CommandImpl arg(Description description, CommandBiAction<S> action, @NotNull Argument<?>... arguments) {
            if (arguments.length == 0) {
                return this;
            }
            SubArg top = null;
            SubArg arg = null;
            for (Argument<?> argument : arguments) {
                SubArg current = new SubArg(argument, (CommandAction<S>) null);
                if (top == null) top = current;
                if (arg != null) {
                    arg.children.add(current);
                }
                arg = current;
            }
            arg.action = action;
            if (description != null) arg.description = description;
            top.compile(new MagicList<>());
            return this;
        }
        
        public CommandImpl arg(Argument<?> argument, CommandBiAction<S> action) {
            return arg(null, argument, action);
        }
        
        public CommandImpl arg(Description description, Argument<?> argument, CommandBiAction<S> action) {
            SubArg top = new SubArg(argument, action);
            top.description = description;
            top.compile(new MagicList<>());
            return this;
        }
        
        public final CommandImpl errorHandler(@Nullable BiConsumer<CommandContext<S>, Throwable> errorFunction) {
            Commander.this.error = errorFunction;
            return this;
        }
        
    }
    
    public class SubArg {
        public final Argument<?> argument;
        public final MagicList<SubArg> children = new MagicList<>();
        public CommandAction<S> action;
        public Description description = null;
        
        public SubArg(Argument<?> argument, CommandAction<S> action) {
            this.argument = argument;
            this.action = action;
        }
        
        public SubArg(Argument<?> argument, Description description, CommandAction<S> action) {
            this.argument = argument;
            this.action = action;
            this.description = description;
        }
        
        @SafeVarargs
        public SubArg(Argument<?> argument, CommandAction<S> action, SubArg... children) {
            this.argument = argument;
            this.action = action;
            this.children.addAll(children);
        }
        
        @SafeVarargs
        public SubArg(Argument<?> argument, Description description, CommandAction<S> action, SubArg... children) {
            this.argument = argument;
            this.action = action;
            this.description = description;
            this.children.addAll(children);
        }
        
        @SafeVarargs
        public SubArg(Argument<?> argument, SubArg... children) {
            this.argument = argument;
            this.action = null;
            this.children.addAll(children);
        }
        
        public void compile(MagicList<Argument<?>> list) {
            list.add(argument);
            if (action != null) {
                ArgumentEntry entry = new ArgumentEntry(list);
                if (description != null) entry.description = description.string;
                tree.put(entry, action);
            }
            if (!children.isEmpty()) {
                for (SubArg child : children) {
                    child.compile(new MagicList<>(list));
                }
            }
        }
    }
    
    class ArgumentTree extends MagicMap<ArgumentEntry, CommandAction<S>> {
    }
    
}
