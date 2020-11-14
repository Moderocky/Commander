package mx.kenzie.commander.exception;

public class IllegalCommandException extends RuntimeException {
    
    public IllegalCommandException() {
    }
    
    public IllegalCommandException(String s) {
        super(s);
    }
    
}
