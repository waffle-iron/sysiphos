## Introduction

In Sysiphos workflows are defined with by a JSON object:

```json
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

In our context we also call this the **Flow Definition**. A Flow Definition has a unique ID and a list of *root* tasks, that
describes the work to be done. Every task can have a list of child tasks that get executed after the parent task has
successfully finished its work.

This task structure lead to a tree (a [Directed Acyclic Graph](https://en.wikipedia.org/wiki/Directed_acyclic_graph), short DAG) 
of tasks that should be executed.

A flow definition can have one or more schedules assigned to it, that will trigger the execution repeatedly.

When Sysiphos decides to execute a flow definition (because of the schedule or because it was triggered by the API) 
it will create a **Flow Instance** with a reference to the ID of a definition. 

A flow instance has have a **Context** which is a dictionary of values that can be seen as additional
attributes that define the instance. This context is available for string interpolation in tasks that 
provide some kind of template.
 
Next it will also create **Flow Task Instance** for the first root task and try to execute it.
Task will per default be retried 3 times if a failure happens.

All of these entities allow a granular tracking and control of the execution. 
To handle the execution of multiple flow instances in parallel Sysiphos uses [Actors](https://en.wikipedia.org/wiki/Actor_model).

See @ref:[Execuction Model](execution-model.md) for a more detailed overview of the interaction of the actors with the 
introduces entities.

