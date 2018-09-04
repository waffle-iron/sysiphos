package com.flowtick.sysiphos.execution

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit, TestProbe }
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.FlowDefinition.SysiphosDefinition
import com.flowtick.sysiphos.flow.{ FlowTaskInstance, _ }
import com.flowtick.sysiphos.task.CommandLineTask
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }

import scala.concurrent.Future

class FlowInstanceExecutorActorSpec extends TestKit(ActorSystem("MySpec")) with ImplicitSender with MockFactory
  with FlatSpecLike with Matchers with BeforeAndAfterAll {

  val flowInstanceRepository: FlowInstanceRepository = stub[FlowInstanceRepository]
  val flowTaskInstanceRepository: FlowTaskInstanceRepository[FlowTaskInstance] = stub[FlowTaskInstanceRepository[FlowTaskInstance]]

  val flowInstance: FlowInstance = new FlowInstance {
    override def status: String = "new"

    override def context: Seq[FlowInstanceContextValue] = Seq.empty

    override def id: String = "???"

    override def flowDefinitionId: String = flowDefinition.id

    override def creationTime: Long = 1L

    override def startTime: Option[Long] = None

    override def endTime: Option[Long] = None

    override def retries: Int = 3
  }

  lazy val flowTaskInstance = FlowTaskInstanceDetails(
    id = "task-id",
    flowInstanceId = flowInstance.id,
    taskId = flowDefinition.task.id,
    creationTime = 1l,
    startTime = None,
    endTime = None,
    retries = 0,
    status = "new")

  val flowDefinition: FlowDefinition = SysiphosDefinition("ls-definition-id", CommandLineTask("ls-task-id", None, "ls"))

  implicit val repositoryContext: RepositoryContext = new RepositoryContext {
    override def currentUser: String = "test-user"
  }

  val flowInstanceActorProps = Props(
    new FlowInstanceExecutorActor(
      flowInstance,
      flowInstanceRepository,
      flowTaskInstanceRepository)(repositoryContext))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "FlowInstanceExecutorActor" should "create on start task definition list" in {
    val flowInstanceExecutorActor = TestActorRef.apply[FlowInstanceExecutorActor](flowInstanceActorProps)
    (flowTaskInstanceRepository.getFlowTaskInstances(_: String)(_: RepositoryContext))
      .when(flowInstance.id, *)
      .returns(Future.successful(Seq(flowTaskInstance)))

    flowInstanceExecutorActor ! FlowInstanceExecution.Init(flowDefinition)
    flowInstanceExecutorActor.underlyingActor.taskDefinitions should be(List(CommandLineTask("ls-task-id", None, "ls")))
  }

  it should "send proper message to flow task executor" in {

    val flowTaskExecutorProbe = TestProbe()

    val flowInstanceExecutorActor = TestActorRef(
      new FlowInstanceExecutorActor(flowInstance, flowInstanceRepository, flowTaskInstanceRepository) {
        override def flowTaskExecutor: ActorRef = flowTaskExecutorProbe.ref
      })

    (flowTaskInstanceRepository.getFlowTaskInstances(_: String)(_: RepositoryContext))
      .when(flowInstance.id, *)
      .returns(Future.successful(Seq(flowTaskInstance)))

    (flowTaskInstanceRepository.setStatus(_: String, _: String)(_: RepositoryContext))
      .when(flowTaskInstance.id, "running", *)
      .returns(Future.successful(()))

    flowInstanceExecutorActor ! FlowInstanceExecution.Init(flowDefinition)

    flowTaskExecutorProbe.expectMsg(FlowTaskExecution.Execute(flowDefinition.task, flowTaskInstance))

  }
}
