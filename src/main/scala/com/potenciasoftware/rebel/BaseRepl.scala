package com.potenciasoftware.rebel

import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.shell.ILoop
import scala.tools.nsc.interpreter.shell.ShellConfig

import BaseRepl._

class BaseRepl {

  protected def settings: Settings = new Settings

  private lazy val _settings = {
    val sets = settings
    if (sets.classpath.isDefault)
      sets.classpath.value = sys.props("java.class.path")
    sets
  }

  /** Override to provide banner text to display at startup. */
  protected val banner: String = WelcomePlaceholder

  protected def config: ShellConfig = ShellConfig(_settings)

  protected def repl: ILoop = new ILoop(config) {
    val _banner = Option(banner).getOrElse(WelcomePlaceholder)
    override def welcome: String = {
      _banner.replaceAll(WelcomePlaceholder, super.welcome)
    }
  }

  def run(): Unit = {
    repl.run(_settings)
  }
}

object BaseRepl {
  private val WelcomePlaceholder = "%%%%welcome%%%%"
}
