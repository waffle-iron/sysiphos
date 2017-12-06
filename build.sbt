import sbt.url
import sbtrelease.ReleaseStateTransformations._

val scalaV = "2.12.4"
val finchV = "0.16.0-RC1"
val circeV = "0.8.0"

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
  libraryDependencies += "org.scalatest" %%% "scalatest" % "3.0.3" % Test,
  libraryDependencies += "org.scalamock" %%% "scalamock-scalatest-support" % "3.6.0" % Test
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
    libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.6",
    libraryDependencies += "com.github.alonsodomin.cron4s" %% "cron4s-core" % "0.4.2",
    libraryDependencies += "io.monix" %% "monix" % "2.3.0",
    libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.25"
  ).dependsOn(coreJVM)

lazy val git = project.in(file("git")).
  settings(common).
  settings(
    libraryDependencies += "org.eclipse.jgit" % "org.eclipse.jgit" % "4.9.0.201710071750-r"
  ).dependsOn(coreJVM)

lazy val server = crossProject.in(file("server")).
  settings(common).
  settings(
    name := "sysiphos-server",
    libraryDependencies ++= Seq(
      "com.github.finagle" %% "finch-core" % finchV,
      "com.github.finagle" %% "finch-circe" % finchV,
      "org.sangria-graphql" %% "sangria" % "1.3.2",
      "org.sangria-graphql" %% "sangria-circe" % "1.1.0"
    )
  ).dependsOn(core)

lazy val serverJVM = server.jvm.settings(
  libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"
).dependsOn(akka, git)

lazy val serverJS = server.js.settings(
  scalaJSUseMainModuleInitializer := true,
  artifactPath in (Compile, fastOptJS) := baseDirectory.value.getParentFile / "jvm" / "target" / "scala-2.12" / "classes" / "sysiphos-ui.js",
  artifactPath in (Compile, fullOptJS) := (artifactPath in (Compile, fastOptJS)).value,
  libraryDependencies ++= Seq(
    "in.nvilla" %%% "monadic-html" % "0.3.2",
    "org.scala-js" %%% "scalajs-dom" % "0.9.2"
  )
)

lazy val root = project.in(file(".")).
  settings(common).
  aggregate(coreJS, coreJVM, serverJVM, akka, git).
  settings(
    publish := {},
    publishLocal := {},
    PgpKeys.publishSigned := {}
  )