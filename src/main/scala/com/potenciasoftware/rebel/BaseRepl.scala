package com.potenciasoftware.rebel

import scala.tools.nsc.interpreter.shell.ILoop
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.shell.ShellConfig

class BaseRepl {

    protected def settings: Settings = {
      val sets = new Settings
      if (sets.classpath.isDefault)
        sets.classpath.value = sys.props("java.class.path")
      sets
    }

    protected def config: ShellConfig = ShellConfig(settings)

    protected def repl: ILoop = new ILoop(config)

    def run(): Unit = {
      repl.run(settings)
    }
}
