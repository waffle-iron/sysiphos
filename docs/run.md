# Run

### Docker
A [docker image](https://hub.docker.com/r/flowtick/sysiphos) is provided, so you can just run it.

```bash
docker run -p 9090:8080 flowtick/sysiphos:latest
```

Will run Sysiphos with an in-memory database, that will accessible in the browser at `http://localhost:9090`.

See also the `docker-compose` example, which is using MySQL:

@@snip [docker-compose-dev.yml](../docker-compose-dev.yml)

### From Source

The server can also be run directly in the source directory via `sbt`

```bash
sbt serverJVM/run -Dprop=value
```

In the example a system property with key `prop` is passed in.
