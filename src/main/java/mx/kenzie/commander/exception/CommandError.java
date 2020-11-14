package mx.kenzie.commander.exception;

public class CommandError extends Error {
    
    public CommandError() {
    }
    
    public CommandError(Throwable ex) {
        super(ex);
    }
    
    public CommandError(String s) {
        super(s);
    }
    
}
