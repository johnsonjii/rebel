package com.potenciasoftware.rebel.executionWrapper

import sun.misc.Signal
import sun.misc.SignalHandler
import zio._

/**
 * Execute the command within a ZIO fiber so that commands that
 * run too long can be killed with Ctrl+C without killing the
 * whole REPL.
 */
trait ExecuteInFiber extends ExecutionWrapper with ResultMapper {

  override lazy val code: String = {
    installSigInt()
      val cls = getClass()
      Seq(
        cls.getPackage().getName(),
        cls.getSimpleName().stripSuffix("$"),
        "execute") mkString "."
  }

  def execute(a: => Any): String =
    try Runtime.default.unsafeRun {
        for {
          f <- ZIO.attempt(a.toString).sandbox.fork
          _ <- storeFiber(f)
          result <- f.join
            .map(mapResult)
            .ensuring(clearFiber)
        } yield result
    }
    catch {
      case FiberFailure(Cause.Fail(cause, _)) =>
        cause.asInstanceOf[Cause[Throwable]] match {
          case Cause.Fail(t, _) => throw t
          case Cause.Die(t, _) => throw t
          case Cause.Interrupt(_, _) => throw new InterruptedException()
          case c => throw new Exception(c.prettyPrint)
        }
    }

  private[executionWrapper] var fiber: Option[Fiber.Runtime[_, _]] = None

  private def storeFiber(f: Fiber.Runtime[_, _]): UIO[Unit] =
    ZIO.succeed { fiber = Some(f) }

  private val clearFiber: UIO[Unit] =
    ZIO.succeed { fiber = None }

  private[executionWrapper] def cancel(): Unit = {
    fiber map { f =>
      Runtime.default.unsafeRun(f.interrupt)
    }
  }

  private[executionWrapper] def exit(): Unit = { sys.exit() }

  private[executionWrapper] val handler: SignalHandler = { _ =>
    if (fiber.isDefined) cancel()
    else exit()
  }

  private[executionWrapper] def installSigInt(): Unit =  {
    Signal.handle(ExecuteInFiber.SIGINT, handler)
  }
}

object ExecuteInFiber extends ExecuteInFiber with ResultMapper.Identity {
  private[executionWrapper] final val SIGINT: Signal = new Signal("INT")
}
