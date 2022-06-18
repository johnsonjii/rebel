package com.potenciasoftware.rebel.executionWrapper

trait ExecutionWrapper {

  /**
   * The exact code to inject to invoke the execution wrapper.
   *
   * This should be a fully qualified call to a method that takes one
   * parameter of (=> Any) and returns String. The parameter list
   * portion of the method call (including parenthesis) should be omitted
   * from this string.
   *
   * The string that is returned from the wrapped method is what will be
   * output to the REPL. This would normally be the string representation of
   * the (=> Any) input.
   *
   * For example:
   * {{{
   * package com.example
   *
   * import com.potenciasoftware.rebel.ExecutionWrapper
   *
   * object SampleExecutionWrapper extends ExecutionWrapper {
   *
   *    override val code: String =
   *      "_root_.com.example.SampleExecutionWrapper.execute"
   *
   *    def execute(a: => Any): String = {
   *
   *      // do stuff before execution
   *
   *      val result = a.toString
   *
   *      // do stuff after execution
   *
   *      result
   *    }
   * }
   * }}}
   */
  def code: String
}

object ExecutionWrapper {
  val none = new ExecutionWrapper {
    override val code: String = ""
  }
}
