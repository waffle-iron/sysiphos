import sbt.url
import sbtrelease.ReleaseStateTransformations._

val scalaV = "2.12.5"
val finchV = "0.16.0-RC1"
val circeV = "0.8.0"
val slf4jV = "1.7.25"

scalacOptions += "-P:scalajs:sjsDefinedByDefault"

lazy val common = Seq(
  name := "sysiphos",
  organization := "com.flowtick",
  scalaVersion := scalaV,
  crossScalaVersions := Seq(scalaV, "2.11.11"),
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseCrossBuild := true,
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    publishArtifacts,
    setNextVersion,
    commitNextVersion,
    releaseStepCommandAndRemaining("sonatypeReleaseAll"),
    pushChanges
  ),
  publishTo := Some(
    if (isSnapshot.value)
      Opts.resolver.sonatypeSnapshots
    else
      Opts.resolver.sonatypeStaging
  ),
  publishMavenStyle := true,
  licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage := Some(url("https://github.com/flowtick/sysiphos")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/flowtick/sysiphos.git"),
      "scm:git@github.com:flowtick/sysiphos.git"
    )
  ),
  developers := List(
    Developer(id = "adrobisch", name = "Andreas Drobisch", email = "github@drobisch.com", url = url("http://drobisch.com/"))
  ),
  libraryDependencies += "org.scalatest" %%% "scalatest" % "3.0.4" % Test,
  libraryDependencies += "org.scalamock" %%% "scalamock" % "4.1.0" % Test
)

lazy val core = crossProject.in(file("core")).
  settings(common).
  settings(
    name := "sysiphos-core",
    libraryDependencies += "org.scala-js" %% "scalajs-stubs" % scalaJSVersion % "provided",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core",
      "io.circe" %%% "circe-generic",
      "io.circe" %%% "circe-parser"
    ).map(_ % circeV)
  )

lazy val coreJVM = core.jvm
lazy val coreJS = core.js

lazy val akka = project.in(file("akka")).
  settings(common).
  settings(
    name := "sysiphos-akka",
    libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.6",
    libraryDependencies += "com.github.alonsodomin.cron4s" %% "cron4s-core" % "0.4.2",
    libraryDependencies += "io.monix" %% "monix" % "2.3.0",
    libraryDependencies += "org.slf4j" % "slf4j-api" % slf4jV
  ).dependsOn(coreJVM)

lazy val git = project.in(file("git")).
  settings(common).
  settings(
    name := "sysiphos-git",
    libraryDependencies += "org.eclipse.jgit" % "org.eclipse.jgit" % "4.9.0.201710071750-r" % Provided
  ).dependsOn(coreJVM)

lazy val slick = project.in(file("slick")).
  settings(common).
  settings(
    name := "sysiphos-slick",
    libraryDependencies += "com.typesafe.slick" %% "slick" % "3.2.3",
    libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.34",
    libraryDependencies += "org.slf4j" % "slf4j-api" % slf4jV,
    libraryDependencies += "com.h2database" % "h2" % "1.4.196"
  ).dependsOn(coreJVM)

lazy val server = crossProject.in(file("server")).
  settings(common).
  settings(
    name := "sysiphos-server",
  )

lazy val serverJVM = server.jvm.settings(
  libraryDependencies ++= Seq(
    "com.github.finagle" %% "finch-core" % finchV,
    "com.github.finagle" %% "finch-circe" % finchV,
    "org.sangria-graphql" %% "sangria" % "1.3.2",
    "org.sangria-graphql" %% "sangria-circe" % "1.1.0",
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "org.eclipse.jgit" % "org.eclipse.jgit" % "4.9.0.201710071750-r"
  )
).dependsOn(git, akka, slick)

lazy val serverJS = server.js.settings(
  scalaJSUseMainModuleInitializer := true,
  artifactPath in (Compile, fastOptJS) := baseDirectory.value.getParentFile / "jvm" / "target" / "scala-2.12" / "classes" / "sysiphos-ui.js",
  artifactPath in (Compile, fullOptJS) := (artifactPath in (Compile, fastOptJS)).value,
  libraryDependencies ++= Seq(
    "in.nvilla" %%% "monadic-html" % "0.4.0-RC1",
    "org.scala-js" %%% "scalajs-dom" % "0.9.2",
    "com.flowtick" %%% "pages" % "0.1.4"
  )
).dependsOn(coreJS)

lazy val root = project.in(file(".")).
  settings(common).
  aggregate(coreJS, coreJVM, serverJVM, serverJS, akka, git).
  settings(
    name := "sysiphos-root",
    publish := {},
    publishLocal := {},
    PgpKeys.publishSigned := {}
  )