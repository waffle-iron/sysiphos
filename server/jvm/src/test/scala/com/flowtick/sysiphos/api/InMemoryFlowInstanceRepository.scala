package com.flowtick.sysiphos.api

import java.time.{ LocalDateTime, ZoneOffset }
import java.util.UUID

import com.flowtick.sysiphos.flow.{ FlowInstance, FlowInstanceQuery, FlowInstanceRepository }

import scala.collection.mutable
import scala.concurrent.Future

case class InMemoryFlowInstance(id: String,
                                flowDefinitionId: String,
                                creationTime: Long,
                                status: String,
                                retries: Int,
                                startTime: Option[Long] = None,
                                endTime: Option[Long] = None,
                                context: Map[String, String] = Map.empty) extends FlowInstance

class InMemoryFlowInstanceRepository extends FlowInstanceRepository {
  private val instances = mutable.ListBuffer[FlowInstance]()

  override def getFlowInstances(query: FlowInstanceQuery): Future[Seq[FlowInstance]] =
    Future.successful(instances)

  override def createFlowInstance(flowDefinitionId: String, context: Map[String, String]): Future[FlowInstance] = {
    val newInstance = InMemoryFlowInstance(
      id = UUID.randomUUID().toString,
      flowDefinitionId = flowDefinitionId,
      creationTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC),
      status = "new",
      retries = 3
    )
    Future.successful(newInstance)
  }
}
