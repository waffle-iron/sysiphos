package com.flowtick.sysiphos.execution.task

import cats.effect.IO
import cats.syntax.apply._
import com.flowtick.sysiphos.execution.Logging
import com.flowtick.sysiphos.flow.FlowDefinition.SysiphosDefinition
import com.flowtick.sysiphos.flow.{ FlowDefinition, FlowInstanceContextValue, FlowTask }
import com.flowtick.sysiphos.logging.Logger
import com.flowtick.sysiphos.logging.Logger.LogId
import com.flowtick.sysiphos.task.{ CamelTask, DefinitionImportTask, TriggerFlowTask }

import scala.collection.JavaConverters._

trait DefinitionImportTaskExecution extends CamelTaskExecution with Logging {
  def getFlowDefinition(
    definitionImportTask: DefinitionImportTask,
    contextValues: Seq[FlowInstanceContextValue],
    logId: LogId)(logger: Logger): IO[FlowDefinition] = {
    executeExchange(definitionImportTask.fetchTask, Seq.empty, logId)(logger).flatMap {
      case (exchange, _) =>
        for {
          configurations <- evaluateExpression[TaskConfigurationDtos](definitionImportTask.items, exchange)
          template <- definitionImportTask.definitionTemplate
            .orElse(definitionImportTask.targetDefinitionId.map(SysiphosDefinition(_, Seq.empty))).map(IO.pure)
            .getOrElse(IO.raiseError(new IllegalArgumentException("either target definition id or template need to be defined")))
          finalDefinition <- definitionFromConfigurations(
            template,
            definitionImportTask.taskTemplate,
            configurations.items.asScala)
        } yield finalDefinition
    }
  }

  protected def definitionFromConfigurations(
    definitionTemplate: FlowDefinition,
    taskTemplate: FlowTask,
    configurations: Seq[TaskConfigurationDto]): IO[FlowDefinition] = IO.unit *> {
    val taskConfigs: Seq[TaskConfiguration] = configurations.map { taskConfigDto =>
      TaskConfiguration(id = taskConfigDto.id, businessKey = taskConfigDto.businessKey, contextValues = taskConfigDto.properties.asScala.map { property =>
        FlowInstanceContextValue(property.key, property.value)
      })
    }

    def replaceValuesInCamelTasks(camelTask: CamelTask, taskConfig: TaskConfiguration, extraProps: Map[String, String]): CamelTask = {
      val children = camelTask.children.map(_.flatMap { child =>
        child match {
          case childrenCamel: CamelTask => Some(replaceValuesInCamelTasks(childrenCamel, taskConfig, extraProps))
          case _ => None
        }
      })
      camelTask.copy(id = replaceContextInTemplate(camelTask.id, taskConfig.contextValues, extraProps).get)
        .copy(uri = replaceContextInTemplate(camelTask.uri, taskConfig.contextValues, extraProps).get)
        .copy(bodyTemplate = camelTask.bodyTemplate.map(replaceContextInTemplate(_, taskConfig.contextValues, extraProps).get))
        .copy(children = children)
    }

    val tasks: IO[Seq[FlowTask]] = IO(taskConfigs.map { taskConfig =>
      val extraProps: Map[String, String] = Map("businessKey" -> taskConfig.businessKey)

      taskTemplate match {
        case camelTask: CamelTask =>
          replaceValuesInCamelTasks(camelTask, taskConfig, extraProps)

        case trigger: TriggerFlowTask =>
          val replacedContextValues = trigger.context match {
            case Some(contextValues) => Some(contextValues.map(contextValue => {
              FlowInstanceContextValue(
                key = contextValue.key,
                value = replaceContextInTemplate(contextValue.value, taskConfig.contextValues, extraProps).getOrElse(contextValue.value))
            }))
            case None => None
          }

          trigger.copy(
            id = replaceContextInTemplate(trigger.id, taskConfig.contextValues, extraProps).get,
            context = replacedContextValues)
      }
    })

    tasks.map(tasksWithReplacements => {
      SysiphosDefinition(
        id = definitionTemplate.id,
        tasks = definitionTemplate.tasks ++ tasksWithReplacements,
        latestOnly = definitionTemplate.latestOnly,
        parallelism = definitionTemplate.parallelism,
        taskParallelism = definitionTemplate.taskParallelism,
        taskRatePerSecond = definitionTemplate.taskRatePerSecond,
        onFailure = definitionTemplate.onFailure)
    })
  }
}

