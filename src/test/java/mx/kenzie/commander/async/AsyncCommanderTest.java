package mx.kenzie.commander.async;

import mx.kenzie.magic.collection.MagicStringList;
import org.junit.Test;

import java.util.concurrent.ExecutionException;

public class AsyncCommanderTest {
    
    final ExampleDispatchCommander commander = new ExampleDispatchCommander();
    final FutureResultReader reader = new FutureResultReader();
    
    @Test
    public void def() throws ExecutionException, InterruptedException {
        commander.executeAsync(reader, "test").get();
        assert reader.getResult().equals("default");
    }
    
    @Test
    public void simpleArgs() throws ExecutionException, InterruptedException {
        commander.executeAsync(reader, "test", "foo").get();
        assert reader.getResult().equals("foo");
        commander.executeAsync(reader, "test", "bar").get();
        assert reader.getResult().equals("bar");
        commander.executeAsync(reader, "test", "blob").get();
        assert reader.getResult().equals("blob");
    }
    
    @Test
    public void complexArgs() throws ExecutionException, InterruptedException {
        commander.executeAsync(reader, "test", "true").get();
        assert reader.getResult().equals("true");
        commander.executeAsync(reader, "test", "blob", "true").get();
        assert reader.getResult().equals("true");
        commander.executeAsync(reader, "test", "box", "true").get();
        assert reader.getResult().equals("true");
        commander.executeAsync(reader, "test", "box", "boo").get();
        assert reader.getResult().equals("boo-str");
        commander.executeAsync(reader, "test", "box", "boo", "5").get();
        assert reader.getResult().equals("5-int");
        commander.executeAsync(reader, "test", "num", "1", "5").get();
        assert reader.getResult().equals("15++");
        commander.executeAsync(reader, "test", "num", "1").get();
        assert reader.getResult().equals("1+");
    }
    
    @Test
    public void conflict() throws ExecutionException, InterruptedException {
        commander.executeAsync(reader, "test", "conflict", "1").get();
        assert reader.getResult().equals("1-s");
        commander.executeAsync(reader, "test", "conflict", "blob").get();
        assert reader.getResult().equals("blob-s");
        commander.executeAsync(reader, "test", "nonconflict", "1").get();
        assert reader.getResult().equals("1-i");
        commander.executeAsync(reader, "test", "nonconflict", "blob").get();
        assert reader.getResult().equals("blob-s");
    }
    
    @Test
    public void takeAll() throws ExecutionException, InterruptedException {
        commander.executeAsync(reader, "test", "takeall", "hello", "there!").get();
        assert reader.getResult().equals("hello there!");
        commander.executeAsync(reader, "test", "takeall", "hello there!").get();
        assert reader.getResult().equals("hello there!");
        commander.executeAsync(reader, "test", "takeafter", "0.5").get();
        assert reader.getResult().equals("0.5");
        commander.executeAsync(reader, "test", "takeafter", "0.5", "hello", "there!").get();
        assert reader.getResult().equals("hello there!");
    }
    
    @Test
    public void patterns() {
        assert commander.getNextCompletions("t").containsAll(new MagicStringList("true", "takeall", "takeafter"));
        assert commander.getNextCompletions("b").containsAll(new MagicStringList("bar", "box", "blob"));
        assert commander.getNextCompletions("box", "fa").contains("false");
    }
    
}
