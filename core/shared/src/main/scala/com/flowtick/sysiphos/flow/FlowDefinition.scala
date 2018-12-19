package com.flowtick.sysiphos.flow

import com.flowtick.sysiphos.task._
import io.circe.Decoder.Result
import io.circe._
import com.flowtick.sysiphos._

trait FlowTask {
  /**
   * @return unique identifier of this flow task
   */
  def id: String

  /**
   * @return the children of this task, this will recursively define a tree of tasks
   */
  def children: Option[Seq[FlowTask]]

  /**
   *
   * @return number of seconds to delay the first execution of this task
   */
  def startDelay: Option[Long]

  /**
   * @return number of seconds to delay before attempting another retry
   */
  def retryDelay: Option[Long]

  /**
   * @return the number of times the task should be retried in case of a failure
   */
  def retries: Option[Int]
}

trait FlowDefinition {
  /**
   * @return unique identifier of this flow definition
   */
  def id: String

  /**
   * @return the root tasks for this flow, this is a list to allow task parallelism on the root level
   */
  def tasks: Seq[FlowTask]

  /**
   * @return true if only the last instance in a list of scheduled instances should be executed
   */
  def latestOnly: Boolean

  /**
   * @param id a task id
   * @return first task in the tasks and there children that matches the id param
   */
  def findTask(id: String): Option[FlowTask] = {
    def findInTask(task: FlowTask): Option[FlowTask] =
      if (task.id == id) {
        Some(task)
      } else task
        .children
        .getOrElse(Seq.empty)
        .map(task => findInTask(task))
        .flatMap(result => result.find(_.id == id))
        .headOption

    tasks.flatMap(findInTask).headOption
  }

  /**
   * @return the instance level parallelism, meaning how many instance should be allowed to run in parallel
   */
  def parallelism: Option[Int]

  /**
   * @return the task level parallelism, meaning how many task instance should run in parallel per running instance
   */
  def taskParallelism: Option[Int]

  /**
   * @return how many tasks per seconds should be executed, this should be seen as maximum capping
   */
  def taskRatePerSecond: Option[Int]
}

object FlowDefinition {
  import io.circe.generic.auto._
  import io.circe.parser._
  import io.circe.syntax._

  def apply(flowDefinitionDetails: FlowDefinitionDetails): Either[Exception, FlowDefinition] =
    for {
      json <- Either.fromOption(flowDefinitionDetails.source, new IllegalStateException("source is empty"))
      flowDefinition <- FlowDefinition.fromJson(json)
    } yield flowDefinition

  implicit val definitionDecoder: Decoder[FlowDefinition] = new Decoder[FlowDefinition] {
    override def apply(c: HCursor): Result[FlowDefinition] = for {
      id <- c.downField("id").as[String]
      tasks <- c.downField("tasks").as[Seq[FlowTask]]
      latestOnly <- c.downField("latestOnly").as[Option[Boolean]]
      parallelism <- c.downField("parallelism").as[Option[Int]]
      taskParallelism <- c.downField("taskParallelism").as[Option[Int]]
      taskRatePerSecond <- c.downField("taskRatePerSecond").as[Option[Int]]
    } yield SysiphosDefinition(
      id = id,
      tasks = tasks,
      latestOnly = latestOnly.getOrElse(false),
      parallelism = parallelism,
      taskParallelism = taskParallelism,
      taskRatePerSecond = taskRatePerSecond)
  }

  implicit val definitionEncoder: Encoder[FlowDefinition] = new Encoder[FlowDefinition] {
    override def apply(a: FlowDefinition): Json = Json.obj()
  }

  implicit val taskDecoder: Decoder[FlowTask] = new Decoder[FlowTask] {
    override def apply(c: HCursor): Result[FlowTask] = for {
      _ <- c.downField("id").as[String]
      typeHint <- c.downField("type").as[String]
      task <- taskFromCursor(typeHint, c)
    } yield task
  }

  implicit val taskEncoder: Encoder[FlowTask] = new Encoder[FlowTask] {
    override def apply(a: FlowTask): Json = a match {
      case task: CommandLineTask => task.asJson
      case task: TriggerFlowTask => task.asJson
      case task: CamelTask => task.asJson
      case task: SysiphosTask => task.asJson
      case task: DefinitionImportTask => task.asJson
      case _ => Json.Null
    }
  }

  def taskFromCursor(typeHint: String, cursor: HCursor): Either[DecodingFailure, FlowTask] = {
    typeHint match {
      case "shell" => cursor.as[CommandLineTask]
      case "trigger" => cursor.as[TriggerFlowTask]
      case "camel" => cursor.as[CamelTask]
      case "dynamic" => cursor.as[DynamicTask]
      case "definition-import" => cursor.as[DefinitionImportTask]
      case _ => cursor.as[SysiphosTask]
    }
  }

  sealed trait ExtractExpression {
    def `type`: String
    def expression: String
    def extractSingle: Option[Boolean] // to extract value from singleton list
  }

  final case class ExtractSpec(`type`: String, name: String, expression: String, extractSingle: Option[Boolean] = None) extends ExtractExpression
  final case class ItemSpec(`type`: String, expression: String, extractSingle: Option[Boolean] = None) extends ExtractExpression

  final case class SysiphosDefinition(
    id: String,
    tasks: Seq[FlowTask],
    latestOnly: Boolean = false,
    parallelism: Option[Int] = None,
    taskParallelism: Option[Int] = None,
    taskRatePerSecond: Option[Int] = None) extends FlowDefinition

  final case class SysiphosTask(
    id: String,
    `type`: String,
    children: Option[Seq[FlowTask]],
    properties: Option[Map[String, String]],
    startDelay: Option[Long] = None,
    retryDelay: Option[Long] = None,
    retries: Option[Int] = None) extends FlowTask

  def fromJson(json: String): Either[Exception, FlowDefinition] = decode[FlowDefinition](json)

  def toJson(definition: FlowDefinition): String = definition match {
    case s: SysiphosDefinition => s.asJson.spaces2
  }
}

