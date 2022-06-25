package com.potenciasoftware.rebel

import com.potenciasoftware.rebel.executionWrapper.ExecutionWrapper
import com.potenciasoftware.rebel.executionWrapper.ExecuteInFiber

object Examples {

  trait Example {
    def main(args: Array[String]): Unit
  }

  class ApplicationSettings {
    var prettyPrintResults: Boolean = false
  }

  object MyExecutionWrapper extends ExecutionWrapper {

    def execute(a: => Any): String = {
      val result = a.toString
      if (result startsWith "// mutated") ""
      else result
    }

    override def code: String =
      "com.potenciasoftware.rebel.Examples.MyExecutionWrapper.execute"
  }

  object MyExecuteInFiber extends ExecuteInFiber {
    override val code: String =
      "com.potenciasoftware.rebel.Examples.MyExecuteInFiber.execute"
  }

  val examples = Map[String, Example](

    "1" -> {
      import com.potenciasoftware.rebel.BaseRepl

      new Example {

        class MyRepl extends BaseRepl {
          // customize the REPL here
        }

        def main(args: Array[String]): Unit = {
          new MyRepl().run()
        }
      }
    },

    "2" -> {

      import com.potenciasoftware.rebel.BaseRepl
      import scala.tools.nsc.Settings

      new Example {

        class MyRepl extends BaseRepl {
          override protected def updateSettings(settings: Settings): Unit = {
            // This corresponds to the -Xjline command line option of the scala command.
            settings.Xjline.value = "vi"
          }
        }

        def main(args: Array[String]): Unit = {
          new MyRepl().run()
        }
      }
    },

    "3" -> {

      import com.potenciasoftware.rebel.BaseRepl
      import com.potenciasoftware.rebel.BaseRepl.Parameter

      new Example {

        // This class definition has been moved so that it has a
        // TypeTag.
        // class ApplicationSettings {
        //   var prettyPrintResults: Boolean = false
        // }

        class MyRepl extends BaseRepl {

          val settings = new ApplicationSettings

          override protected val boundValues: Seq[Parameter] =
            Seq(Parameter[ApplicationSettings](
              name   = "settings",
              value  = settings))
        }

        def main(args: Array[String]): Unit = {
          new MyRepl().run()
        }
      }
    },

    "4" -> {

      import com.potenciasoftware.rebel.BaseRepl

      new Example {

        class MyRepl extends BaseRepl {
          override protected val startupScript: String =
            """
              |import zio._
              |import Cause._
              |""".stripMargin
        }

        def main(args: Array[String]): Unit = {
          new MyRepl().run()
        }
      }
    },

    "5" -> {

      // package com.example

      import com.potenciasoftware.rebel.BaseRepl
      // import com.potenciasoftware.rebel.executionWrapper.ExecutionWrapper

      new Example {

        // This object definition has been moved so that it has a
        // globally accessable location.
        //
        // object MyExecutionWrapper extends ExecutionWrapper {
        //
        //   def execute(a: => Any): String = {
        //     // do something before each command
        //     val result = a.toString
        //     // do something after each command
        //     result
        //   }
        //
        //   override def code: String =
        //     "com.example.MyExecutionWrapper.execute"
        // }

        class MyRepl extends BaseRepl {
          override val executionWrapper = MyExecutionWrapper
        }

        def main(args: Array[String]): Unit = {
          new MyRepl().run()
        }
      }
    },

    "6" -> {

      import com.potenciasoftware.rebel.BaseRepl
      // import com.potenciasoftware.rebel.executionWrapper.ExecuteInFiber

      new Example {

        // This object definition has been moved so that it has a
        // object MyExecuteInFiber extends ExecuteInFiber

        class MyRepl extends BaseRepl {
          override val executionWrapper = MyExecuteInFiber
        }

        def main(args: Array[String]): Unit = {
          new MyRepl().run()
        }
      }
    },

    "7" -> {

      import com.potenciasoftware.rebel.BaseRepl
      import com.potenciasoftware.rebel.BaseRepl.LoopCommand

      new Example {

        class MyRepl extends BaseRepl {
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

        def main(args: Array[String]): Unit = {
          new MyRepl().run()
        }
      }
    },

  )

  def main(args: Array[String]): Unit = {
    args match {
      case Array(num, tail@_*) if examples.keySet contains num =>
        examples(num).main(tail.toArray)
      case _ =>
        val available =
          examples.keys.toSeq
            .map(_.toInt)
            .sorted
            .mkString(", ")

        println(s"Available examples: $available")
    }
  }
}
