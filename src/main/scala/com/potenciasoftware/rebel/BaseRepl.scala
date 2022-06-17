package com.potenciasoftware.rebel

import com.potenciasoftware.rebel.executionWrapper.ExecutionWrapper
import zio._

import java.lang.reflect.Field
import java.net.URLClassLoader
import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.Repl
import scala.tools.nsc.interpreter.shell.Completion
import scala.tools.nsc.interpreter.shell.ILoop
import scala.tools.nsc.interpreter.shell.NoCompletion
import scala.tools.nsc.interpreter.shell.ShellConfig
import scala.tools.nsc.typechecker.TypeStrings

import BaseRepl._

class BaseRepl {

  /**
   * Overwrite to alter the settings before starting the REPL.
   *
   * Settings is a mutable representation of all compiler options.
   * For documentation of these options, see:
   * <pre>scalac -help</pre>
   *
   * For instance, the -deprecation flag is [[Settings.deprecation]].
   * It can be enabled like so:
   *
   * {{{
   * override protected def updateSettings(settings: Settings): Unit = {
   *   settings.deprecation.value = true
   * }
   * }}}
   */
  protected def updateSettings(settings: Settings): Unit = ()

  private lazy val settings = {
    val sets = new Settings
    updateSettings(sets)
    if (sets.classpath.isDefault)
      Thread.currentThread.getContextClassLoader match {
        case cl: URLClassLoader =>
          sets.classpath.value =
            cl.getURLs
              .map(_.toString)
              .distinct
              .mkString(java.io.File.pathSeparator)
        case _ => sys.error("classloader is not a URLClassLoader")
      }
    sets
  }

  /** Override to provide banner text to display at startup. */
  protected val banner: String = WelcomePlaceholder

  /**
   * Override to provide bound values.
   * These will be available from within the REPL.
   */
  protected def boundValues: Seq[Parameter] = Seq.empty

  // Because ILoop declares 'prompt' to be a lazy val,
  // we can't just override it or set it directly.
  // We have to use Java reflection to change it after
  // the ILoop has been instatiated.
  private lazy val promptField: Option[Field] =
    classOf[ILoop]
      .getDeclaredFields
      .find(_.getName == "prompt")
      .map { field =>
        field.setAccessible(true)
        field
      }

  /**
   * Override to provide a script to run on startup.
   *
   * This can be a multiline string. No output will be shown from this script.
   */
  protected def startupScript: String = ""

  /** Override to provide a custom ExecutionWrapper. */
  protected val executionWrapper: ExecutionWrapper = ExecutionWrapper.none

  /** Override to provide additional colon commands to the REPL. */
  protected def customCommands: Seq[LoopCommand] = Seq.empty

  /** Override to provide logic to execute when quitting the REPL. */
  def onQuit(): Unit = ()

  /** Read the current text of the REPL prompt. */
  def prompt: String = repl.prompt

  /** Change the current text of the REPL prompt. */
  def prompt_=(newValue: String): Unit = {
    promptField foreach { field =>
      field.set(repl, newValue)
    }
  }

  private lazy val repl: ILoop = new ILoop(ShellConfig(settings)) {

    val _banner = Option(banner).getOrElse(WelcomePlaceholder)
    override def welcome: String = {
      _banner.replaceAll(WelcomePlaceholder, super.welcome)
    }

    override def createInterpreter(interpreterSettings: Settings): Unit = {

      super.createInterpreter(interpreterSettings)

      intp.beQuietDuring {
        for (param <- boundValues)
          param.bindTo(intp)

        if (startupScript.nonEmpty)
          intp.interpret(startupScript)
      }

      intp.setExecutionWrapper(executionWrapper.code)
    }

    private def customQuit(q: LoopCommand): Seq[LoopCommand] =
      Seq(LoopCommand.cmd(
        name = q.name,
        usage = q.usage,
        help = q.help,
        f = { line =>
          delayedAction(5.seconds) { sys.exit() }
          onQuit()
          q(line)
        },
        completion = q.completion))

    override def commands: List[LoopCommand] = {
      val (Seq(quitCommand), cmds) =
        super.commands.partition(_.name == "quit")
      (cmds ++ customCommands
        .map(_.convert(LoopCommand.cmd _, Result.apply)))
        .sortBy(_.name) ++ customQuit(quitCommand)
    }
  }

  def run(): Unit = {
    repl.run(settings)
  }
}

object BaseRepl {

  private val WelcomePlaceholder = "%%%%welcome%%%%"

  private def delayedAction(after: Duration)(action: => Unit): Unit =
    Runtime.default.unsafeRunAsync {
      ZIO.attempt(action)
        .delay(after)
        .sandbox
        .catchAll(_ => ZIO.unit)
    }

  case class LoopCommand(
    name: String,
    usage: String,
    help: String,
    f: String => LoopCommand.Result,
    completion: Completion = NoCompletion
  ) {
    private[BaseRepl] def convert[A, B](
      toCommand: (String, String, String, String => B, Completion) => A,
      toResult: (Boolean, Option[String]) => B
    ): A =
      toCommand(name, usage, help,
      { s => val r = f(s); toResult(r.keepRunning, r.lineToRecord) },
      completion)
  }

  object LoopCommand {
    case class Result(keepRunning: Boolean, lineToRecord: Option[String])
  }

  class Parameter private (
    name: String,
    `type`: String,
    value: Any,
    modifiers: List[String]
  ) {
    private[BaseRepl] def bindTo(intp: Repl): Unit =
      intp.bind(name, `type`, value, modifiers)
  }

  object Parameter {
    def apply[A: TypeTag: ClassTag](
      name: String,
      value: A,
      modifiers: String*
    ) = new Parameter(
      name,
      TypeStrings.fromTag[A],
      value,
      modifiers.toList)
  }
}

