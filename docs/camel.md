# Camel Tasks

Sysiphos allows to use [Apache Camel](https://github.com/apache/camel/blob/master/components/readme.adoc) 
components via the usage of Camel URIs. This enables rich integration flows inside a flow definition.

The context values are available for @ref[string interpolation](tasks.md#string-interpolation) when using endpoints that can take a body.

Following are some common examples of using the Camel Task. 

## HTTP

@@snip [camel-http-flow.json](examples/src/main/resources/camel-http-flow.json)

In this example are two HTTP tasks defined. The first one will do a `GET`-request due to the missing
`bodyTemplate` and will add an `Accept` header to the request. 

The second will replace the `${someContextKey}` expression with the corresponding context value and
do a `POST` including the `Content-Type` header.

Its also possible to define the HTTP method and other options like timeouts etc via uri or header, 
see the [http4 component reference](https://github.com/apache/camel/blob/master/components/camel-http4/src/main/docs/http4-component.adoc).

Note the `extract`-definition on this task. Its possible to extract a value from the response body via a 
[JsonPath](https://goessner.net/articles/JsonPath) expression. So assuming that the response will be:

```json
{
 "data": {
    "value": 42
 }
}
```

There will be a new or updated context value with the name `result` and the value `42` in the flow instance.
It is possible to add more than one extraction. On failures in the extraction the task will we considered failed 
(potentially going into retry). 

## Slack

@@snip [slack-flow.json](examples/src/main/resources/camel-slack-flow.json)

This task will send the message `Hello World!` to the slack channel `a-channel` 
given a flow instance with a context value with the name `subject` and value `World`.

To be able to use a slack task you need a webhook url that you can create in the configuration
of your corresponding slack app.

## SQL

@@snip [slack-flow.json](examples/src/main/resources/camel-sql-flow.json)

This task will execute the SQL query in the URI using the datasource specified in the `registry` with name `ds`.
In this case it will return a single row with a column `RESULT` and the value `2`. In Camel result sets
will be transformed to collections, so a single result will be a `Map` and a bigger result set will become
a `List` of `Map`s. Its possible to extract values from the result using a [simple](https://github.com/apache/camel/blob/master/camel-core/src/main/docs/simple-language.adoc) 
expression that can access values of the Camel exchange.

In the example the expression would extract the value `2` from the result map and store it as context value
with the name `result`.

## Java

@@snip [slack-flow.json](examples/src/main/resources/camel-bean-flow.json)

This task will create a new instance of type `com.flowtick.sysiphos.example.MyClass` using the default constructor.
The bean is referenced by its registry name `myBean` to invoke the method `doStuff`:

@@snip [MyClass.java](examples/src/test/java/com/flowtick/sysiphos/example/MyClass.java) 

## Other Camel task properties

- `convertStreamToString`: by default Sysiphos tries to convert Camel exchange bodies of type `java.io.InputStream` 
to the type String to allow multiple extractions. This can be disabled by setting to value `false`.
- `exchangeType`: can be one of `procuder`, `consumer`. This is useful to set for endpoints that allow 
both consuming and producing.

# Adding more components or own Java classes

To add more Camel components or you own Java classes, you currently need to copy the (component) jar and its dependencies 
to the `lib` (`/opt/docker/lib` in the docker image) folder of the Sysiphos package.

You can also add a component by extending the library dependencies of the `build.sbt` in the Sysiphos source folder
and creating a custom image by running `sbt docker:publishLocal`.

A more convenient way of providing jars might be added in the future.
