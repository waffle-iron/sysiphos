package com.flowtick.sysiphos.api

import java.io.File

trait SysiphosApiServerConfig {
  def propOrEnv(key: String, defaultValue: Option[String] = None): Option[String] =
    Option(System.getProperties.getProperty(key))
      .orElse(Option(System.getenv(key)))
      .orElse(defaultValue)

  def repoBaseDir = new File(".sysiphos")
  def flowDefinitionsRemoteUrl: Option[String] = propOrEnv("flow.definitions.remote.url")
  def flowSchedulesRemoteUrl: Option[String] = propOrEnv("flow.schedules.remote.url")
}
