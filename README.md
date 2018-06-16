[![Waffle.io - Columns and their card count](https://badge.waffle.io/flowtick/sysiphos.png?columns=all)](https://waffle.io/flowtick/sysiphos?utm_source=badge)
[![travis ci](https://api.travis-ci.org/flowtick/sysiphos.svg?branch=master)](https://travis-ci.org/flowtick/sysiphos)

# Sysiphos

A graph-based task scheduler.

# Why?

After looking at projects like 
[Conductor](https://netflix.github.io/conductor), 
[Airflow](https://airflow.incubator.apache.org),
[Chronos](https://mesos.github.io/chronos)
or even [Zeebe](https://zeebe.io)
there seems to be a place for a solution that is 

* providing a rich language agnostic scheduler DSL
* independent of a (technical) domain
* not reinventing the wheel
* usable with minimal dependencies
* JVM-based
* developed with an _API first_ UI approach

Sysiphos wants to be that solution.

# Build

    sbt package

# License

Apache License Version 2.0, see [LICENSE](LICENSE)






