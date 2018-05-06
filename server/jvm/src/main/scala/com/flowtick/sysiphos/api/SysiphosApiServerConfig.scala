package com.flowtick.sysiphos.api

import slick.jdbc.{ DriverDataSource, H2Profile, MySQLProfile }

trait SysiphosApiServerConfig {
  def propOrEnv(key: String): Option[String] =
    Option(System.getProperties.getProperty(key))
      .orElse(Option(System.getenv(key)))

  def propOrEnv(key: String, defaultValue: String): String =
    Option(System.getProperties.getProperty(key))
      .orElse(Option(System.getenv(key)))
      .getOrElse(defaultValue)

  def bindAddress: String = propOrEnv("http.bind.address").getOrElse("0.0.0.0")
  def httpPort: Int = propOrEnv("PORT0").orElse(propOrEnv("http.port")).getOrElse("8080").toInt
  def repoBaseDir: String = propOrEnv("repo.base.dir", defaultValue = ".sysiphos")
  def flowDefinitionsRemoteUrl: Option[String] = propOrEnv("flow.definitions.remote.url")
  def flowSchedulesRemoteUrl: Option[String] = propOrEnv("flow.schedules.remote.url")

  def dbProfileName: String = propOrEnv("database.profile", "h2")

  def dataSource = new DriverDataSource(
    propOrEnv("database.url", "jdbc:h2:sysiphos.db"),
    propOrEnv("database.user", "sa"),
    propOrEnv("database.password", ""))

  def dbProfile = dbProfileName match {
    case "mysql" => MySQLProfile
    case "h2" => H2Profile
    case _ => throw new RuntimeException(s"unsupported database profile $dbProfileName")
  }

  def instanceThreads: Int = propOrEnv("instance.threads", "10").toInt
  def apiThreads: Int = propOrEnv("api.threads", "10").toInt
}
