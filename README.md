Commander
=====
A framework for building standard command systems.

### Features
* [x]  Simple lambda-based system
* [x]  Works with literal (preset) arguments, and dynamic (accepting input) arguments
* [x]  Works with **any** system, as long as there is a string input
* [x]  Automatic argument input serialisation
* [x]  Argument overloading
* [x]  Automatic pattern generation, e.g. `/command <string> arg [<*int> <string...>]`
* [x]  Argument/command aliases and descriptions
* [x]  Fast, pre-parsed, type-safe argument handling
* [x]  Fallback argument handling
* [x]  Optional thread dispatch (async) execution

### Maven Information
```xml
<repository>
    <id>pan-repo</id>
    <name>Pandaemonium Repository</name>
    <url>https://gitlab.com/api/v4/projects/18568066/packages/maven</url>
</repository>
``` 

```xml
<dependency>
    <groupId>mx.kenzie</groupId>
    <artifactId>commander</artifactId>
    <version>4.0.0</version>
    <scope>compile</scope>
</dependency>
```

Examples
---
Some examples are available at [the Mask v3 Wiki](https://gitlab.com/Pandaemonium/Mask/-/wikis/Commander/Examples) however some of these may now be out-of-date.

#### Exception Handlers
```java 
command("test")
    .errorHandler((context, throwable) -> {
        if (!throwable.getMessage().contains("potato"))
            Overlord.UNSAFE.throwException(ex);
        context.sender(); // The sender object
    })
```

#### Default Behaviour
```java 
command("test")
    .defaultAction(sender -> sender.println("Hello!"));
    // Assumes the "sender" is a print stream (like System::out)
```

#### Literal Arguments
(Assumes the sender has a "reply" method - the sender can be anything you want.)
```java 
command("test")
    .arg("xyz", sender -> {
        throw new RuntimeException();
    })
    .arg("foo", sender -> sender.reply("foo"))
    .arg("bar", sender -> sender.reply("bar"));
```

This generates:
```css 
test xyz -> throws exception
test foo -> replies with "foo"
test bar -> replies with "bar"
```

#### Dynamic (Input) Arguments
(Assumes the sender has a "reply" method - the sender can be anything you want.)
```java 
command("test")
    .arg("bar", sender -> sender.reply("blue"))
    // Generates '%command-label% bar'
    .arg(
        (sender, inputs) -> sender.reply(inputs[0] + ""),
        new ArgBoolean() // inputs[0] will be this!
    // Generates '%command-label% %arg-label%'
    ).arg("blob",
        arg(
            (sender, inputs) -> sender.reply(inputs[0] + ""),
            new ArgBoolean() // inputs[0] will be this!
        )
    );
    // Generates '%command-label% blob %arg-label%'
```

```css 
test bar -> replies with "blue"
test <boolean> -> replies with boolean input
test blob <boolean> -> replies with boolean input
```

Literal arguments will always be prioritised over dynamic arguments.

This means that if you had `/test <string>` and `/test hello`, passing the argument "hello" would ALWAYS trigger the `/test hello` action rather than the dynamic `<string>` input.

#### Order of Execution
```java 
command("test")
    .arg("bar", sender -> sender.reply("red"))
    .arg("bar", sender -> sender.reply("blue"));
```

```css 
test bar -> replies with "red"
test bar -> replies with "blue"
```

In this situation we have two identical patterns.
As they are always tested in order, this means the FIRST one will always be used.

The result will always be "red".

#### Stacking Arguments
There are two ways to stack arguments.

Simple:
```java 
command("test")
    .arg("num",
        arg(
            (sender, inputs) -> sender.send(inputs[0] + ", " + inputs[1]),
            new ArgInteger(), new ArgInteger()
        )
    );
```

```css 
test num <int> <int>
```

In this case, we have behaviour only for two integers being provided.
However, if only one is provided (e.g. `test num 6`) we have no 

Complex:
```java 
command("test")
    .arg("num",
        arg(
            (sender, inputs) -> sender.send(inputs[0] + ""),
            Argument.INTEGER
        ),
        arg(
            (sender, inputs) -> sender.send(inputs[0] + ", " + inputs[1]),
            Argument.INTEGER
        )
    );
```

```css 
test num <int>
test num <int> <int>
```

In this case we have TWO patterns generated, for one or two integers.

Stacking arguments using `arg, arg, ...` will generate a new pattern for each one with the new argument appended.

NOTE: this is equivalent to the following:
```java 
command("test")
    .arg("num",
        arg(
            (sender, inputs) -> sender.send(inputs[0] + ""),
            Argument.INTEGER
        )
    )
    .arg("num",
        arg(
            (sender, inputs) -> sender.send(inputs[0] + ", " + inputs[1]),
            Argument.INTEGER, Argument.INTEGER
        )
    );
```

```css 
test num <int>
test num <int> <int>
```

As you can see, there are multiple ways to generate patterns.

At runtime, Commander simply resolves your command tree into a map of `patterns -> executions`.

This means that you can generate the SAME behaviour using completely different trees.

#### Proof of Tree Equivalence

The following two command trees will be resolved to the same result.

1.
```java 
command("tree")
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
```

2.
```java 
command("tree")
    .arg(sender -> {}, "alice")
    .arg("bob", sender -> {})
    .arg(sender -> {}, "cecil", "ex1")
    .arg(sender -> {}, "cecil", "ex1", "ex2")
    .arg((sender, inputs) -> {},
        new ArgLiteral("cecil"), new ArgString()
    );
```

Result:
```css 
tree alice
tree bob
tree cecil <string>
tree cecil ex1
tree cecil ex1 ex2
```

This demonstrates that there are many ways to produce the same result, so it is down to the user's personal preference.

#### Execution Dispatch (Async)

If you provide an ExecutorService to the command tree, the commands will be dispatched to the service for execution.
Parsing of the arguments will still be done on the original thread.

This is as simple as:
```java 
command("test")
    .allowAsyncExecution(Executors.newCachedThreadPool())
    .defaultAction(sender -> sender.send("default"))
    .arg("foo", sender -> sender.send("foo"))
    .arg("bar", sender -> sender.send("bar"));
```

Remember: if your command has an async executor, you can get a CompletableFuture using `executeAsync` as opposed to the traditional execute method.
