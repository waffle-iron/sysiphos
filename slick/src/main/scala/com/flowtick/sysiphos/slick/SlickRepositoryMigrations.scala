package com.flowtick.sysiphos.slick

import java.io.StringWriter
import java.sql.{ Connection, DriverManager }

import com.flowtick.sysiphos.config.Configuration
import liquibase.database.DatabaseConnection
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.{ Contexts, LabelExpression, Liquibase }

import scala.util.Try

trait SlickRepositoryMigrations {
  def changeLogFile: String = Configuration.propOrEnv("db.changelog").getOrElse("db/db.changelog.xml")
  def databaseUrl: String = Configuration.propOrEnv("db.url").getOrElse("jdbc:h2:mem:sysiphos-default")
  def databaseUser: String = Configuration.propOrEnv("db.user").getOrElse("sa")
  def databasePassword: String = Configuration.propOrEnv("db.password").getOrElse("")
  def liquibaseContexts: String = Configuration.propOrEnv("db.liquibase.contexts").getOrElse("")

  def createLiquibase(databaseConnection: DatabaseConnection) = new Liquibase(changeLogFile, new ClassLoaderResourceAccessor(), databaseConnection)

  def createJdbcConnection: Connection = DriverManager.getConnection(databaseUrl, databaseUser, databasePassword)

  def createDatabaseConnection: DatabaseConnection = new JdbcConnection(createJdbcConnection)

  def updateDatabase: Try[Unit] =
    Try {
      val liquibase = createLiquibase(createDatabaseConnection)
      liquibase.update(new Contexts(liquibaseContexts))
    }

  def pendingChanges: Try[Seq[String]] = Try {
    import scala.collection.JavaConverters._

    createLiquibase(createDatabaseConnection).listUnrunChangeSets(new Contexts(liquibaseContexts), new LabelExpression()).asScala.map {
      change => s"Unrun changeset: ${change.getId}, ${change.getAuthor}, ${change.getDescription}, ${change.getComments}"
    }
  }

  def pendingSql: Try[String] = Try {
    val liquibase = createLiquibase(createDatabaseConnection)
    val writer = new StringWriter()
    liquibase.update(new Contexts(liquibaseContexts), writer)
    writer.toString
  }

  def unlock = Try {
    val liquibase = createLiquibase(createDatabaseConnection)
    liquibase.forceReleaseLocks()
  }
}

object DefaultSlickRepositoryMigrations extends SlickRepositoryMigrations

object DefaultSlickRepositoryMigrationsApp extends App {
  println(DefaultSlickRepositoryMigrations.updateDatabase)
}