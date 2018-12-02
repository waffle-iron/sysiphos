package com.flowtick.sysiphos.slick

import java.util.UUID

import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.{ FlatSpec, Matchers }
import slick.jdbc.DriverDataSource

trait SlickSpec extends FlatSpec
  with Matchers
  with SlickRepositoryMigrations
  with ScalaFutures
  with IntegrationPatience {
  def testIds: IdGenerator = new IdGenerator {
    var id = 0

    override def nextId: String = {
      id += 1
      id.toString
    }
  }

  def dataSource = {
    Class.forName(classOf[org.h2.Driver].getName)
    val h2DataSource = new DriverDataSource(
      url = s"jdbc:h2:./target/${UUID.randomUUID().toString}", // wait for VM to die for closing in-memory db
      user = "sa",
      password = "")

    updateDatabase(h2DataSource).get

    h2DataSource
  }
}
