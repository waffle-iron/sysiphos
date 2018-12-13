package com.flowtick.sysiphos.execution.task

import java.util.{ List => javaList, Map => javaMap }

import cats.effect.IO
import com.flowtick.sysiphos.execution.Logging
import com.flowtick.sysiphos.flow.FlowInstanceContextValue
import com.flowtick.sysiphos.logging.Logger
import com.flowtick.sysiphos.logging.Logger.LogId
import com.flowtick.sysiphos.task.{ CamelTask, DynamicTask }

import scala.collection.JavaConverters._
import scala.util.{ Failure, Success }

case class DynamicTaskConfiguration(contextValues: Seq[FlowInstanceContextValue])
case class DynamicTaskConfigurations(
  limit: Long,
  offset: Long,
  configurations: Seq[DynamicTaskConfiguration])

trait DynamicTaskExecution extends CamelTaskExecution with Logging {
  def getConfigurations(
    offset: Long,
    limit: Long,
    dynamicTask: DynamicTask,
    logId: LogId)(logger: Logger): IO[DynamicTaskConfigurations] = {
    val uriWithOffsetAndLimit = replaceContextInTemplate(
      dynamicTask.contextSourceUri,
      Seq.empty,
      Map("offset" -> offset.toString, "limit" -> limit.toString)).getOrElse(dynamicTask.contextSourceUri)

    val camelTask = CamelTask(
      id = s"${dynamicTask.id}-fetch-configuration",
      uri = uriWithOffsetAndLimit,
      children = None)

    executeExchange(camelTask, Seq.empty, logId)(logger).flatMap {
      case (exchange, _) =>
        evaluateExpression[javaList[javaList[javaMap[String, String]]]]( // -> Seq[Seq[FlowInstanceContextValue]]
          dynamicTask.items,
          exchange) match {
            case Success(configurations) =>
              val contexts = configurations.asScala.map { contextValues =>
                DynamicTaskConfiguration(contextValues.asScala.map { keyValueMap =>
                  FlowInstanceContextValue(keyValueMap.get("key").toString, keyValueMap.get("value").toString)
                })
              }

              IO.delay(DynamicTaskConfigurations(limit, offset, contexts))
            case Failure(error) => IO.raiseError(error)
          }
    }
  }
}
