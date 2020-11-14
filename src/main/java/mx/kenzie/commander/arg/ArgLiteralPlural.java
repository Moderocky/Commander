package mx.kenzie.commander.arg;

import mx.kenzie.magic.collection.MagicList;
import mx.kenzie.magic.collection.MagicStringList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;

public class ArgLiteralPlural extends ArgLiteral {
    
    public final MagicStringList aliases = new MagicStringList();
    private final Pattern pattern;
    
    public ArgLiteralPlural(String... aliases) {
        super(aliases[0].toLowerCase());
        this.aliases.addAll(new MagicList<>(aliases).collect(String::toLowerCase));
        pattern = Pattern.compile("^(" + String.join("|", this.aliases) + ")$");
    }
    
    public ArgLiteralPlural(MagicList<String> aliases) {
        super(aliases.getFirst().toLowerCase());
        this.aliases.addAll(aliases.collect(String::toLowerCase));
        pattern = Pattern.compile("^(" + String.join("|", this.aliases) + ")$");
    }
    
    public ArgLiteralPlural(String name, String... aliases) {
        super(name);
        this.aliases.add(name.toLowerCase());
        if (aliases.length > 0) this.aliases.addAll(new MagicList<>(aliases).collect(String::toLowerCase));
        pattern = Pattern.compile("^(" + String.join("|", this.aliases) + ")$");
    }
    
    @NotNull
    @Override
    public Void serialise(String string) {
        return null;
    }
    
    @Override
    public boolean matches(String string) {
        return aliases.contains(string.toLowerCase());
    }
    
    @Override
    public @NotNull String getName() {
        return aliases.getFirst();
    }
    
    @Override
    public @Nullable List<String> getCompletions() {
        return new MagicList<>(aliases);
    }
    
    @Override
    public boolean isPlural() {
        return false;
    }
    
    @Override
    public boolean isRequired() {
        return true;
    }
    
    @Override
    public Argument<Void> setRequired(boolean boo) {
        return this;
    }
    
    @Override
    public Argument<Void> setLabel(@NotNull String name) {
        return this;
    }
    
    @Override
    public Pattern getPattern() {
        return pattern;
    }
}
