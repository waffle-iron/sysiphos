package com.flowtick.sysiphos.execution

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKit }
import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }

class FlowExecutorActorSpec extends TestKit(ActorSystem("MySpec")) with ImplicitSender
  with FlatSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "FlowExecutionActor" should "do some nice things" in {

  }
}
