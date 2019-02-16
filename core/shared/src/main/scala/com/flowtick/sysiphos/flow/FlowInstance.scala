package com.flowtick.sysiphos.flow

import com.flowtick.sysiphos.flow
import io.circe.{ Decoder, Encoder }

import scala.util.Try

final case class FlowInstanceContextValue(key: String, value: String)

object FlowInstanceStatus extends Enumeration {

  type FlowInstanceStatus = Value
  val Scheduled: FlowInstanceStatus.Value = Value("scheduled")
  val Triggered: FlowInstanceStatus.Value = Value("triggered")
  val Done: FlowInstanceStatus.Value = Value("done")
  val Failed: FlowInstanceStatus.Value = Value("failed")
  val Running: FlowInstanceStatus.Value = Value("running")
  val Skipped: FlowInstanceStatus.Value = Value("skipped")

  implicit val decoder: Decoder[flow.FlowInstanceStatus.Value] = Decoder.decodeString.flatMap { str =>
    Decoder.instanceTry { _ =>
      Try(FlowInstanceStatus.withName(str.toLowerCase))
    }
  }

  implicit val encoder: Encoder[flow.FlowInstanceStatus.Value] = Encoder.enumEncoder(FlowInstanceStatus)
}

final case class FlowInstanceDetails(
  id: String,
  flowDefinitionId: String,
  creationTime: Long,
  startTime: Option[Long],
  endTime: Option[Long],
  status: FlowInstanceStatus.FlowInstanceStatus) extends FlowInstance

final case class FlowInstanceContext(instance: FlowInstanceDetails, context: Seq[FlowInstanceContextValue])

trait FlowInstance extends FlowExecutable {
  def status: FlowInstanceStatus.FlowInstanceStatus
}
