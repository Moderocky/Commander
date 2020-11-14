package mx.kenzie.commander.sync;

import mx.kenzie.commander.Commander;
import mx.kenzie.commander.arg.ArgBoolean;
import mx.kenzie.commander.arg.ArgInteger;
import mx.kenzie.commander.arg.ArgStringFinal;
import mx.kenzie.commander.arg.Argument;
import mx.kenzie.overlord.Overlord;

public class ExampleCommander extends Commander<ResultReader> {
    
    @Override
    protected CommandImpl create() {
        return command("test")
            .errorHandler((context, ex) -> {
                if (!ex.getMessage().contains("potato"))
                    Overlord.UNSAFE.throwException(ex);
            })
            .arg("xyz", sender -> {
                throw new RuntimeException();
            })
            .arg("abc", sender -> {
                throw new RuntimeException("potatoes are cool");
            })
            .arg("foo", sender -> sender.send("foo"))
            .arg("bar", sender -> sender.send("bar"))
            .arg("blob", sender -> sender.send("blob"))
            .arg("bar", sender -> sender.send("blue"))
            .arg(
                (sender, inputs) -> sender.send(inputs[0] + ""),
                new ArgBoolean()
            ).arg("blob",
                arg(
                    (sender, inputs) -> sender.send(inputs[0] + ""),
                    Argument.BOOLEAN
                )
            ).arg("box",
                arg(
                    (sender, inputs) -> sender.send(inputs[0] + ""),
                    new ArgBoolean()
                )
            ).arg("box",
                arg(
                    (sender, inputs) -> sender.send(inputs[0] + "-str"),
                    Argument.STRING
                ),
                arg(
                    (sender, inputs) -> sender.send(inputs[1] + "-int"),
                    new ArgInteger()
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
                    new ArgStringFinal()
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
    
    @Override
    public CommandSingleAction<ResultReader> getDefault() {
        return sender -> sender.send("default");
    }
    
}