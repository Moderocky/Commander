package mx.kenzie.commander.sync;

import mx.kenzie.commander.Commander;
import mx.kenzie.commander.arg.ArgLiteral;
import mx.kenzie.commander.arg.ArgString;
import org.junit.Test;

public class TreeResolverTest {
    
    ExTree1 tree1 = new ExTree1();
    ExTree2 tree2 = new ExTree2();
    
    @Test
    public void test() {
        assert tree1.getPatterns().containsAll(tree2.getPatterns());
        assert String.join("\n", tree1.getPatterns()).equals(String.join("\n", tree2.getPatterns()));
        assert tree1.getNextCompletions("cecil", "").equals(tree2.getNextCompletions("cecil", ""));
        assert tree1.getNextCompletions("").equals(tree2.getNextCompletions(""));
        
        System.out.println(String.join("\n", tree1.getPatterns()));
    }
    
    static class ExTree1 extends Commander<ResultReader> {
        @Override
        protected CommandImpl create() {
            return command("tree")
                .arg("alice", sender -> {})
                .arg("bob", sender -> {})
                .arg("cecil",
                    arg("ex1", sender -> {}),
                    arg("ex2", sender -> {})
                )
                .arg("cecil",
                    arg((sender, inputs) -> {},
                        new ArgString()
                    )
                );
        }
    }
    
    static class ExTree2 extends Commander<ResultReader> {
        @Override
        protected CommandImpl create() {
            return command("tree")
                .arg(sender -> {}, "alice")
                .arg("bob", sender -> {})
                .arg(sender -> {}, "cecil", "ex1")
                .arg(sender -> {}, "cecil", "ex1", "ex2")
                .arg((sender, inputs) -> {},
                    new ArgLiteral("cecil"), new ArgString()
                );
        }
    }
}
