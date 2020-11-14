package mx.kenzie.commander.async;

import mx.kenzie.commander.Commander;
import mx.kenzie.commander.arg.Argument;

import java.util.concurrent.Executors;

public class ExampleDispatchCommander extends Commander<FutureResultReader> {
    
    @Override
    protected CommandImpl create() {
        return command("test")
            .defaultAction(sender -> sender.send("default"))
            .allowAsyncExecution(Executors.newCachedThreadPool())
            .arg("foo", sender -> sender.send("foo"))
            .arg("bar", sender -> sender.send("bar"))
            .arg("blob", sender -> sender.send("blob"))
            .arg("bar", sender -> sender.send("blue"))
            .arg(
                Argument.BOOLEAN,
                (sender, inputs) -> sender.send(inputs[0] + "")
            ).arg("blob",
                arg(
                    (sender, inputs) -> sender.send(inputs[0] + ""),
                    Argument.BOOLEAN
                )
            ).arg("box",
                arg(
                    (sender, inputs) -> sender.send(inputs[0] + ""),
                    Argument.BOOLEAN
                )
            ).arg("box",
                arg(
                    (sender, inputs) -> sender.send(inputs[0] + "-str"),
                    Argument.STRING
                ),
                arg(
                    (sender, inputs) -> sender.send(inputs[1] + "-int"),
                    Argument.INTEGER
                )
            ).arg("num",
                arg(
                    (sender, inputs) -> sender.send(inputs[0] + "+"),
                    Argument.INTEGER
                ),
                arg(
                    (sender, inputs) -> sender.send(inputs[0] + "" + inputs[1] + "++"),
                    Argument.INTEGER
                )
            ).arg("conflict",
                arg(
                    (sender, inputs) -> sender.send(inputs[0] + "-s"),
                    Argument.STRING
                )
            ).arg("conflict",
                arg(
                    (sender, inputs) -> sender.send(inputs[0] + "-i"),
                    Argument.INTEGER
                )
            ).arg("nonconflict",
                arg(
                    (sender, inputs) -> sender.send(inputs[0] + "-i"),
                    Argument.INTEGER
                )
            ).arg("nonconflict",
                arg(
                    (sender, inputs) -> sender.send(inputs[0] + "-s"),
                    Argument.STRING
                )
            ).arg("takeall",
                arg(
                    (sender, inputs) -> sender.send(inputs[0] + ""),
                    Argument.STRING_TAKE_ALL
                )
            ).arg("takeafter",
                arg(
                    (sender, inputs) -> sender.send(inputs[0] + ""),
                    Argument.NUMBER
                ),
                arg(
                    (sender, inputs) -> sender.send(inputs[1] + ""),
                    Argument.STRING_TAKE_ALL
                )
            );
    }
    
}