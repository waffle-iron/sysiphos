package com.flowtick.sysiphos.api.resources

trait GraphIQLResources extends StaticResourceSupport {
  val graphiqlResources =
    getResource("es6-promise.auto.min.js", "application/javascript") :+:
      getResource("fetch.min.js", "application/javascript") :+:
      getResource("react.min.js", "application/javascript") :+:
      getResource("react-dom.min.js", "application/javascript") :+:
      getResource("graphiql.min.js", "application/javascript") :+:
      getResource("graphiql.min.css", "text/css") :+:
      getResource("graphiql", "text/html", Some("graphiql.html"))
}
