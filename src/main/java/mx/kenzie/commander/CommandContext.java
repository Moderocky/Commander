package mx.kenzie.commander;

import mx.kenzie.magic.collection.MagicList;

public record CommandContext<S>(S sender, String command, MagicList<Object> contextVariables, String... arguments) {
    
    protected CommandContext(S sender, String input) {
        this(sender,
            (input.indexOf(' ') == -1) ? input.trim() :
                input.substring(0, input.indexOf(' ')),
            (input.indexOf(' ') == -1) ? new String[0] :
                input.substring(input.indexOf(' ')).trim().split(" "));
    }
    
    public CommandContext(S sender, String command, String... arguments) {
        this(sender, command, new MagicList<>(), arguments);
    }
    
    public static <S> CommandContext<S> build(S sender, String input) {
        final int space = input.indexOf(' ');
        final String label = (space == -1) ? input.trim() : input.substring(0, space);
        final String[] args = (space == -1) ? new String[0] :
            input.substring(space).trim().split(" ");
        return new CommandContext<>(sender, label, args);
    }
    
}
