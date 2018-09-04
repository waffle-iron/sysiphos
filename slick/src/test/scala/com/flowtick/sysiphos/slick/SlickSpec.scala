package com.flowtick.sysiphos.slick

import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.{ BeforeAndAfterAll, FlatSpec, Matchers }
import slick.jdbc.DriverDataSource

trait SlickSpec extends FlatSpec
  with Matchers
  with SlickRepositoryMigrations
  with BeforeAndAfterAll
  with ScalaFutures
  with IntegrationPatience {

  lazy val dataSource = {
    Class.forName(classOf[org.h2.Driver].getName)

    new DriverDataSource(
      url = s"jdbc:h2:mem:${getClass.getName};DB_CLOSE_DELAY=-1", // wait for VM to die for closing in-memory db
      user = "sa",
      password = "")
  }

  override protected def beforeAll(): Unit = {
    updateDatabase(dataSource)
  }
}
