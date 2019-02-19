package com.flowtick.sysiphos.slick

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow._
import slick.jdbc.H2Profile
import scala.concurrent.ExecutionContext.Implicits.global

class SlickFlowInstanceRepositorySpec extends SlickSpec {
  def createTestRepository = new SlickFlowInstanceRepository(dataSource, testIds)(H2Profile, scala.concurrent.ExecutionContext.Implicits.global)

  "Slick Instance Repository" should "create instance" in new RepositoryContext {
    override def currentUser: String = "test-user"
    override def epochSeconds: Long = 0

    val instanceRepository = createTestRepository

    instanceRepository.getFlowInstances.futureValue should be(empty)

    val newInstance: FlowInstanceContext = instanceRepository.createFlowInstance("some-definition", Seq(FlowInstanceContextValue("foo", "bar")), FlowInstanceStatus.Scheduled)(this).futureValue
    val instancesWithContext: Seq[FlowInstance] = instanceRepository.getFlowInstances(FlowInstanceQuery(flowDefinitionId = Some("some-definition"), None, None, None))(this).futureValue

    instanceRepository.getContextValues(newInstance.instance.id)(this).futureValue should be(Seq(FlowInstanceContextValue("foo", "bar")))
    instancesWithContext.head.status should be(FlowInstanceStatus.Scheduled)
  }

  it should "find instances by query" in new RepositoryContext {
    override def currentUser: String = "test-user"
    override def epochSeconds: Long = 0

    val instanceRepository = createTestRepository

    val newInstance: FlowInstanceContext =
      instanceRepository.createFlowInstance("some-definition", Seq(FlowInstanceContextValue("foo", "bar")), FlowInstanceStatus.Scheduled)(this).futureValue

    val anotherInstance: FlowInstanceContext =
      instanceRepository.createFlowInstance("some-definition", Seq(FlowInstanceContextValue("bar", "baz"), FlowInstanceContextValue("mup", "moep")), FlowInstanceStatus.Triggered)(this).futureValue

    instanceRepository
      .getFlowInstances(FlowInstanceQuery(flowDefinitionId = Some("some-definition")))(this)
      .futureValue should contain only (newInstance.instance, anotherInstance.instance)

    instanceRepository
      .getFlowInstances(FlowInstanceQuery(None, status = Some(Seq(FlowInstanceStatus.Triggered))))(this)
      .futureValue should contain only anotherInstance.instance
  }

  it should "update instance" in new RepositoryContext {
    override def currentUser: String = "test-user"
    override def epochSeconds: Long = 0

    val instanceRepository = createTestRepository

    val newInstance: FlowInstanceContext =
      instanceRepository.createFlowInstance("some-definition", Seq(FlowInstanceContextValue("foo", "bar")), FlowInstanceStatus.Scheduled)(this).futureValue

    val result = (for {
      _ <- instanceRepository.setStartTime(newInstance.instance.id, 42)(this)
      _ <- instanceRepository.setEndTime(newInstance.instance.id, 43)(this)
      status <- instanceRepository.setStatus(newInstance.instance.id, FlowInstanceStatus.Done)(this)
    } yield status).futureValue

    result should be(
      Some(newInstance.instance.copy(startTime = Some(42), endTime = Some(43), status = FlowInstanceStatus.Done)))
  }

  it should "update query" in new RepositoryContext {
    override def currentUser: String = "test-user"
    override def epochSeconds: Long = 0

    val instanceRepository = createTestRepository

    val newInstance: FlowInstanceContext =
      instanceRepository.createFlowInstance("some-definition", Seq(FlowInstanceContextValue("foo", "bar")), FlowInstanceStatus.Scheduled)(this).futureValue
    val query = FlowInstanceQuery(instanceIds = Some(Seq(newInstance.instance.id)))

    newInstance.instance.error should be(None)

    instanceRepository.update(query, FlowInstanceStatus.Failed, Some(new RuntimeException("error")))(this).futureValue

    instanceRepository.findById(newInstance.instance.id)(this).futureValue.get.error should be(defined)
  }

  it should "add context values" in new RepositoryContext {
    override def currentUser: String = "test-user"

    override def epochSeconds: Long = 0

    val instanceRepository = createTestRepository

    val newInstance: FlowInstanceContext =
      instanceRepository.createFlowInstance("some-definition", Seq(FlowInstanceContextValue("foo", "bar")), FlowInstanceStatus.Scheduled)(this).futureValue

    val updatedInstance = instanceRepository.insertOrUpdateContextValues(newInstance.instance.id, Seq(
      FlowInstanceContextValue("foo", "newFooValue"),
      FlowInstanceContextValue("bar", "barValue")))(this).futureValue

    val updatedContextValues = instanceRepository.getContextValues(newInstance.instance.id)(this).futureValue

    updatedContextValues should be(Seq(
      FlowInstanceContextValue("foo", "newFooValue"),
      FlowInstanceContextValue("bar", "barValue")))
  }

  it should "delete flow instances from repository" in new RepositoryContext {
    override def currentUser: String = "test-user"
    override def epochSeconds: Long = 0

    val instanceRepository = createTestRepository

    val newInstance: FlowInstanceContext = instanceRepository.createFlowInstance("some-definition", Seq(FlowInstanceContextValue("foo", "bar")), FlowInstanceStatus.Scheduled)(this).futureValue

    instanceRepository.deleteFlowInstance(newInstance.instance.id)(this).futureValue should be(newInstance.instance.id)
    val afterDelete = instanceRepository.getFlowInstances(FlowInstanceQuery(flowDefinitionId = Some("some-definition"), None, None, None))(this).futureValue
    afterDelete should be(Seq.empty)
  }
}
