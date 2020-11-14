package mx.kenzie.commander.sync;

import mx.kenzie.magic.collection.MagicStringList;
import org.junit.Test;

public class CommanderTest {
    
    final ExampleCommander commander = new ExampleCommander();
    final ResultReader reader = new ResultReader();
    
    @Test
    public void def() {
        commander.execute(reader, "test");
        assert reader.result.equals("default");
    }
    
    @Test
    public void simpleArgs() {
        commander.execute(reader, "test", "foo");
        assert reader.result.equals("foo");
        commander.execute(reader, "test", "bar");
        assert reader.result.equals("bar");
        commander.execute(reader, "test", "blob");
        assert reader.result.equals("blob");
    }
    
    @Test
    public void complexArgs() {
        commander.execute(reader, "test", "true");
        assert reader.result.equals("true");
        commander.execute(reader, "test", "blob", "true");
        assert reader.result.equals("true");
        commander.execute(reader, "test", "box", "true");
        assert reader.result.equals("true");
        commander.execute(reader, "test", "box", "boo");
        assert reader.result.equals("boo-str");
        commander.execute(reader, "test", "box", "boo", "5");
        assert reader.result.equals("5-int");
        commander.execute(reader, "test", "num", "1", "5");
        assert reader.result.equals("15++");
        commander.execute(reader, "test", "num", "1");
        assert reader.result.equals("1+");
    }
    
    @Test
    public void conflict() {
        commander.execute(reader, "test", "conflict", "1");
        assert reader.result.equals("1-s");
        commander.execute(reader, "test", "conflict", "blob");
        assert reader.result.equals("blob-s");
        commander.execute(reader, "test", "nonconflict", "1");
        assert reader.result.equals("1-i");
        commander.execute(reader, "test", "nonconflict", "blob");
        assert reader.result.equals("blob-s");
    }
    
    @Test
    public void takeAll() {
        commander.execute(reader, "test", "takeall", "hello", "there!");
        assert reader.result.equals("hello there!");
        commander.execute(reader, "test", "takeall", "hello there!");
        assert reader.result.equals("hello there!");
        commander.execute(reader, "test", "takeafter", "0.5");
        assert reader.result.equals("0.5");
        commander.execute(reader, "test", "takeafter", "0.5", "hello", "there!");
        assert reader.result.equals("hello there!");
    }
    
    @Test
    public void patterns() {
        assert commander.getNextCompletions("t").containsAll(new MagicStringList("true", "takeall", "takeafter"));
        assert commander.getNextCompletions("b").containsAll(new MagicStringList("bar", "box", "blob"));
        assert commander.getNextCompletions("box", "fa").contains("false");
    }
    
    @Test
    public void error() {
        Throwable throwable = null;
        try {
            commander.execute(reader, "test", "xyz");
        } catch (Throwable ex) {
            throwable = ex;
        }
        assert throwable != null;
    }
    
    @Test
    public void noError() {
        Throwable throwable = null;
        try {
            commander.execute(reader, "test", "abc");
        } catch (Throwable ex) {
            throwable = ex;
        }
        assert throwable == null;
    }
    
}
