package com.flowtick.sysiphos.execution.task

import cats.effect.IO
import com.flowtick.sysiphos.execution.Logging
import com.flowtick.sysiphos.flow.FlowInstanceContextValue
import com.flowtick.sysiphos.logging.Logger
import com.flowtick.sysiphos.logging.Logger.LogId
import com.flowtick.sysiphos.task.{ CamelTask, DynamicTask }

import scala.collection.JavaConverters._

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
        evaluateExpression[TaskConfigurationDtos](
          dynamicTask.items,
          exchange).map { configurations =>
            val taskConfigs = configurations.items.asScala.map(taskConfigDto =>
              TaskConfiguration(id = taskConfigDto.id, businessKey = taskConfigDto.businessKey, contextValues = taskConfigDto.properties.asScala.map { property =>
                FlowInstanceContextValue(property.key, property.value)
              }))
            TaskConfigurations(Some(limit), Some(offset), taskConfigs)
          }
    }
  }
}
