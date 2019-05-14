package com.flowtick.sysiphos.flow

import com.flowtick.sysiphos.flow
import io.circe.{ Decoder, Encoder }

import scala.util.Try

object FlowTaskInstanceStatus extends Enumeration {
  type FlowTaskInstanceStatus = Value
  val New: FlowTaskInstanceStatus.Value = Value("new")
  val Done: FlowTaskInstanceStatus.Value = Value("done")
  val Retry: FlowTaskInstanceStatus.Value = Value("retry")
  val Failed: FlowTaskInstanceStatus.Value = Value("failed")
  val Running: FlowTaskInstanceStatus.Value = Value("running")

  implicit val decoder: Decoder[flow.FlowTaskInstanceStatus.Value] = Decoder.decodeString.flatMap { str =>
    Decoder.instanceTry { _ =>
      Try(FlowTaskInstanceStatus.withName(str.toLowerCase))
    }
  }

  implicit val encoder: Encoder[flow.FlowTaskInstanceStatus.Value] = Encoder.enumEncoder(FlowTaskInstanceStatus)
}

trait FlowTaskInstance {
  def id: String
  def flowInstanceId: String
  def flowDefinitionId: String
  def taskId: String
  def onFailureTaskId: Option[String]
  def creationTime: Long
  def updatedTime: Option[Long]
  def startTime: Option[Long]
  def endTime: Option[Long]
  def retries: Int
  def status: FlowTaskInstanceStatus.FlowTaskInstanceStatus
  def retryDelay: Long
  def nextDueDate: Option[Long]
  def logId: String
}

final case class FlowTaskInstanceDetails(
  id: String,
  flowInstanceId: String,
  flowDefinitionId: String,
  taskId: String,
  creationTime: Long,
  onFailureTaskId: Option[String] = None,
  updatedTime: Option[Long] = None,
  startTime: Option[Long] = None,
  endTime: Option[Long] = None,
  retries: Int,
  status: FlowTaskInstanceStatus.FlowTaskInstanceStatus,
  retryDelay: Long,
  nextDueDate: Option[Long],
  logId: String) extends FlowTaskInstance