package com.flowtick.sysiphos.slick

import java.io.StringWriter
import javax.sql.DataSource

import com.flowtick.sysiphos.config.Configuration
import liquibase.database.DatabaseConnection
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.{ Contexts, LabelExpression, Liquibase }

import scala.util.Try

trait SlickRepositoryMigrations {
  def changeLogFile: String = Configuration.propOrEnv("db.changelog").getOrElse("db/db.changelog.xml")
  def liquibaseContexts: String = Configuration.propOrEnv("db.liquibase.contexts").getOrElse("")

  def createLiquibase(databaseConnection: DatabaseConnection) = new Liquibase(changeLogFile, new ClassLoaderResourceAccessor(), databaseConnection)

  def updateDatabase(dataSource: DataSource): Try[Unit] =
    Try {
      val liquibase = createLiquibase(new JdbcConnection(dataSource.getConnection))
      liquibase.update(new Contexts(liquibaseContexts))
    }

  def pendingChanges(dataSource: DataSource): Try[Seq[String]] = Try {
    import scala.collection.JavaConverters._

    createLiquibase(new JdbcConnection(dataSource.getConnection)).listUnrunChangeSets(new Contexts(liquibaseContexts), new LabelExpression()).asScala.map {
      change => s"Unrun changeset: ${change.getId}, ${change.getAuthor}, ${change.getDescription}, ${change.getComments}"
    }
  }

  def pendingSql(dataSource: DataSource): Try[String] = Try {
    val liquibase = createLiquibase(new JdbcConnection(dataSource.getConnection))
    val writer = new StringWriter()
    liquibase.update(new Contexts(liquibaseContexts), writer)
    writer.toString
  }

  def unlock(dataSource: DataSource) = Try {
    val liquibase = createLiquibase(new JdbcConnection(dataSource.getConnection))
    liquibase.forceReleaseLocks()
  }
}

object DefaultSlickRepositoryMigrations extends SlickRepositoryMigrations