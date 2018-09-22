# Tasks

The following sections will give you an overview on the available task types.

### Shell Task 
#### Example

```json
{
    "id" : "task-id",
    "type" : "shell",
    "command" : "some command",
    "children" : []
}
```

Will execute the command and be considered successfully on exit code `0`.

#### String Interpolation

Its possible to use [freemarker](https://freemarker.apache.org) expression to 
access the context of the instance or system properties.

For example, the execution of `"command" : "echo Hello ${name}"` 
in an instance that has the context value `name` set to `World` 
will print `Hello World` to the log.

### Trigger Task 
#### Example

```json
{
    "id" : "task-id",
    "type" : "trigger",
    "flowDefinitionId" : "some-flow-id",
    "children" : []
}
```

Will trigger the execution of the given flow definition id. The context values of the current flow will be copied over
to the new instance.

