package com.flowtick.sysiphos.config

object Configuration {
  def envKey(propertyKey: String): String = propertyKey.toUpperCase.replace('.', '_')

  def propOrEnv(key: String): Option[String] =
    Option(System.getProperties.getProperty(key))
      .orElse(Option(System.getenv(envKey(key))))

  def propOrEnv(key: String, defaultValue: String): String =
    Option(System.getProperties.getProperty(key))
      .orElse(Option(System.getenv(envKey(key))))
      .getOrElse(defaultValue)
}
