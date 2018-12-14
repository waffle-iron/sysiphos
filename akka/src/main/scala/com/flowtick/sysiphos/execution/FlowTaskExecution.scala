package com.flowtick.sysiphos.execution

import java.io.StringWriter

import com.flowtick.sysiphos.flow.{ FlowInstanceContextValue, FlowTask, FlowTaskInstance }
import freemarker.template.{ Configuration, Template }

import scala.util.Try

trait FlowTaskExecution {
  def sanitizedSysProps: Map[String, String] = sys.props.map(kv => (kv._1.replaceAll("\\.", "_"), kv._2)).toMap

  def replaceContextInTemplate(
    template: String,
    context: Seq[FlowInstanceContextValue],
    additionalModel: Map[String, Any]): Try[String] = Try {
    import scala.collection.JavaConverters._

    val javaModel = (context.map(value => (value.key, value.value)).toMap ++ additionalModel).asJava

    val writer = new StringWriter()
    new Template("template", template, FlowTaskExecution.freemarkerCfg).process(javaModel, writer)
    writer.toString
  }
}

object FlowTaskExecution {
  private[execution] lazy val freemarkerCfg: Configuration = {
    val cfg = new Configuration(Configuration.VERSION_2_3_28)
    cfg.setDefaultEncoding("UTF-8")
    // Don't log exceptions inside FreeMarker that it will thrown at you anyway:// Don't log exceptions inside FreeMarker that it will thrown at you anyway:
    cfg.setLogTemplateExceptions(false)
    // Wrap unchecked exceptions thrown during template processing into TemplateException-s.
    cfg.setWrapUncheckedExceptions(true)
    cfg
  }

  final case class Execute(flowTask: FlowTask, taskInstance: FlowTaskInstance, contextValues: Seq[FlowInstanceContextValue])

  case object TaskAck
  case object TaskStreamInitialized
  case object TaskStreamCompleted
  final case class TaskStreamFailure(ex: Throwable)
}

