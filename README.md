[![Waffle.io - Columns and their card count](https://badge.waffle.io/flowtick/sysiphos.png?columns=all)](https://waffle.io/flowtick/sysiphos?utm_source=badge)
[![travis ci](https://api.travis-ci.org/flowtick/sysiphos.svg?branch=master)](https://travis-ci.org/flowtick/sysiphos)

# Sysiphos

A graph-based task scheduler. It allows to execute JSON-defined workflows in with `cron`-like schedules,
while providing an API and UI for easy operations.

### Example

#### Workflow

```javascript
{
  "id" : "new-flow",
  "tasks" : [{
    "id" : "new-task",
    "type" : "shell",
    "command" : "curl https://api.chucknorris.io/jokes/random?category=${category!\"dev\"}",
    "children" : [
      {
        "id" : "new-task",
        "type" : "shell",
        "command" : "echo I am child task, I get triggered when my parent is done. "
      }
    ]
  }]
}
``` 

Can be scheduled or directly triggered via 

```bash
curl 'http://sysiphos-server:8080/api' -H 'content-type: application/json' --data '{"query":"mutation { createInstance(flowDefinitionId: \"new-flow\", context: [ {key: \"category\", value: \"movie\"} ]) { id, status } }","variables":null}'
...
{"data":{"createInstance":{"id":"848d6293-b5e9-48b8-b6a8-5d0e552033c5","status":"Triggered"}}}% 
```

See the documentation for details.

# Documentation

please check the [site](https://flowtick.github.io/sysiphos) or use the [the markdown docs](docs) directly.

# License

Apache License Version 2.0, see [LICENSE](LICENSE)






