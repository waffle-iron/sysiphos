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
| logger.impl | file | The logger implementation, currently one of *(file, console)*, console will use stdout |
| logger.file.baseDir | /tmp (the value of `java.io.tmpdir`) | The base directory to use for logging |
| http.bind.address | 0.0.0.0 | Address to use to bind the http server |
| http.port | 8080 | Port to use to bind the http server |
| instance.threads | 10 | number of threads to use for database access during instance execution |
| api.threads | 10 | number of threads to use for api request handling |

