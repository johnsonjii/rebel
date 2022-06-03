package com.potenciasoftware.rebel

import java.io.InputStream
import java.lang.Thread.currentThread
import java.net.URLClassLoader
import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag

object TestUtils {

  private lazy val modifiedTestClasspath: Seq[String] = {

    def modified(in: String): Seq[String] =
      in match {
        case scalaCompiler: String if scalaCompiler contains "scala-compiler" =>
          Seq(in,
            in.replaceAll("scala-compiler", "scala-library"),
            in.replaceAll("scala-compiler", "scala-reflect"))
          case _ => Seq(in)
      }

    currentThread.getContextClassLoader match {
      case cl: URLClassLoader =>
        (for {
          url <- cl.getURLs
          file <- modified(url.toString)
        } yield file).toSeq.distinct
      case _ => sys.error("classloader is not a URLClassLoader")
    }
  }

  private class InputLinesStream(lines: Iterable[String]) extends InputStream {

    private val endOfStream = -1
    private val lineSeparator = '\n'.toInt

    private val linesIterator = lines.iterator
    private def nextLine: Option[Iterator[Int]] =
      Option.when(linesIterator.hasNext) {
        linesIterator.next().iterator.map(_.toInt)
      }
    private var currentLine: Option[Iterator[Int]] = nextLine

    override def read(): Int =
      currentLine match {
        case None => endOfStream
        case Some(l) =>
          if (l.hasNext) l.next()
          else {
            currentLine = nextLine
            lineSeparator
          }
      }
  }

  case class RunResults(private val output: Seq[Either[String, String]], exitCode: Int) {

    lazy val out: Seq[String] =
      output
        .filter(_.isRight)
        .map(_.toOption.get)

    lazy val errOut: Seq[String] =
      output
        .filter(_.isLeft)
        .map(_.swap.toOption.get)

    lazy val all: Seq[String] =
      output
        .map(_.fold("[Error] " + _, identity))
  }

  /** In a separate system process, execute a REPL sending it input returning the
    * resulting output. This technique is necessary because, for some reason,
    * instatiating the REPL instance in the same process that is running the
    * unit tests casuse an exception.
    *
    * @tparam C         The class where [[methodName]] is defined.
    * @param methodName The name of a method with 1 [[Seq[String]] parameter
    *                   that takes the lines that will be sent to the REPL as
    *                   inpet and sets up the REPL to test. If the method
    *                   returns a [[BaseRepl]], [[BaseRepl.run()]]
    *                   will be called.
    * @param inputLines All the lines of input to send to the REPL.
    *                   (Note: including a [[":q"]] line at the end of the input
    *                   is not necessary.)
    */
  def replTest[C: ClassTag](methodName: String, inputLines: String*): RunResults = {
    import scala.sys.process._

    val in = new InputLinesStream(inputLines ++ Seq(":q"))
    val output = ArrayBuffer.empty[Either[String, String]]
    val logger = ProcessLogger(
      line => output.append(Right(line)),
      line => output.append(Left(line)))

    val exitCode = (Seq("java",
      "-classpath", modifiedTestClasspath mkString java.io.File.pathSeparator,
      "com.potenciasoftware.rebel.TestUtils",
      implicitly[ClassTag[C]].runtimeClass.getName(),
      methodName) ++ inputLines) #< in !< logger

    RunResults(output.toSeq, exitCode)
  }

  implicit class LinesOps(s: Seq[String]) {

    def asBlock: String = s.mkString("\n")

    def trim: Seq[String] = s.map(_.trim)

    def printAll: Unit = s.foreach(println)

    def compressed: Seq[String] =
      s.filterNot(_.isEmpty)

    def withLineNumbers: Seq[String] = {
      val padTo = (s.size - 1).toString.size
      for {
        (l, i) <- s.zipWithIndex
        n = i.toString.reverse.padTo(padTo, "0").reverse.mkString
      } yield s"$n: $l"
    }
  }

  def main(args: Array[String]): Unit = {
    val Array(className, methodName, input@_*) = args
    val cls = Class.forName(className)
    cls.getMethod(methodName, classOf[Seq[_]])
      .invoke(cls.newInstance(), input.toSeq)
      .asInstanceOf[BaseRepl] match {
        case repl: BaseRepl => repl.run()
        case _ =>
      }
  }
}
