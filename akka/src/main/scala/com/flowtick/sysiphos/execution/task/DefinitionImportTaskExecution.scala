package com.flowtick.sysiphos.execution.task

import java.util.{ List => javaList }

import cats.effect.IO
import com.flowtick.sysiphos.execution.Logging
import com.flowtick.sysiphos.flow.FlowDefinition.SysiphosDefinition
import com.flowtick.sysiphos.flow.{ FlowDefinition, FlowInstanceContextValue, FlowTask }
import com.flowtick.sysiphos.logging.Logger
import com.flowtick.sysiphos.logging.Logger.LogId
import com.flowtick.sysiphos.task.{ CamelTask, DefinitionImportTask }

import scala.collection.JavaConverters._
import scala.util.{ Failure, Success }

trait DefinitionImportTaskExecution extends CamelTaskExecution with Logging {
  def getFlowDefinition(
    definitionImportTask: DefinitionImportTask,
    contextValues: Seq[FlowInstanceContextValue],
    logId: LogId)(logger: Logger): IO[FlowDefinition] = {
    executeExchange(definitionImportTask.fetchTask, Seq.empty, logId)(logger).flatMap {
      case (exchange, _) =>
        evaluateExpression[javaList[TaskConfigurationDto], TaskConfigurationDto](definitionImportTask.items, exchange) match {
          case Success(configurations) => definitionFromConfigurations(
            definitionImportTask.targetDefinitionId,
            definitionImportTask.taskTemplate,
            configurations.asScala)
          case Failure(error) => IO.raiseError(error)
        }
    }
  }

  protected def definitionFromConfigurations(
    definitionId: String,
    taskTemplate: FlowTask,
    configurations: Seq[TaskConfigurationDto]): IO[SysiphosDefinition] = IO {
    val taskConfigs: Seq[TaskConfiguration] = configurations.map { taskConfigDto =>
      TaskConfiguration(id = taskConfigDto.id, businessKey = taskConfigDto.businessKey, contextValues = taskConfigDto.properties.asScala.map { property =>
        FlowInstanceContextValue(property.key, property.value)
      })
    }

    val tasks: Seq[CamelTask] = taskConfigs.map { taskConfig =>
      taskTemplate match {
        case camelTask: CamelTask =>
          val extraProps = Map("businessKey" -> taskConfig.businessKey)
          camelTask
            .copy(id = replaceContextInTemplate(camelTask.id, taskConfig.contextValues, extraProps).get)
            .copy(uri = replaceContextInTemplate(camelTask.uri, taskConfig.contextValues, extraProps).get)
            .copy(bodyTemplate = camelTask.bodyTemplate.map(replaceContextInTemplate(_, taskConfig.contextValues, extraProps).get))
      }
    }

    val definition = SysiphosDefinition(
      id = definitionId,
      tasks = tasks)

    definition
  }
}

