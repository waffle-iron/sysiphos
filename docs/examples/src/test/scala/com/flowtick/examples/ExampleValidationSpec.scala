package com.flowtick.examples

import com.flowtick.sysiphos.flow.FlowDefinition
import org.scalatest.{ FlatSpec, Matchers }
import org.slf4j.{ Logger, LoggerFactory }

class ExampleValidationSpec extends FlatSpec with Matchers {
  val log: Logger = LoggerFactory.getLogger(getClass)

  def loadFromClassPath(path: String): String = scala.io.Source.fromInputStream(getClass
    .getClassLoader
    .getResourceAsStream(path))
    .getLines()
    .mkString("\n")

  val examplesToCheck: Seq[(String, String)] = Seq(
    "shell" -> loadFromClassPath("shell-flow.json"),
    "camel-http" -> loadFromClassPath("camel-http-flow.json"),
    "camel-slack" -> loadFromClassPath("camel-slack-flow.json"),
    "camel-sql" -> loadFromClassPath("camel-sql-flow.json"),
    "camel-bean" -> loadFromClassPath("camel-bean-flow.json"),
    "trigger" -> loadFromClassPath("trigger-flow.json"),
    "onFailure-task" -> loadFromClassPath("onfailure-task.json"))

  examplesToCheck.foreach {
    case (name, json) =>
      it should s"have a valid $name example" in {
        FlowDefinition.fromJson(json) match {
          case Left(error) => fail(s"unable to parse json for $name", error)
          case Right(_) => succeed
        }
      }
  }

}
