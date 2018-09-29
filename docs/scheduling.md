# Scheduling

It is possible to create a schedule to run flow definitions automatically.

A schedule consists of a cron-like expression that defines the time intervals to wait between each run.

Sysiphos uses cron4s, which allows cron expressions that go from seconds to day of week in the following order:

    Seconds
    Minutes
    Hour Of Day
    Day Of Month
    Month
    Day Of Week

# Examples 

```
0 0 12 * * ? 	#Fire at 12pm (noon) every day
```

# Backfill

Per default a schedule will back fill its schedule, which means that it will take the last schedule date as starting 
point to determine the next schedule date.

# Creating a schedule

Schedules can be created in the API using the `createFlowSchedule` mutation, 
or in the corresponding UI section in the flow overview.