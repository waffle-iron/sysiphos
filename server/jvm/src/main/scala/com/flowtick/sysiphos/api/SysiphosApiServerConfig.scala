package com.flowtick.sysiphos.api

import slick.jdbc._
import com.flowtick.sysiphos.config.Configuration._

trait SysiphosApiServerConfig {
  def bindAddress: String = propOrEnv("http.bind.address").getOrElse("0.0.0.0")
  def httpPort: Int = propOrEnv("PORT0").orElse(propOrEnv("http.port")).getOrElse("8080").toInt
  def repoBaseDir: String = propOrEnv("repo.base.dir", defaultValue = ".sysiphos")
  def flowDefinitionsRemoteUrl: Option[String] = propOrEnv("flow.definitions.remote.url")
  def flowSchedulesRemoteUrl: Option[String] = propOrEnv("flow.schedules.remote.url")

  def dbProfileName: String = propOrEnv("database.profile", "h2")
  def dbUrl: String = propOrEnv("database.url", "jdbc:h2:mem:sysiphos;DB_CLOSE_DELAY=-1")

  def dataSource(jdbcProfile: JdbcProfile) = new DriverDataSource(
    dbUrl,
    propOrEnv("database.user", "sa"),
    propOrEnv("database.password", ""),
    driverClassName = jdbcProfile match {
      case MySQLProfile => classOf[com.mysql.jdbc.Driver].getName
      case H2Profile => classOf[org.h2.Driver].getName
      case _ => throw new RuntimeException(s"unknown driver for $dbProfileName")
    })

  def dbProfile = dbProfileName match {
    case "mysql" => MySQLProfile
    case "h2" => H2Profile
    case _ => throw new RuntimeException(s"unsupported database profile $dbProfileName")
  }

  def instanceThreads: Int = propOrEnv("instance.threads", "10").toInt
  def apiThreads: Int = propOrEnv("api.threads", "10").toInt
}
