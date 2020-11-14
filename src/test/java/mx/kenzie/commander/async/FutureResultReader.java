package mx.kenzie.commander.async;

public class FutureResultReader {
    
    public volatile String result;
    
    public synchronized void send(String string) {
        result = string;
    }
    
    public synchronized String getResult() {
        return result;
    }
    
}
