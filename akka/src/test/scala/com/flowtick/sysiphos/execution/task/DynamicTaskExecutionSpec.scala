package com.flowtick.sysiphos.execution.task

import cats.effect.IO
import com.flowtick.sysiphos.execution.WireMockSupport
import com.flowtick.sysiphos.flow.FlowDefinition.ItemSpec
import com.flowtick.sysiphos.flow.FlowInstanceContextValue
import com.flowtick.sysiphos.logging.ConsoleLogger
import com.flowtick.sysiphos.task.DynamicTask
import org.scalatest.{ FlatSpec, Matchers }
import com.github.tomakehurst.wiremock.client.WireMock._

class DynamicTaskExecutionSpec extends FlatSpec with DynamicTaskExecution with Matchers {
  "Dynamic Task execution" should "get configurations via Camel URI" in new WireMockSupport {
    val dynamicTaskConfigurations: TaskConfigurations = withWireMock(server => IO.delay {
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
               |          "id": "1",
               |          "businessKey": "1",
               |          "properties": [
               |            {
               |              "key": "key1",
               |              "value": "value1"
               |            }
               |          ]
               |        },
               |        {
               |          "id": "2",
               |          "businessKey": "2",
               |          "properties": [
               |            {
               |              "key": "key",
               |              "value": "value"
               |            }
               |          ]
               |        },
               |        {
               |          "id": "3",
               |          "businessKey": "3",
               |          "properties": [
               |            {
               |              "key": "key",
               |              "value": "value"
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
        items = ItemSpec("jsonpath", "$.data.configurations"))

      getConfigurations(0, 100, dynamicTask, "test")(new ConsoleLogger)
    }).unsafeRunSync()

    dynamicTaskConfigurations.limit should be(Some(100))
    dynamicTaskConfigurations.offset should be(Some(0))
    dynamicTaskConfigurations.configurations should be(
      Seq(
        TaskConfiguration(id = "1", businessKey = "1", contextValues = Seq(FlowInstanceContextValue("key1", "value1"))),
        TaskConfiguration(id = "2", businessKey = "2", contextValues = Seq(FlowInstanceContextValue("key", "value"))),
        TaskConfiguration(id = "3", businessKey = "3", contextValues = Seq(FlowInstanceContextValue("key", "value")))))

  }
}
