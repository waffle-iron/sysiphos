package com.flowtick.sysiphos.execution

import akka.NotUsed
import akka.actor.{ ActorRef, ActorSystem }
import akka.stream.{ ActorMaterializer, UniqueKillSwitch }
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.testkit.{ TestKit, TestProbe }
import com.flowtick.sysiphos.core.{ DefaultRepositoryContext, RepositoryContext }
import com.flowtick.sysiphos.execution.FlowTaskExecution.{ TaskAck, TaskStreamInitialized }
import com.flowtick.sysiphos.flow.FlowTaskInstanceStatus.FlowTaskInstanceStatus
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.logging.{ ConsoleLogger, Logger }
import com.flowtick.sysiphos.task.CommandLineTask
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ FlatSpecLike, Matchers }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

class FlowInstanceTaskStreamSpec extends TestKit(ActorSystem("task-stream-spec"))
  with FlatSpecLike
  with Matchers
  with FlowInstanceTaskStream
  with FlowInstanceExecution
  with ScalaFutures
  with MockFactory {

  implicit val materializer: ActorMaterializer = ActorMaterializer()(system)

  override implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  override implicit val actorSystem: ActorSystem = system

  val flowInstanceRepository: FlowInstanceRepository = mock[FlowInstanceRepository]
  val flowTaskInstanceRepository: FlowTaskInstanceRepository = mock[FlowTaskInstanceRepository]

  val taskActorProbe = TestProbe()

  override protected def taskActorPool(
    flowInstanceActor: ActorRef,
    flowExecutorActor: ActorRef,
    poolSize: Int,
    logger: Logger): ActorRef = taskActorProbe.ref

  def testStream(
    flowInstanceDetails: FlowInstanceDetails,
    flowInstanceActor: ActorRef,
    flowExecutorActor: ActorRef)(repositoryContext: RepositoryContext): ((SourceQueueWithComplete[FlowTask], UniqueKillSwitch), NotUsed) = createTaskStream(
    flowTaskInstanceRepository,
    flowInstanceRepository,
    flowInstanceActor,
    flowExecutorActor)(
    flowInstanceId = "instance",
    flowDefinitionId = "definition",
    taskParallelism = 1,
    taskRate = 1,
    taskRateDuration = 1.seconds,
    new ConsoleLogger)(repositoryContext).run

  "Flow Instance task stream" should "create instance with context from repository" in new DefaultRepositoryContext("test") {
    override def epochSeconds: Long = 1

    val flowInstanceActor = TestProbe()
    val flowExecutorActor = TestProbe()

    val storedContextValues = Seq(
      FlowInstanceContextValue("foo", "42"))

    val testTask: FlowTask = CommandLineTask(id = "test-task", children = None, command = "ls")

    val flowInstance = FlowInstanceDetails("instance", "definition", 0, Some(0), None, FlowInstanceStatus.Running)
    val flowTaskInstance = FlowTaskInstanceDetails(
      id = "taskInstanceId",
      flowInstanceId = flowInstance.id,
      flowDefinitionId = flowInstance.flowDefinitionId,
      taskId = testTask.id,
      0, None, None, None, 3,
      FlowTaskInstanceStatus.New, 10, None, "log-id")

    val ((queue, killSwith), done) = testStream(flowInstance, flowInstanceActor.ref, flowExecutorActor.ref)(this)

    (flowInstanceRepository.getContextValues(_: String)(_: RepositoryContext))
      .expects(*, *)
      .returning(Future.successful(storedContextValues))
      .atLeastOnce()

    (flowInstanceRepository.findById(_: String)(_: RepositoryContext))
      .expects(*, *)
      .returning(Future.successful(Some(flowInstance)))
      .atLeastOnce()

    (flowTaskInstanceRepository.findOne(_: FlowTaskInstanceQuery)(_: RepositoryContext))
      .expects(*, *)
      .returning(Future.successful(None))
      .atLeastOnce()

    (flowTaskInstanceRepository.createFlowTaskInstance(_: String, _: String, _: String, _: String, _: Int, _: Long, _: Option[Long], _: Option[FlowTaskInstanceStatus.FlowTaskInstanceStatus])(_: RepositoryContext))
      .expects(*, *, *, *, *, *, *, *, *)
      .returning(Future.successful(flowTaskInstance))
      .atLeastOnce()

    (flowTaskInstanceRepository.setStartTime(_: String, _: Long)(_: RepositoryContext))
      .expects(flowTaskInstance.id, 1, *)
      .returning(Future.successful(Some(flowTaskInstance)))
      .atLeastOnce()

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus, _: Option[Int], _: Option[Long])(_: RepositoryContext))
      .expects(flowTaskInstance.id, FlowTaskInstanceStatus.Running, *, *, *)
      .returning(Future.successful(Some(flowTaskInstance)))
      .atLeastOnce()

    queue.offer(testTask).futureValue

    taskActorProbe.expectMsg(TaskStreamInitialized)
    taskActorProbe.reply(TaskAck)

    taskActorProbe.fishForSpecificMessage() {
      case FlowTaskExecution.Execute(_, _, executionContextValues) => storedContextValues.equals(executionContextValues)
    }
  }

}
