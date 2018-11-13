package com.flowtick.sysiphos.execution.task

import java.io.InputStream

import cats.effect.IO
import com.flowtick.sysiphos.execution.WireMockSupport
import com.flowtick.sysiphos.flow.{ FlowInstanceContextValue, FlowInstanceDetails, FlowInstanceStatus }
import com.flowtick.sysiphos.logging.ConsoleLogger
import com.flowtick.sysiphos.task.CamelTask
import org.apache.camel.component.mock.MockEndpoint
import org.scalatest.{ FlatSpec, Matchers }

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
    val mockEndpoint = camelContext.getEndpoint("mock:test", classOf[MockEndpoint])
    mockEndpoint.expectedBodiesReceived("hello bar")
    mockEndpoint.expectedHeaderReceived("bar", "baz")

    executeExchange(CamelTask(
      id = "camel-task",
      uri = "mock:test",
      bodyTemplate = Some("hello ${foo}"),
      headers = Some(Map("bar" -> "baz")),
      children = None), flowInstance, "test")(taskLogger = new ConsoleLogger).unsafeRunSync()

    mockEndpoint.assertIsSatisfied()
  }

  it should "execute HTTP GET request" in new WireMockSupport {
    import com.github.tomakehurst.wiremock.client.WireMock._

    val result: AnyRef = withWireMock(server => IO.delay {
      server.stubFor(get(urlEqualTo("/get-test"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody("Some get content")))
    }.flatMap { _ =>
      executeExchange(CamelTask(
        id = "camel-task",
        uri = s"http4://localhost:${server.port}/get-test",
        bodyTemplate = None,
        headers = Some(Map("bar" -> "baz")),
        children = None), flowInstance, "test")(taskLogger = new ConsoleLogger)
    }).unsafeRunSync()

    scala.io.Source.fromInputStream(result.asInstanceOf[InputStream]).getLines().mkString should
      be("Some get content")
  }

  it should "execute HTTP POST request" in new WireMockSupport {
    import com.github.tomakehurst.wiremock.client.WireMock._

    val result: AnyRef = withWireMock(server => IO.delay {
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
        children = None), flowInstance, "test")(taskLogger = new ConsoleLogger)
    }).unsafeRunSync()

    scala.io.Source.fromInputStream(result.asInstanceOf[InputStream]).getLines().mkString should
      be("Some post response")
  }

}
