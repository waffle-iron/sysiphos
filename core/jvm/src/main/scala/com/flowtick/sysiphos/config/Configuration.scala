package com.flowtick.sysiphos.config

object Configuration {
  def propOrEnv(key: String): Option[String] =
    Option(System.getProperties.getProperty(key))
      .orElse(Option(System.getenv(key)))

  def propOrEnv(key: String, defaultValue: String): String =
    Option(System.getProperties.getProperty(key))
      .orElse(Option(System.getenv(key)))
      .getOrElse(defaultValue)
}
