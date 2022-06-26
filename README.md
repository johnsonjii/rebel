Rebel
=====
#### _Vive la_ REPL!

Build applications that use the Scala REPL as a user interface.
--------------------------------------------------------------------------------

The Scala REPL is a great tool. We can use it to run random code, test out
theories, and other quick Scala tasks. We can even run scripts in the REPL to
set up more complex code. The REPL can also be used as the command line user
interface for applications. Anyone who has used sbt has used a customized Scala
REPL. Wouldn't it be nice if we could leverage this powerful tool and the
powerful Scala language for our user interfaces?

Using the Scala REPL in this way is not well documented, but it is able to be
incorporated into your Scala code. This library, rebel, is intended to expose
parts of the REPL in an easy to access place so that you too can utilize the
REPL for powerful user interfaces.

## Usage
[![Badge-SonatypeReleases]][Link-SonatypeReleases]

Add the dependency to your product:

- sbt
  ```scala
  libraryDependencies += "com.potenciasoftware" %% "rebel" % "[[version]]"
  ```
- Maven
  ```xml
  <dependency>
    <groupId>com.potenciasoftware</groupId>
    <artifactId>rebel_2.13</artifactId>
    <version>[[version]]</version>
  </dependency>
  ```

Extend `BaseRepl` and override its protected members to customize the REPL
according to your needs. Call `run()` on an instance of your customized REPL
class to start the REPL.

```scala
import com.potenciasoftware.rebel.BaseRepl

object Main {

  class MyRepl extends BaseRepl {
    // Customize the REPL here.
  }

  def main(args: Array[String]): Unit = {
    new MyRepl().run()
  }
}
```

## Customization

### updateSettings(settings: Settings): Unit

This allows full access to the compiler settings prior to instantiation of the
REPL. See `scalac -help` and its more specific variants (e.g. `scalac -X`) for
details. All of the options described in the help output are available from the
`Settings` instance that is passed to `updateSettings()`.

Also note, the `Settings` instance is mutable, just set the desired values on
the properties you want to change.

As an example, the below code will cause the REPL to use VIM keybindings rather
than Emacs keybindings:

```scala
class MyRepl extends BaseRepl {

  import scala.tools.nsc.Settings

  override protected def updateSettings(settings: Settings): Unit = {
    // This corresponds to the -Xjline command line option of the scala command.
    settings.Xjline.value = "vi"
  }
}
```

Please note that if you leave `Settings.classpath` set to the default value,
`BaseRepl` will automatically include the full classpath of your running
application as the classpath of the REPL. This is usually what you want.

### banner: String

By default when the REPL starts up, it prints out a standard banner including
information about the version of Scala and the JVM running the REPL. Override
`banner` to customize this startup banner. Including the value of
`super.banner` will cause the inclusion of the default information.

### prompt: String<br/>prompt_=(newValue: String): Unit

