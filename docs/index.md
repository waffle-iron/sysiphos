Sysiphos
========

`Sysiphos` is a graph-based task scheduler.

# Why?

After looking at projects like 
[Airflow](https://airflow.incubator.apache.org),
[Chronos](https://mesos.github.io/chronos)
or [Oozie](https://oozie.apache.org/)
there seems to be a place for a solution that is 

* providing a rich language agnostic scheduler DSL
* independent of a (technical) domain or focus area like Hadoop
* not reinventing the wheel
* usable with minimal dependencies
* JVM-based
* developed with an _API first_ UI approach

Sysiphos wants to be that solution.

That being that, Sysiphos is heavily influenced by the usage of the above mentioned tools and 
tries to pick the best features of each. Its currently not a feature-rich as any of those solutions and 
provides basic scheduling.

# Comparison

|     | Sysiphos    | Airflow | Oozie  |
| --- |:-----------:|:-------:|-------:| 
| Definition Format | Json | Python | XML |
| Definition Model | DAG | DAG | Workflow Graph |
| Target Group | JVM-minded, admins | Python-minded, admins | Hadoop Users |
| Dependencies | RDBMS (H2, MySQL, Postgres) | RDBMS (MySQL, Postgres), Celery (Redis) | Hadoop (HUE, HDFS) | 
| Monitoring Support | StatsD, Prometheus | StatsD | ? |
| Packaging | tar.gz, docker image | pip, community docker image | Part of the Hadoop distribution | 
| Integration Support | 100+ components via Camel | many community contributed operators, sensors etc. | none |
| Workflow Pattern Support | basic (sequence, trigger) | advanced (sequence, branching, trigger, sub-dag) |  basic (sequence, branching) |
| Data Sharing Concept | local, dynamic (instance context values, extractions) | global, static (XComs, Variables) | none |
| Execution Model | actors, streams (internal queues) | threads, external queues | intertwined with the Yarn application model |
| Scale | high volume, streaming (tasks / seconds) | low volume, batching (tasks / minute / hour) | low volume, batching |
| High Availability | Akka Clustering (WIP) | via Celery worker scaling | via Hadoop | 
| API | self documenting GraphQL HTTP API | experimental HTTP API (only execute instance, only one per DAG / second)| SOAP| 
| UI | single page application (API-powered) | form-based | HUE, ExtJS Application| 

See the @ref:[Introduction](intro.md) to read about the core concepts.

@@@ index

* [Introduction](intro.md)
* [Execution Model](execution-model.md)
* [Concurrency](concurrency.md)
* [Run](run.md)
* [Monitoring](monitoring.md)
* [Tutorial](tutorial.md)
* [Tasks](tasks.md)
* [Camel](camel.md)
* [Configuration](configuration.md)
* [Build](build.md)
* [Screenshots](screenshots.md)
* [Acknowledgments](acknowledgments.md)
* [License](license.md)

@@@