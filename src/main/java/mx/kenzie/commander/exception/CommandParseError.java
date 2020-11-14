package mx.kenzie.commander.exception;

public class CommandParseError extends CommandError {
    
    public CommandParseError() {
    }
    
    public CommandParseError(Throwable ex) {
        super(ex);
    }
    
    public CommandParseError(String s) {
        super(s);
    }
    
}
