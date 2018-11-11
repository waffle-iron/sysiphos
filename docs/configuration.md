## Configuration

Sysiphos allows configuration via Java system properties or environment variables.

The corresponding environment variable for a property is created by making it upper case 
with all `.` characters replaced by `_`.

So `a.property.key` becomes `A_PROPERTY_KEY`. 

Sysiphos will first look for the property value, then for the corresponding 
environment variable and then fallback to default if possible.

| Property      | Default                                  | Description  |
| ------------- |:----------------------------------------:| ------------:|
| database.url  | jdbc:h2:mem:sysiphos;DB_CLOSE_DELAY=-1   | The JDBC URL to use |
| database.user | sa | The JDBC user to use |
| database.password | empty string | The JDBC password to use |
| database.profile | h2 | The database type, currently one of *(h2, mysql)* |
| logger.impl | file | The logger backend implementation, currently one of *(file-direct, file-stream, s3, console)*, console will use stdout |
| logger.stream.chunkSize | 100 | The number of lines to aggregate (chunking) before writing to the log backend, only applies to stream backends like `s3` or  `file-stream` |
| logger.stream.queueSize | 1000 | The size of the queue used for interacting with the process output |
| logger.file.baseDir | /tmp (the value of `java.io.tmpdir`) | The base directory to use for logging for the file based backends |
| logger.s3.accessKey | changeme | The access key to use for logging to S3 (default credentials chain is checked first) |
| logger.s3.secretKey | changeme | The secret key to use for logging to S3 (default credentials chain is checked first) |
| logger.s3.bucket | changeme | The bucket to use for S3 logging, this can be seen as the root folder for logging |
| http.bind.address | 0.0.0.0 | Address to use to bind the http server |
| http.port | 8080 | Port to use to bind the http server |
| instance.threads | 10 | number of threads to use for database access during instance execution |
| api.threads | 10 | number of threads to use for api request handling |
| task.retries.default | 3 | default number of retries for a task, if not specified otherwise |
| task.retry.delay.default | 18000 | default number of seconds to wait until next try, if not specified otherwise |
