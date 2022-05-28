package com.potenciasoftware.rebel

import java.lang.reflect.Field
import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.Repl
import scala.tools.nsc.interpreter.shell.ILoop
import scala.tools.nsc.interpreter.shell.ShellConfig
import scala.tools.nsc.typechecker.TypeStrings

import BaseRepl._

class BaseRepl {

  /**
   * Overwrite to provide alter the settings before starting the REPL.
   *
   * Settings is a mutable representation of all compiler options.
   * For documentation of these options, see:
   * <pre>scalac -help</pre>
   *
   * For instance, the -deprecation flag is [[Settings.deprecation]].
   * It can be enabled like so:
   *
   * {{{
   * val settings = new Settings
   * Settings.deprecation.value = true
   * }}}
   */
  protected def updateSettings(settings: Settings): Unit = ()

  private lazy val settings = {
    val sets = new Settings
    updateSettings(sets)
    if (sets.classpath.isDefault)
      sets.classpath.value = sys.props("java.class.path")
    sets
  }

  /** Override to provide banner text to display at startup. */
  protected val banner: String = WelcomePlaceholder

  /**
   * Override to provide bound values.
   * These will be available from within the REPL.
   */
  protected def boundValues: Seq[Parameter] = Seq()

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

  /** Read the current text of the REPL prompt. */
  def prompt: String = repl.prompt

  /** Change the current text of the REPL prompt. */
  def prompt_=(newValue: String): Unit = {
    promptField.foreach { field =>
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
      }
    }
  }

  def run(): Unit = {
    repl.run(settings)
  }
}

object BaseRepl {
  private val WelcomePlaceholder = "%%%%welcome%%%%"

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
    def apply[A: TypeTag: ClassTag](name: String, value: A, modifiers: String*) =
      new Parameter(name, TypeStrings.fromTag[A], value, modifiers.toList)
  }
}

