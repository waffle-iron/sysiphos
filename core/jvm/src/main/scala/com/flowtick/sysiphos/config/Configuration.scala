package com.flowtick.sysiphos.config

trait Configuration {
  def propOrEnv(key: String): Option[String]
}

object Configuration {
  private var internalBackend: Option[Configuration] = None

  private def envKey(propertyKey: String): String = propertyKey.toUpperCase.replace('.', '_')

  def propOrEnv(key: String): Option[String] =
    Option(System.getProperties.getProperty(key))
      .orElse(Option(System.getenv(envKey(key))))
      .orElse(internalBackend.flatMap(_.propOrEnv(key)))

  def propOrEnv(key: String, defaultValue: String): String =
    Option(System.getProperties.getProperty(key))
      .orElse(Option(System.getenv(envKey(key))))
      .orElse(internalBackend.flatMap(_.propOrEnv(key)))
      .getOrElse(defaultValue)

  def setBackend(backend: Configuration): Unit = internalBackend = Some(backend)
}
