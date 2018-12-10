package com.flowtick.sysiphos.execution.task

import java.io.InputStream

import cats.effect.IO
import com.flowtick.sysiphos.execution.WireMockSupport
import com.flowtick.sysiphos.flow.{ FlowInstanceContextValue, FlowInstanceDetails, FlowInstanceStatus }
import com.flowtick.sysiphos.logging.ConsoleLogger
import com.flowtick.sysiphos.task.{ CamelTask, ExtractSpec, RegistryEntry }
import com.github.tomakehurst.wiremock.client.WireMock.{ aResponse, equalTo, post, urlEqualTo }
import org.apache.camel.component.mock.MockEndpoint
import org.scalatest.{ FlatSpec, Matchers, Succeeded }

class CamelTaskExecutionSpec extends FlatSpec with CamelTaskExecution with Matchers {
  val flowInstance = FlowInstanceDetails(
    status = FlowInstanceStatus.Scheduled,
    id = "camel-instance",
    flowDefinitionId = "camel-flow",
    creationTime = 1L,
    context = Seq(
      FlowInstanceContextValue("foo", "bar")),
    startTime = None,
    endTime = None)

  "Camel execution" should "execute camel task" in {
    val task = CamelTask(
      id = "camel-task",
      uri = "direct:input",
      to = Some(Seq("mock:test")),
      bodyTemplate = Some("hello ${foo}"),
      headers = Some(Map("bar" -> "baz")),
      children = None)

    val runExchange: IO[Unit] = for {
      camelContext <- createCamelContext(task)
      result <- createExchange(task, flowInstance, "test")(new ConsoleLogger)
    } yield {
      val mockEndpoint = camelContext.getEndpoint("mock:test", classOf[MockEndpoint])

      mockEndpoint.expectedBodiesReceived("hello bar")
      mockEndpoint.expectedHeaderReceived("bar", "baz")

      result(camelContext)

      mockEndpoint.assertIsSatisfied()
    }

    runExchange.unsafeRunSync()
  }

