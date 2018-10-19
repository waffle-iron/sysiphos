package com.flowtick.sysiphos.execution

import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit }
import com.flowtick.sysiphos.execution.FlowExecutorActor.Tick
import com.flowtick.sysiphos.flow.{ FlowDefinitionRepository, FlowInstanceRepository, FlowTaskInstanceRepository }
import com.flowtick.sysiphos.scheduler.{ FlowScheduleRepository, FlowScheduleStateStore, FlowScheduler }
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }

import scala.concurrent.ExecutionContext.Implicits.global

class FlowExecutorActorSpec extends TestKit(ActorSystem("MySpec")) with ImplicitSender
  with FlatSpecLike with Matchers with BeforeAndAfterAll with MockFactory {

  val flowScheduleRepository: FlowScheduleRepository = mock[FlowScheduleRepository]
  val flowInstanceRepository: FlowInstanceRepository = mock[FlowInstanceRepository]
  val flowDefinitionRepository: FlowDefinitionRepository = mock[FlowDefinitionRepository]
  val flowTaskInstanceRepository: FlowTaskInstanceRepository = mock[FlowTaskInstanceRepository]
  val flowScheduleStateStore: FlowScheduleStateStore = mock[FlowScheduleStateStore]
  val flowScheduler: FlowScheduler = mock[FlowScheduler]

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "FlowExecutionActor" should "execute new instances on tick" in {
    val flowExecutorActorProps = Props(
      new FlowExecutorActor(
        flowScheduleRepository,
        flowInstanceRepository,
        flowDefinitionRepository,
        flowTaskInstanceRepository,
        flowScheduleStateStore,
        flowScheduler))

    val flowExecutorActor: TestActorRef[FlowExecutorActor] = TestActorRef.apply[FlowExecutorActor](flowExecutorActorProps)

    flowExecutorActor ! Tick()
  }
}
