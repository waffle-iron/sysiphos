package com.flowtick.sysiphos.execution.task

import cats.effect.IO
import com.flowtick.sysiphos.execution.FlowTaskExecution
import com.flowtick.sysiphos.flow.FlowInstance
import com.flowtick.sysiphos.logging.Logger
import com.flowtick.sysiphos.logging.Logger.LogId
import com.flowtick.sysiphos.task.CamelTask
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.{ CamelContext, ExchangePattern }

import scala.collection.JavaConverters._

trait CamelTaskExecution extends FlowTaskExecution {
  val camelContext = new DefaultCamelContext()

  def withContext[T](block: CamelContext => IO[T]): IO[T] = {
    block(camelContext)
  }

  def executeExchange(
    camelTask: CamelTask,
    flowInstance: FlowInstance,
    logId: LogId)(taskLogger: Logger): IO[AnyRef] = withContext { camelContext =>
    IO.delay {
      val body = camelTask.bodyTemplate.map(replaceContextInTemplate(_, flowInstance.context, Map.empty).get).orNull
      val headers: java.util.Map[String, AnyRef] = camelTask.headers.getOrElse(Map.empty).mapValues(value => value: AnyRef).asJava

      val pattern: ExchangePattern = camelTask.pattern.map(_.toLowerCase) match {
        case Some("in-only") => ExchangePattern.InOnly
        case Some("out-only") => ExchangePattern.OutOnly
        case _ => ExchangePattern.InOut
      }

      camelContext.createProducerTemplate().sendBodyAndHeaders(camelTask.uri, pattern, body, headers)
    }
  }
}
