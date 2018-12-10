# Tasks

The following sections will give you an overview on the available task types.

## Shell Task 

@@snip [shell-flow.json](examples/src/main/resources/shell-flow.json)

Will execute the command and be considered successfully on exit code `0`. Be aware that the shell is optional,
if not specified the JVM process builder will be used to execute the command. If shell specific features 
like output piping etc. are needed, please set it accordingly.

The command property supports [string interpolation](#string-interpolation).

## Trigger Task 

@@snip [trigger-flow.json](examples/src/main/resources/trigger-flow.json)

Will trigger the execution of the given flow definition id. The context values of the current flow will be copied over
to the new instance.

Note that the triggering instance will not wait for the completion of the triggered instance,  
once the instance is successfully created, the trigger task is considered done.

# String Interpolation

Its possible to use [freemarker](https://freemarker.apache.org) expression to 
access the context of the instance or system properties on some tasks that support it.

For example, the execution of `"command" : "echo Hello ${name}"` 
in an instance that has the context value `name` with value `World` 
will print `Hello World` to the log.
 