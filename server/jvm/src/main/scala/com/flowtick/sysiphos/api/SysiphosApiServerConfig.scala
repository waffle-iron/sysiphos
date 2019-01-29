package com.flowtick.sysiphos.api

import com.flowtick.sysiphos.config.Configuration
import slick.jdbc._
import com.flowtick.sysiphos.config.Configuration._
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import com.typesafe.config.{ Config, ConfigFactory }

import scala.util.Try

trait SysiphosApiServerConfig {
  Configuration.setBackend(new TypesafeConfigBackend)

  def bindAddress: String = propOrEnv("http.bind.address").getOrElse("0.0.0.0")
  def httpPort: Int = propOrEnv("PORT0").orElse(propOrEnv("http.port")).getOrElse("8080").toInt
  def repoBaseDir: String = propOrEnv("repo.base.dir", defaultValue = ".sysiphos")
  def flowDefinitionsRemoteUrl: Option[String] = propOrEnv("flow.definitions.remote.url")
  def flowSchedulesRemoteUrl: Option[String] = propOrEnv("flow.schedules.remote.url")

  def dbProfileName: String = propOrEnv("database.profile", "h2")
  def dbUrl: String = propOrEnv("database.url", "jdbc:h2:mem:sysiphos;DB_CLOSE_DELAY=-1")
  def clusterName: String = propOrEnv("clustering.cluster.name", "sysiphos-cluster")

  def dataSource(jdbcProfile: JdbcProfile): DataSource = {
    val ds = new HikariDataSource()
    ds.setJdbcUrl(dbUrl)
    ds.setUsername(propOrEnv("database.user", "sa"))
    ds.setPassword(propOrEnv("database.password", ""))
    ds.setDriverClassName(jdbcProfile match {
      case MySQLProfile => classOf[com.mysql.jdbc.Driver].getName
      case H2Profile => classOf[org.h2.Driver].getName
      case PostgresProfile => classOf[org.postgresql.Driver].getName
      case _ => throw new RuntimeException(s"unknown driver for $dbProfileName")
    })
    ds
  }

  def dbProfile = dbProfileName match {
    case "mysql" => MySQLProfile
    case "h2" => H2Profile
    case "postgres" => PostgresProfile
    case _ => throw new RuntimeException(s"unsupported database profile $dbProfileName")
  }

  def instanceThreads: Int = propOrEnv("instance.threads", "10").toInt
  def apiThreads: Int = propOrEnv("api.threads", "10").toInt
}

class TypesafeConfigBackend extends Configuration {
  private val config: Config = ConfigFactory.load()

  override def propOrEnv(key: String): Option[String] = Try(config.getString(key)).toOption
}
