package com.flowtick.sysiphos.execution.task

import cats.effect.IO
import com.flowtick.sysiphos.execution.WireMockSupport
import com.flowtick.sysiphos.flow.FlowDefinition.ItemSpec
import com.flowtick.sysiphos.flow.FlowInstanceContextValue
import com.flowtick.sysiphos.logging.ConsoleLogger
import com.flowtick.sysiphos.task.{ CamelTask, DynamicTask }
import org.scalatest.{ FlatSpec, Matchers }
import com.github.tomakehurst.wiremock.client.WireMock._

class DynamicTaskExecutionSpec extends FlatSpec with DynamicTaskExecution with Matchers {
  "Dynamic Task execution" should "get configurations via Camel URI" in new WireMockSupport {
    val dynamicTaskConfigurations: DynamicTaskConfigurations = withWireMock(server => IO.delay {
      server.stubFor(get(urlEqualTo("/configurations"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody(
            s"""
               |{
               |  "data": {
               |    "configurations": {
               |      "items": [
               |        {
               |          "providerConfig": [
               |            {
               |              "key": "key1",
               |              "value": "value1"
               |            }
               |          ]
               |        },
               |        {
               |          "providerConfig": [
               |            {
               |              "key": "key",
               |              "value": "value"
               |            }
               |          ]
               |        },
               |        {
               |          "providerConfig": [
               |            {
               |              "key": "key",
               |              "value": "value"
               |            }
               |          ]
               |        },
               |        {
               |          "providerConfig": [
               |            {
               |              "key": "key",
               |              "value": "value"
               |            }
               |          ]
               |        },
               |        {
               |          "providerConfig": [
               |            {
               |              "key": "key",
               |              "value": "value"
               |            }
               |          ]
               |        },
               |        {
               |          "providerConfig": [
               |            {
               |              "key": "key",
               |              "value": "value"
               |            }
               |          ]
               |        },
               |        {
               |          "providerConfig": [
               |            {
               |              "key": "key",
               |              "value": "value"
               |            }
               |          ]
               |        },
               |        {
               |          "providerConfig": [
               |            {
               |              "key": "key",
               |              "value": "value"
               |            }
               |          ]
               |        },
               |        {
               |          "providerConfig": [
               |            {
               |              "key": "key",
               |              "value": "value"
               |            }
               |          ]
               |        },
               |        {
               |          "providerConfig": [
               |            {
               |              "key": "key10",
               |              "value": "value10"
               |            }
               |          ]
               |        }
               |      ],
               |      "offset": 0,
               |      "limit": 10
               |    }
               |  }
               |}
             """.stripMargin)))

    }.flatMap { _ =>
      val dynamicTask = DynamicTask(
        id = "foo",
        contextSourceUri = s"http4://localhost:${server.port()}/configurations",
        items = ItemSpec("jsonpath", "$.data.configurations..providerConfig"))

      getConfigurations(0, 100, dynamicTask, "test")(new ConsoleLogger)
    }).unsafeRunSync()

    dynamicTaskConfigurations.limit should be(100)
    dynamicTaskConfigurations.offset should be(0)
    dynamicTaskConfigurations.configurations.should(be(
      Seq(
        DynamicTaskConfiguration(contextValues = Seq(FlowInstanceContextValue("key1", "value1"))),
        DynamicTaskConfiguration(contextValues = Seq(FlowInstanceContextValue("key", "value"))),
        DynamicTaskConfiguration(contextValues = Seq(FlowInstanceContextValue("key", "value"))),
        DynamicTaskConfiguration(contextValues = Seq(FlowInstanceContextValue("key", "value"))),
        DynamicTaskConfiguration(contextValues = Seq(FlowInstanceContextValue("key", "value"))),
        DynamicTaskConfiguration(contextValues = Seq(FlowInstanceContextValue("key", "value"))),
        DynamicTaskConfiguration(contextValues = Seq(FlowInstanceContextValue("key", "value"))),
        DynamicTaskConfiguration(contextValues = Seq(FlowInstanceContextValue("key", "value"))),
        DynamicTaskConfiguration(contextValues = Seq(FlowInstanceContextValue("key", "value"))),
        DynamicTaskConfiguration(contextValues = Seq(FlowInstanceContextValue("key10", "value10"))))))

  }
}
