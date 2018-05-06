package com.flowtick.sysiphos.slick

import com.flowtick.sysiphos.core.RepositoryContext
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ FlatSpec, Matchers }
import slick.jdbc.{ DriverDataSource, H2Profile }

class SlickFlowScheduleRepositorySpec extends FlatSpec with Matchers with ScalaFutures {

  val slickScheduleRepository = new SlickFlowScheduleRepository(new DriverDataSource(
    url = "jdbc:h2:mem:sysiphos",
    user = "sa",
    password = ""))(H2Profile, scala.concurrent.ExecutionContext.Implicits.global)

  "Slick Schedule Repository" should "create schedule" in new RepositoryContext {
    override def currentUser: String = "test-user"

    slickScheduleRepository.addFlowSchedule("id", "expression", "definition_id", None, None, Some(true))(this).futureValue
  }

}
