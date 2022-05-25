package com.potenciasoftware.rebel

import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.shell.ILoop
import scala.tools.nsc.interpreter.shell.ShellConfig

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

  protected def config: ShellConfig = ShellConfig(settings)

  protected def repl: ILoop = new ILoop(config) {
    val _banner = Option(banner).getOrElse(WelcomePlaceholder)
    override def welcome: String = {
      _banner.replaceAll(WelcomePlaceholder, super.welcome)
    }
  }

  def run(): Unit = {
    repl.run(settings)
  }
}

object BaseRepl {
  private val WelcomePlaceholder = "%%%%welcome%%%%"
}
