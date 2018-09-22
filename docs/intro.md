## Introduction

In Sysiphos workflows are defined with by a JSON object:

```json
{
  "id" : "new-flow",
  "task" : {
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
  }
}
```

In our context we also call this the *Flow Definition*. A Flow Definition has a unique ID and a *root* task, that
describes the work to be done. Every task can have a list of child tasks that get executed after the parent task has
successfully finished its work.

This task structure lead to a tree (a [Directed Acyclic Graph](https://en.wikipedia.org/wiki/Directed_acyclic_graph), short DAG) 
of tasks that should be executed.

When Sysiphos decides to run this workflow (because of the schedule or because it was triggered by the API) 
it will create a *Flow Instance* with a reference to the ID of a definition. 
Next it will also create *Flow Task Instance* for the root task. 

All of these entities allow a granular tracking and control of the execution. 
To handle the execution of multiple flow instances in parallel Sysiphos uses actors.

There is a singleton actor to control the time based aspects of the scheduling which also acts as supervisor for 
the *Flow Instance* actors. These instance actors will spawn *Flow Task Instance* actors matching the parent-child 
relation of the tasks in the definition.

![actor-entity-relation](assets/hierarchy_table_relation.png)

