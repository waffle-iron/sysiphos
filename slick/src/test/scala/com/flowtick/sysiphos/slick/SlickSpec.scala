package com.flowtick.sysiphos.slick

import java.sql.Connection

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, FlatSpec, Matchers }
import slick.jdbc.DriverDataSource

trait SlickSpec extends FlatSpec
  with Matchers
  with SlickRepositoryMigrations
  with BeforeAndAfterAll
  with ScalaFutures {

  val dataSource = new DriverDataSource(
    url = "jdbc:h2:mem:sysiphos;DB_CLOSE_DELAY=-1", // wait for VM to die for closing in-memory db
    user = "sa",
    password = "")

  override def createJdbcConnection: Connection = dataSource.getConnection()

  override protected def beforeAll(): Unit = {
    updateDatabase
  }
}
