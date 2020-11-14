package mx.kenzie.commander.exception;

public class CommandRuntimeError extends CommandError {
    
    public CommandRuntimeError() {
    }
    
    public CommandRuntimeError(Throwable ex) {
        super(ex);
    }
    
    public CommandRuntimeError(String s) {
        super(s);
    }
    
}
