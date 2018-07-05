package com.flowtick.sysiphos.api.resources

trait TwitterBootstrapResources extends StaticResourceSupport {
  val bootstrapResources = getResource("bootstrap.min.js", "application/javascript") :+:
    getResource("respond.min.js", "application/javascript") :+:
    getResource("html5shiv.min.js", "application/javascript") :+:
    getResource("jquery.min.js", "application/javascript") :+:
    getResource("font.css", "text/css") :+:
    getResource("bootstrap.min.css", "text/css") :+:
    getResource("bootswatch.lumen.min.css", "text/css") :+:
    getResource("fontawesome-all.css", "text/css") :+:
    getResource("toastr.min.css", "text/css") :+:
    getResource("toastr.min.js", "text/css")
}