You may want to set the prompt to something other than the default (`scala> `).
You may also want to change the prompt to indicate some change in state within
your application. The `prompt` getter and setter members (`def prompt: String`
and `def prompt_=(String): Unit` may be used for these purposes.

### boundValues: Seq[Parameter]

You may want to bind objects from within your application to values that are
accessable from the REPL. Override `boundValues` to provide a list of the
values to bind.

Example:
```scala
class ApplicationSettings {
  var prettyPrintResults: Boolean = false
}

class MyRepl extends BaseRepl {

  import com.potenciasoftware.rebel.BaseRepl.Parameter

  val settings = new ApplicationSettings

  override protected val boundValues: Seq[Parameter] =
    Seq(Parameter[ApplicationSettings](
      name  = "settings",
      value = settings))
}
```

### startupScript: String

Often you want to run a set of commands whenever your REPL starts. For instance
you may want to automatically run imports so that the contents of your packages
will be immediately available to the REPL user.

Example:
```scala
class MyRepl extends BaseRepl {
  override protected val startupScript: String =
     """
       |import com.example.Utils._
       |import com.example.Implicits._
       |""".stripMargin
}
```

### executionWrapper: ExecutionWrapper

Every command entered into the REPL is turned into a larger piece of Scala code
that is then compiled and executed. An execution wrapper is a method that takes
`=> Any` and returns `String` which gives you the ability to participate in the
generation of that code.

When an execution wrapper method is set on the REPL interpreter, each command
entered into the REPL will be wrapped with a call to that method effectively
passing each command to the method's `=> Any` parameter and returning the
`String` that the method returns.

The execution wrapper method must be able to be statically referenced (i.e.
from a static global value or a public object) because the fully qualified path
to the execution wrapper method is provided as part of the code that the REPL
compiles and executes.

The trait `ExecutionWrapper` provides a place to define this fully qualified
path, `def code: String`. A typical use of the trait would be to:

  1. Extend `ExecutionWrapper` on a public object.
  2. Define the execution wrapper method as part of that object.
  3. Override `def code: String` on the object with the fully qualified path to
     the method from step 2.
  4. Override `val executionWrapper: ExecutionWrapper` on your REPL class to
     return the object.

```scala
package com.example

import com.potenciasoftware.rebel.executionWrapper.ExecutionWrapper

object Main {

  object MyExecutionWrapper extends ExecutionWrapper {

    def execute(a: => Any): String = {
      // do something before each command
      val result = a
      // do something after each command
      result.toString
    }

    override def code: String =
      "com.example.Main.MyExecutionWrapper.execute"
  }

  class MyRepl extends BaseRepl {
    override val executionWrapper: ExecutionWrapper = MyExecutionWrapper
  }

  def main(args: Array[String]): Unit = {
    new MyRepl().run()
  }
}
```

Included in this library is an `ExecutionWrapper` implementation,
`ExecuteInFiber`, which executes each command in a ZIO fiber which can be
interrupted with the INT signal (Ctrl-C). When this `ExecutionWrapper` is
installed, the user may cancel a long running (or hung) command with Ctrl-C
without causing the REPL to exit as it normally would.

```scala
import com.potenciasoftware.rebel.executionWrapper.ExecuteInFiber

object MyExecutionWrapper extends ExecuteInFiber

object Main {

  class MyRepl extends BaseRepl {
    override val executionWrapper: ExecutionWrapper = MyExecutionWrapper
  }

  def main(args: Array[String]): Unit = {
    new MyRepl().run()
  }
}
```

Note: If you need to debug your execution wrapper method, one thing that can be
helpful is to tell the REPL to output the code that will be compiled by
appending `// show` at the end of your command like this:

```
scala> 1 + 1 // show
```

### customCommands: Seq[LoopCommand]

The REPL provides commands like `:load` and `:type` out of the box. This method
may be used to provide additional commands.

Example:
```scala
class MyRepl extends BaseRepl {

  import com.potenciasoftware.rebel.BaseRepl.LoopCommand

  override protected val customCommands: Seq[LoopCommand] =
    Seq(LoopCommand(
      name  = "answer",
      usage = "[question]",
      help  = "Gives the answer to any question you have regarding " +
                "Life, the Universe, and Everything.",
      f     = input => {
                println("42")
                LoopCommand.Result(true, None)
              }))
}
```

### onQuit(): Unit

When a user enters the command `:quit` into the REPL it will exit. Your
application may want to do some quick clean up before the REPL exits. Override
the `onQuit()` method for this.

Note: Since users of the REPL expect it to close pretty quickly, `BaseRepl`
will automatically call `sys.exit()` after 5 seconds has elapsed. Your cleanup
code needs to be fast enough to finish in that time.

```scala
class MyRepl extends BaseRepl {
  override protected def onQuit(): Unit = {
    println("Goodbye")
  }
}
```

[Link-SonatypeReleases]: https://s01.oss.sonatype.org/content/repositories/releases/com/potenciasoftware/rebel_2.13/
[Badge-SonatypeReleases]: https://img.shields.io/nexus/r/https/s01.oss.sonatype.org/com.potenciasoftware/rebel_2.13.svg "Sonatype Releases"
