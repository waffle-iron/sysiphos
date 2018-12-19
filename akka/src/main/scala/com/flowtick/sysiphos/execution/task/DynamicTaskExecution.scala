package com.flowtick.sysiphos.execution.task

import java.util.{ List => javaList }

import cats.effect.IO
import com.flowtick.sysiphos.execution.Logging
import com.flowtick.sysiphos.flow.FlowInstanceContextValue
import com.flowtick.sysiphos.logging.Logger
import com.flowtick.sysiphos.logging.Logger.LogId
import com.flowtick.sysiphos.task.{ CamelTask, DynamicTask }

import scala.collection.JavaConverters._
import scala.util.{ Failure, Success }

trait DynamicTaskExecution extends CamelTaskExecution with Logging {
  def getConfigurations(
    offset: Long,
    limit: Long,
    dynamicTask: DynamicTask,
    logId: LogId)(logger: Logger): IO[TaskConfigurations] = {
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
        evaluateExpression[javaList[TaskConfigurationDto], TaskConfigurationDto](
          dynamicTask.items,
          exchange) match {
            case Success(configurations) =>
              val taskConfigs = configurations.asScala.map { taskConfigDto =>
                TaskConfiguration(id = taskConfigDto.id, businessKey = taskConfigDto.businessKey, contextValues = taskConfigDto.properties.asScala.map { property =>
                  FlowInstanceContextValue(property.key, property.value)
                })
              }

              IO.delay(TaskConfigurations(Some(limit), Some(offset), taskConfigs))
            case Failure(error) => IO.raiseError(error)
          }
    }
  }
}
