package com.flowtick.sysiphos.execution.task

import cats.effect.IO
import com.flowtick.sysiphos.execution.FlowTaskExecution
import com.flowtick.sysiphos.flow.FlowInstance
import com.flowtick.sysiphos.logging.Logger
import com.flowtick.sysiphos.logging.Logger.LogId
import com.flowtick.sysiphos.task.CamelTask
import org.apache.camel.impl.{ DefaultCamelContext, SimpleRegistry }
import org.apache.camel.{ CamelContext, Exchange, ExchangePattern, Processor }

trait CamelTaskExecution extends FlowTaskExecution {
  protected def createCamelContext(camelTask: CamelTask): IO[CamelContext] = IO.delay {
    val registry = new SimpleRegistry
    camelTask.registry.getOrElse(Map.empty).foreach {
      case (name, entry) => if (entry.`type`.equalsIgnoreCase("bean")) {
        val newInstance: AnyRef = getClass.getClassLoader.loadClass(entry.fqn).newInstance().asInstanceOf[AnyRef]
        registry.put(name, newInstance)
      }
    }

    new DefaultCamelContext(registry)
  }

  protected def createExchange(
    camelTask: CamelTask,
    flowInstance: FlowInstance,
    logId: LogId)(taskLogger: Logger): IO[CamelContext => Exchange] = IO.delay { camelContext =>
    val pattern: ExchangePattern = camelTask.pattern.map(_.toLowerCase) match {
      case Some("in-only") => ExchangePattern.InOnly
      case Some("out-only") => ExchangePattern.OutOnly
      case _ => ExchangePattern.InOut
    }

    val producer = camelContext.createProducerTemplate()

    val exchange = producer.send(camelTask.uri, pattern, new Processor {
      override def process(exchange: Exchange): Unit = {
        camelTask.headers.getOrElse(Map.empty).foreach {
          case (key, value) => exchange.getIn.setHeader(key, value)
        }

        val body = camelTask.bodyTemplate.map(replaceContextInTemplate(_, flowInstance.context, Map.empty).get).orNull

        exchange.getIn.setBody(body)
      }
    })

    Option(exchange.getException).foreach(throw _)
    exchange
  }

  def executeExchange(
    camelTask: CamelTask,
    flowInstance: FlowInstance,
    logId: LogId)(taskLogger: Logger): IO[Exchange] =
    for {
      camelContext <- createCamelContext(camelTask)
      result <- createExchange(camelTask, flowInstance, logId)(taskLogger)
    } yield result(camelContext)
}
