package com.flowtick.sysiphos.execution.task

import java.io.InputStream
import java.util

import cats._
import cats.instances.list._
import cats.instances.try_._
import cats.effect.IO
import com.fasterxml.jackson.databind.{ DeserializationFeature, ObjectMapper }
import com.flowtick.sysiphos.execution.{ FlowTaskExecution, Logging }
import com.flowtick.sysiphos.flow.FlowDefinition.ExtractExpression
import com.flowtick.sysiphos.flow.FlowInstanceContextValue
import com.flowtick.sysiphos.logging.Logger
import com.flowtick.sysiphos.logging.Logger.LogId
import com.flowtick.sysiphos.task.CamelTask
import org.apache.camel.{ CamelContext, Exchange, ExchangePattern, Processor }
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.impl.{ DefaultCamelContext, SimpleRegistry }
import org.apache.camel.jsonpath.JsonPathExpression
import org.apache.camel.language.simple.SimpleLanguage
import org.springframework.beans.{ BeanUtils, PropertyAccessorFactory }
import scala.collection.JavaConverters._
import scala.util.{ Failure, Success, Try }

trait CamelTaskExecution extends FlowTaskExecution with Logging {

  lazy val objectMapper: ObjectMapper = {
    val mapper = new ObjectMapper()
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    mapper
  }

  protected def createCamelContext(camelTask: CamelTask): IO[CamelContext] = IO.delay {
    import scala.collection.JavaConverters._

    val registry = new SimpleRegistry
    camelTask.registry.getOrElse(Map.empty).foreach {
      case (name, entry) => if (entry.`type`.equalsIgnoreCase("bean")) {
        val clazz = classOf[CamelTaskExecution].getClassLoader.loadClass(entry.fqn)
        val newInstance = BeanUtils.instantiateClass(clazz)
        val wrapper = PropertyAccessorFactory.forBeanPropertyAccess(newInstance)
        wrapper.setPropertyValues(entry.properties.getOrElse(Map.empty).asJava)

        registry.put(name, wrapper.getWrappedInstance)
      }
    }

    val context = new DefaultCamelContext(registry)
    context.getProperties.put("CamelJacksonTypeConverterToPojo", "true")
    context.disableJMX()
    context
  }

  protected def createExchange(
    camelTask: CamelTask,
    context: Seq[FlowInstanceContextValue],
    logId: LogId)(taskLogger: Logger): IO[CamelContext => Exchange] = IO.delay { camelContext =>

    def exchange: Exchange = camelTask.exchangeType.getOrElse("producer") match {
      case "producer" =>
        val producer = camelContext.createProducerTemplate()

        val pattern: ExchangePattern = camelTask.pattern.map(_.toLowerCase) match {
          case Some("in-only") => ExchangePattern.InOnly
          case Some("out-only") => ExchangePattern.OutOnly
          case _ => ExchangePattern.InOut
        }

        val exchange = producer.send(camelContext.getEndpoint(camelTask.sendUri.getOrElse(camelTask.uri)), pattern, new Processor {
          override def process(exchange: Exchange): Unit = {
            camelTask.headers.getOrElse(Map.empty).foreach {
              case (key, value) => exchange.getIn.setHeader(key, value)
            }

            val body = camelTask.bodyTemplate.map(replaceContextInTemplate(_, context).get).orNull

            exchange.getIn.setBody(body)
          }
        })

        Option(exchange.getException).foreach(throw _)
        exchange

      case "consumer" =>
        val consumer = camelContext.createConsumerTemplate()

        val exchange = consumer.receive(camelTask.receiveUri.getOrElse(camelTask.uri))
        exchange.setOut(exchange.getIn)

        Option(exchange.getException).foreach(throw _)
        exchange
    }

    camelTask.to.filter(_.nonEmpty) match {
      case None => exchange

      case Some(toEndpoints) =>
        val routeBuilder = new RouteBuilder() {
          override def configure(): Unit = {
            val root = from(camelTask.uri)

            toEndpoints.foreach { toUri =>
              root.to(toUri)
            }
          }
        }

        camelContext.addRoutes(routeBuilder)

        camelContext.start()
        val result = exchange
        camelContext.stop()

        result
    }

  }

  def evaluateExpression[T, I](
    extractExpression: ExtractExpression,
    exchange: Exchange)(implicit targetType: Manifest[T], itemType: Manifest[I]): Try[T] = Try(extractExpression.`type`.toLowerCase match {
    case "jsonpath" =>
      val jsonPathExpression = new JsonPathExpression(extractExpression.expression.trim)
      jsonPathExpression.init()

      // json path expression works only on in message
      val expressionExchange = exchange.copy(true)
      expressionExchange.getIn.setBody(exchange.getOut.getBody)

      val targetTypeClass: Class[T] = targetType.runtimeClass.asInstanceOf[Class[T]]
      val itemTypeClass: Class[I] = itemType.runtimeClass.asInstanceOf[Class[I]]

      jsonPathExpression.evaluate(expressionExchange, classOf[Object]) match {
        case javaList: java.util.List[Any] if javaList.size() == 1 && extractExpression.extractSingle.getOrElse(true) =>
          exchange.getContext.getTypeConverter.convertTo(targetTypeClass, javaList.get(0))
        case other: java.util.List[Any] =>
          val resultList: util.List[I] = new util.ArrayList[I]()

          other.asScala.foreach(item => resultList.add(objectMapper.convertValue(item, itemTypeClass)))

          resultList.asInstanceOf[T]
        case other: Any => exchange.getContext.getTypeConverter.convertTo(targetTypeClass, other)
      }

    case "simple" =>
      SimpleLanguage.simple(extractExpression.expression).evaluate(exchange, targetType.runtimeClass.asInstanceOf[Class[T]])
  })

  def executeExchange(
    camelTask: CamelTask,
    context: Seq[FlowInstanceContextValue],
    logId: LogId)(taskLogger: Logger): IO[(Exchange, Seq[FlowInstanceContextValue])] =
    for {
      camelContext <- createCamelContext(camelTask)
      result <- createExchange(camelTask, context, logId)(taskLogger)
      exchange <- IO(result(camelContext)).map { exchange =>
        if (camelTask.convertStreamToString.getOrElse(true) && exchange.getOut.getBody.isInstanceOf[InputStream]) {
          exchange.getOut.setBody(exchange.getOut.getBody(classOf[String]))
        }
        exchange
      }
      contextValues <- IO.fromEither {
        val expressionsValues: List[Try[FlowInstanceContextValue]] = camelTask
          .extract
          .getOrElse(Seq.empty)
          .map { extract =>
            evaluateExpression[String, Nothing](extract, exchange).map(FlowInstanceContextValue(extract.name, _))
          }.toList

        Traverse[List].sequence(expressionsValues) match {
          case Success(contextValues) => Right(contextValues)
          case Failure(error) => Left(error)
        }
      }
    } yield (exchange, contextValues)
}