  it should "execute HTTP GET request" in new WireMockSupport {
    import com.github.tomakehurst.wiremock.client.WireMock._

    val result: Any = withWireMock(server => IO.delay {
      server.stubFor(get(urlEqualTo("/get-test"))
        .withHeader("bar", equalTo("baz"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody("Some get content")))
    }.flatMap { _ =>
      executeExchange(CamelTask(
        id = "camel-task",
        uri = s"http4://localhost:${server.port}/get-test",
        bodyTemplate = None,
        headers = Some(Map("bar" -> "baz")),
        children = None), flowInstance, "test")(taskLogger = new ConsoleLogger).map(_._1.getOut.getBody)
    }).unsafeRunSync()

    result should be("Some get content")
  }

  it should "execute HTTP GET request without conversion" in new WireMockSupport {
    import com.github.tomakehurst.wiremock.client.WireMock._

    val result: Any = withWireMock(server => IO.delay {
      server.stubFor(get(urlEqualTo("/get-test"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody("Some get content")))
    }.flatMap { _ =>
      executeExchange(CamelTask(
        id = "camel-task",
        uri = s"http4://localhost:${server.port}/get-test",
        bodyTemplate = None,
        convertStreamToString = Some(false),
        children = None), flowInstance, "test")(taskLogger = new ConsoleLogger).map(_._1.getOut.getBody)
    }).unsafeRunSync()

    scala.io.Source.fromInputStream(result.asInstanceOf[InputStream]).getLines().mkString should
      be("Some get content")
  }

  it should "execute HTTP POST request" in new WireMockSupport {
    import com.github.tomakehurst.wiremock.client.WireMock._

    val result: Any = withWireMock(server => IO.delay {
      server.stubFor(post(urlEqualTo("/post-test"))
        .withRequestBody(equalTo("body bar"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody("Some post response")))
    }.flatMap { _ =>
      executeExchange(CamelTask(
        id = "camel-task",
        uri = s"http4://localhost:${server.port}/post-test",
        bodyTemplate = Some("body ${foo}"),
        headers = Some(Map("bar" -> "baz")),
        children = None), flowInstance, "test")(taskLogger = new ConsoleLogger).map(_._1.getOut.getBody)
    }).unsafeRunSync()

    result should be("Some post response")
  }

  it should "extract a value from a HTTP response with JsonPath" in new WireMockSupport {
    import com.github.tomakehurst.wiremock.client.WireMock._

    val (exchange, contextValues) = withWireMock(server => IO.delay {
      server.stubFor(get(urlEqualTo("/extract-test"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody(s"""{ "key" : "foo" }""")))
    }.flatMap { _ =>
      executeExchange(CamelTask(
        id = "camel-task",
        uri = s"http4://localhost:${server.port}/extract-test",
        bodyTemplate = None,
        extract = Some(Seq(ExtractSpec("jsonpath", "extracted", "$.key"), ExtractSpec("jsonpath", "extracted2", "$.key"))),
        children = None), flowInstance, "test")(taskLogger = new ConsoleLogger)
    }).unsafeRunSync()

    contextValues should contain allOf (FlowInstanceContextValue("extracted", "foo"), FlowInstanceContextValue("extracted2", "foo"))
    exchange.getOut.getBody should be(s"""{ "key" : "foo" }""")
  }

  it should "send a slack message" in new WireMockSupport {
    val result: Any = withWireMock(server => IO.delay {
      server.stubFor(post(urlEqualTo("/services/a/b/c"))
        .withRequestBody(equalTo("{\"icon_url\":null,\"channel\":\"#a-channel\",\"text\":\"test bar\",\"icon_emoji\":null,\"username\":null}"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody("slack approves this message")))
    }.flatMap { _ =>
      executeExchange(CamelTask(
        id = "camel-task",
        uri = s"slack:#a-channel?webhookUrl=http://localhost:${server.port()}/services/a/b/c",
        bodyTemplate = Some("test ${foo}"),
        children = None), flowInstance, "test")(taskLogger = new ConsoleLogger).map(_ => {
        server.findAllUnmatchedRequests.size() should be(0)
      })
    }).unsafeRunSync()

    result should be(Succeeded)
  }

  it should "execute a sql query" in {
    val (exchange, contextValues) = executeExchange(CamelTask(
      id = "camel-task",
      uri = s"sql:select 1+1 as result?dataSource=testDs",
      exchangeType = Some("consumer"),
      extract = Some(Seq(ExtractSpec("simple", "extracted", "${body.get(\"RESULT\")}"))),
      children = None,
      registry = Some(
        Map("testDs" -> RegistryEntry(
          `type` = "bean",
          fqn = classOf[org.h2.jdbcx.JdbcDataSource].getName,
          properties = Some(Map(
            "url" -> "jdbc:h2:./target/test-h2",
            "user" -> "sa",
            "password" -> "sa")))))), flowInstance, "test")(taskLogger = new ConsoleLogger).unsafeRunSync()

    exchange.getOut.getBody.asInstanceOf[java.util.Map[String, Any]].get("RESULT") should be(2)
    contextValues should contain only FlowInstanceContextValue("extracted", "2")
  }

  it should "invoke a bean method" in {
    val (exchange, _) = executeExchange(CamelTask(
      id = "bean-task",
      uri = "direct:bean",
      to = Some(Seq(
        "bean:myBean?method=doStuff")),
      children = None,
      registry = Some(
        Map("myBean" -> RegistryEntry(
          `type` = "bean",
          fqn = classOf[MyBean].getName,
          properties = None)))), flowInstance, "test")(taskLogger = new ConsoleLogger).unsafeRunSync()

    exchange.getContext.getRegistry.lookupByNameAndType("myBean", classOf[MyBean]).called should be(1)
  }

}

class MyBean() {
  var called = 0

  def doStuff(): Unit = {
    called = 1
  }
}

