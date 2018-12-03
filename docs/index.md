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

See the @ref:[Introduction](intro.md) to read about the core concepts.

@@@ index

* [Introduction](intro.md)
* [Execution Model](execution-model.md)
* [Concurrency](concurrency.md)
* [Run](run.md)
* [Monitoring](monitoring.md)
* [Tutorial](tutorial.md)
* [Tasks](tasks.md)
* [Configuration](configuration.md)
* [Build](build.md)
* [Screenshots](screenshots.md)
* [Acknowledgments](acknowledgments.md)
* [License](license.md)

@@@