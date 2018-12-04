package com.flowtick.sysiphos.execution

import com.flowtick.sysiphos.config.Configuration
import com.timgroup.statsd.NonBlockingStatsDClient
import com.typesafe.config.Config
import kamon.metric.PeriodSnapshot
import kamon.statsd.{ MetricKeyGenerator, SimpleMetricKeyGenerator }
import kamon.{ Kamon, MetricReporter }

import scala.util.{ Failure, Try }

object Monitoring {
  class DataDogStatsDReporter extends MetricReporter with Logging {
    var client: Option[(NonBlockingStatsDClient, MetricKeyGenerator)] = None

    override def start(): Unit = {
      reconfigure(Kamon.config())
    }

    override def stop(): Unit = {
      client.foreach(_._1.stop())
    }

    override def reconfigure(config: Config): Unit = {
      client = createClient(config)
    }

    def createClient(baseConfig: Config): Option[(NonBlockingStatsDClient, MetricKeyGenerator)] = Try {
      for {
        statsHost <- Configuration.propOrEnv("stats.host")
          .orElse(Some(baseConfig.getString("stats.host")))

        statsPort: Int <- Configuration.propOrEnv("stats.port")
          .map(_.toInt)
          .orElse(Try(baseConfig.getInt("stats.port")).toOption)
          .orElse(Some(8125))
      } yield {
        log.info(s"using stats host: $statsHost")
        log.info(s"using stats port: $statsPort")

        (new NonBlockingStatsDClient("", statsHost, statsPort), new SimpleMetricKeyGenerator(baseConfig))
      }
    }.recoverWith {
      case error =>
        log.error("unable to create stats client", error)
        Failure(error)
    }.getOrElse(None)

    override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = client match {
      case Some((statsClient, keyGenerator)) =>
        for (counter <- snapshot.metrics.counters) {
          statsClient.count(keyGenerator.generateKey(counter.name, counter.tags), counter.value)
        }

        for (gauge <- snapshot.metrics.gauges) {
          statsClient.gauge(keyGenerator.generateKey(gauge.name, gauge.tags), gauge.value)
        }

        for (
          metric <- snapshot.metrics.histograms ++ snapshot.metrics.rangeSamplers;
          bucket <- metric.distribution.bucketsIterator
        ) {
          statsClient.histogram(keyGenerator.generateKey(metric.name, metric.tags), bucket.value, bucket.frequency)
        }

      case _ => ()
    }
  }
}
