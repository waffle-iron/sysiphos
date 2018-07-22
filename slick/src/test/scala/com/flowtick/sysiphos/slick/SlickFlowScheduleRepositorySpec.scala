package com.flowtick.sysiphos.slick

import com.flowtick.sysiphos.core.RepositoryContext
import slick.jdbc.H2Profile

class SlickFlowScheduleRepositorySpec extends SlickSpec {
  val slickScheduleRepository = new SlickFlowScheduleRepository(dataSource)(H2Profile, scala.concurrent.ExecutionContext.Implicits.global)

  "Slick Schedule Repository" should "create schedule" in new RepositoryContext {
    override def currentUser: String = "test-user"

    slickScheduleRepository.createFlowSchedule("id", "expression", "definition_id", None, None, Some(true))(this).futureValue
  }

}
