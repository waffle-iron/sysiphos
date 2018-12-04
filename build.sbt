import sbt.url
import sbtrelease.ReleaseStateTransformations._

val scalaV = "2.12.7"
val finchV = "0.24.0"
val circeV = "0.10.0"
val slf4jV = "1.7.25"
val logbackV = "1.2.3"
val blobStoreV = "0.2.2"
val camelV = "2.22.1"
val akkaV = "2.5.18"

scalacOptions += "-P:scalajs:sjsDefinedByDefault"

cancelable in Global := true

lazy val common = Seq(
  name := "sysiphos",
  organization := "com.flowtick",
  scalaVersion := scalaV,
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
  libraryDependencies += "org.scalamock" %%% "scalamock" % "4.1.0" % Test,
  libraryDependencies += "ch.qos.logback" % "logback-classic" % logbackV % Test
)

lazy val crossCompile = Seq(
  crossScalaVersions := Seq(scalaV, "2.11.12"),
)

lazy val core = crossProject.in(file("core")).
  settings(common ++ crossCompile).
  settings(
    name := "sysiphos-core",
    libraryDependencies += "org.scala-js" %% "scalajs-stubs" % scalaJSVersion % "provided",
    libraryDependencies += "org.typelevel" %% "cats-core" % "1.4.0",
    libraryDependencies += "org.typelevel" %% "cats-effect" % "1.0.0",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core",
      "io.circe" %%% "circe-generic",
      "io.circe" %%% "circe-parser"
    ).map(_ % circeV)
  )

lazy val coreJVM = core.jvm
lazy val coreJS = core.js

lazy val akka = project.in(file("akka")).
  settings(common ++ crossCompile).
  settings(
    name := "sysiphos-akka",
    libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaV,
    libraryDependencies += "com.typesafe.akka" %% "akka-stream" % akkaV,
    libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % akkaV % Test,
    libraryDependencies += "com.github.alonsodomin.cron4s" %% "cron4s-core" % "0.4.2",
    libraryDependencies += "io.monix" %% "monix" % "2.3.0",
    libraryDependencies += "org.freemarker" % "freemarker" % "2.3.28",
    libraryDependencies += "org.slf4j" % "slf4j-api" % slf4jV,
    libraryDependencies += "io.kamon" %% "kamon-core" % "1.1.3",
    libraryDependencies += "io.kamon" %% "kamon-statsd" % "1.0.0",
    libraryDependencies += "io.kamon" %% "kamon-akka-2.5" % "1.1.0",
    libraryDependencies += "io.kamon" %% "kamon-prometheus" % "1.0.0",
    libraryDependencies += "io.kamon" %% "kamon-logback" % "1.0.0",
    libraryDependencies += "com.datadoghq" % "java-dogstatsd-client" % "2.6.1",
    libraryDependencies += "org.apache.camel" % "camel-core" % camelV,
    libraryDependencies += "org.apache.camel" % "camel-http4" % camelV,
    libraryDependencies += "org.apache.camel" % "camel-jsonpath" % camelV,
    libraryDependencies += "org.apache.camel" % "camel-slack" % camelV,
    libraryDependencies += "org.apache.camel" % "camel-mail" % camelV,
    libraryDependencies += "org.apache.camel" % "camel-sql" % camelV,
    libraryDependencies += "org.apache.camel" % "camel-test" % camelV % Test,
    libraryDependencies += "com.github.tomakehurst" % "wiremock-standalone" % "2.19.0" % Test,
    libraryDependencies += "com.h2database" % "h2" % "1.4.197" % Test
  ).dependsOn(coreJVM, logging)

lazy val gitProject = project.in(file("git")).
  settings(common).
  settings(
    name := "sysiphos-git",
    libraryDependencies += "org.eclipse.jgit" % "org.eclipse.jgit" % "4.9.0.201710071750-r" % Provided
  ).dependsOn(coreJVM)

lazy val slick = project.in(file("slick")).
  settings(common ++ crossCompile).
  settings(
    name := "sysiphos-slick",
    libraryDependencies += "com.typesafe.slick" %% "slick" % "3.2.3",
    libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.34",
    libraryDependencies += "org.slf4j" % "slf4j-api" % slf4jV,
    libraryDependencies += "com.h2database" % "h2" % "1.4.196",
    libraryDependencies += "org.liquibase" % "liquibase-core" % "3.6.1"
  ).dependsOn(coreJVM)

lazy val logging = project.in(file("logging")).
  settings(common ++ crossCompile).
  settings(
    name := "sysiphos-logging",
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % slf4jV,
      "com.lendup.fs2-blobstore" %% "core" % blobStoreV,
      "com.lendup.fs2-blobstore" %% "s3" % blobStoreV
    )
  ).dependsOn(coreJVM)

lazy val server = crossProject.in(file("server")).
  settings(common).
  settings(
    name := "sysiphos-server",
  )

val updateUi = taskKey[Unit]("copy ui resources to class dir")

lazy val serverJVM = server.jvm.enablePlugins(JavaAppPackaging, JavaAgent).settings(
  libraryDependencies ++= Seq(
    "com.github.finagle" %% "finch-core" % finchV,
    "com.github.finagle" %% "finch-circe" % finchV,
    "org.sangria-graphql" %% "sangria" % "1.3.2",
    "org.sangria-graphql" %% "sangria-circe" % "1.1.0",
    "ch.qos.logback" % "logback-classic" % logbackV,
    "org.eclipse.jgit" % "org.eclipse.jgit" % "4.9.0.201710071750-r"
  ),
  maintainer := "flowtick.com",
  dockerAliases := Seq(
    DockerAlias(None, Some("flowtick"), "sysiphos", Some("latest")),
    DockerAlias(None, Some("flowtick"), "sysiphos", Some(version.value))
  ),
  javaAgents += "org.aspectj" % "aspectjweaver" % "1.8.13",
  javaOptions in Universal += "-Dorg.aspectj.tracing.factory=default",
  resourceGenerators in Compile += Def.task {
    Seq((fullOptJS in Compile in serverJS).value.data.getAbsoluteFile)
  }.taskValue,
  (updateUi in Compile) := {
    val jsFile = (fastOptJS in Compile in serverJS).value.data.getAbsoluteFile
    val classDir = (classDirectory in Compile).value
    IO.copyFile(jsFile, classDir / jsFile.getName)
  }
).dependsOn(gitProject, akka, slick)

lazy val serverJS = server.js.settings(
  scalaJSUseMainModuleInitializer := true,
  artifactPath in (Compile, fastOptJS) := target.value / "sysiphos-ui.js",
  artifactPath in (Compile, fullOptJS) := (artifactPath in (Compile, fastOptJS)).value,
  libraryDependencies ++= Seq(
    "com.thoughtworks.binding" %%% "dom" % "latest.release",
    "com.thoughtworks.binding" %%% "futurebinding" % "latest.release",
    "io.suzaku" %%% "diode" % "1.1.3",
    "org.scala-js" %%% "scalajs-dom" % "0.9.2",
    "be.doeraene" %%% "scalajs-jquery" % "0.9.3",
    "com.flowtick" %%% "pages" % "0.1.6"
  ),
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
).dependsOn(coreJS)

lazy val root = project.in(file(".")).
  settings(common).
  aggregate(coreJS, coreJVM, serverJVM, serverJS, akka, gitProject, slick).
  settings(
    name := "sysiphos-root",
    publish := {},
    publishLocal := {},
    PgpKeys.publishSigned := {},
  )