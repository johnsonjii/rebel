package com.potenciasoftware.rebel.executionWrapper

/**
 * A mixin that provides a hook to map the strings that result from code
 * execution through an ExecutionWrapper.
 */
trait ResultMapper {
  def mapResult(result: String): String
}

object ResultMapper {

  trait Identity extends ResultMapper {
    override def mapResult(result: String): String = result
  }
}
