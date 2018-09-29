package com.flowtick.sysiphos.slick

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.scheduler.FlowScheduleDetails
import org.scalatest.Inside
import slick.jdbc.H2Profile

class SlickFlowScheduleRepositorySpec extends SlickSpec with Inside {
  val slickScheduleRepository = new SlickFlowScheduleRepository(dataSource)(H2Profile, scala.concurrent.ExecutionContext.Implicits.global)

  "Slick Schedule Repository" should "create schedule" in new RepositoryContext {
    override def currentUser: String = "test-user"

    val newSchedule = slickScheduleRepository
      .createFlowSchedule(Some("id"), Some("expression"), "definition_id", None, None, None)(this)
      .futureValue

    inside(newSchedule) {
      case FlowScheduleDetails(_, _, _, _, updated, expression, flowDefinitionId, flowTaskId, nextDueDate, enabled, backFill) =>
        updated should be(None)
        expression should be(Some("expression"))
        flowDefinitionId should be("definition_id")
        flowTaskId should be(None)
        nextDueDate should be(None)
        enabled should be(None)
        backFill should be(None)
    }
  }

  it should "update a schedule" in new RepositoryContext {
    override def currentUser: String = "test-user"
    override def epochSeconds: Long = 42

    val newSchedule: FlowScheduleDetails = slickScheduleRepository
      .createFlowSchedule(Some("new-schedule"), Some("expression"), "definition_id", None, None, None)(this)
      .futureValue

    newSchedule.expression should be(Some("expression"))
    newSchedule.backFill should be(None)
    newSchedule.enabled should be(None)

    val updatedBackFill = slickScheduleRepository
      .updateFlowSchedule(newSchedule.id, Some("new expression"), None, backFill = Some(true))(this)
      .futureValue

    updatedBackFill.expression should be(Some("new expression"))
    updatedBackFill.backFill should be(Some(true))
    updatedBackFill.enabled should be(None)

    val updatedEnabled = slickScheduleRepository
      .updateFlowSchedule(newSchedule.id, Some("new expression"), Some(true), backFill = None)(this)
      .futureValue

    updatedEnabled.expression should be(Some("new expression"))
    updatedEnabled.backFill should be(Some(true))
    updatedEnabled.enabled should be(Some(true))
  }

}
