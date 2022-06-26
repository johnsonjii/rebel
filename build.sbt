inThisBuild(Def.settings(
  scalaVersion := "2.13.8",
  version := "1.0.2-SNAPSHOT",
  versionScheme := Some("early-semver"),
  organization := "com.potenciasoftware",
  organizationName := "potencia",
  organizationHomepage := Some(url("https://github.com/potencia/")),
  description := "Build applications that use the Scala REPL as a user interface",
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/potencia/rebel"),
      "scm:git@github.com:potencia/rebel.git"
    )
  ),
  developers := List(
    Developer(
      id    = "johnsonjii",
      name  = "John Johnson II",
      email = "johnsonjii@potenciasoftware.com",
      url   = url("https://github.com/johnsonjii"))
    ),
  licenses := List("MIT" -> new URL("https://raw.githubusercontent.com/potencia/rebel/master/LICENSE")),
  homepage := Some(url("https://github.com/potencia/rebel")),
  pomIncludeRepository := { _ => false },
  publishTo := Some("releases" at "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2"),
  publishMavenStyle := true,
  scalacOptions ++= Seq(
    "-encoding", "utf8",
    "-deprecation",
    "-unchecked",
    "-feature",
    "-Xlint:unused",
    ),
  ))

lazy val rebel = (project in file("."))
  .settings(
    name := "rebel",
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value,
    libraryDependencies += "dev.zio" %% "zio" % "2.0.0",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.11" % Test,
    libraryDependencies += "org.scalamock" %% "scalamock" % "5.2.0" % Test,
  )

