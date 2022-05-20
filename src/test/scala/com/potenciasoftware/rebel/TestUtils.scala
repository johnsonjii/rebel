package com.potenciasoftware.rebel

import java.lang.Thread.currentThread
import java.net.URLClassLoader
import java.io.InputStream
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

  case class RunResults(out: Seq[String], errOut: Seq[String], exitCode: Int)

  def replTest[C: ClassTag](methodName: String, inputLines: Iterable[String]): RunResults = {
    import scala.sys.process._

    val in = new InputLinesStream(inputLines ++ Seq(":q"))
    val out = ArrayBuffer.empty[String]
    val errOut = ArrayBuffer.empty[String]
    val logger = ProcessLogger(
      line => out.append(line),
      line => errOut.append(line))

    val exitCode = Seq("java",
      "-classpath", modifiedTestClasspath mkString ":",
      "com.potenciasoftware.rebel.TestUtils",
      implicitly[ClassTag[C]].runtimeClass.getName(),
      methodName) #< in !< logger

    RunResults(out.toSeq, errOut.toSeq, exitCode)
  }

  def main(args: Array[String]): Unit = {
    val Array(className, methodName) = args
    val cls = Class.forName(className)
    cls.getMethod(methodName).invoke(cls.newInstance())
  }
}
