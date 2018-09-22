## Build

Sysiphos uses [sbt](https://www.scala-sbt.org) as build tool. It is assumed that you have installed.

The core libs should also work for older Scala versions, so remember to verify / run everything with the 
cross build prefix:

```bash
sbt +compile
sbt +test
```

## Packaging

Sysiphos uses [sbt-native-packager](https://github.com/sbt/sbt-native-packager), 
this allows to create multiple formats, see the documentation for details.

Examples:

```bash
# creates a deb file
sbt debian:packageBin 

# create a zip package with start scripts
sbt universal:packageBin 

# create a tar.gz package with start scripts
sbt universal:packageZipTarball 

# create a local docker image
sbt docker:publishLocal 
```

Build packages can be found in `server/jvm/target[/universal]`

## Publishing

First build the docker image:

```bash
sbt docker:publishLocal
```

This will create a `latest` tag and one for the current build version.

Then login and push it:

```bash
docker login 
# enter your credentials
docker push flowtick/sysiphos:latest
```

## Documentation

Generate the site:

```bash
sbt makeSite
```

and check the result in `target/site`.

If it looks good and you have the permissions, you can push it to the GitHub Pages via:

```bash
sbt ghpagesPushSite
```