# Monitoring

Sysiphos uses [kamon](https://kamon.io) to export monitoring data. Currently it supports
StatsD (for Graphite / Grafana) and Prometheus.

See the @ref[Configuration](configuration.md) for the corresponding keys to enable it.

Once stats are enabled Prometheus metrics will be exposed on port `9095`.

You can also download an [example dashboard for Grafana](assets/grafana-dashboard-example.json).

