## Rendering Service: Debugging

### Use "debug" Profile   

application-debug.properties are used for debugging purposes. To use them active teh debug profile;

In Run/Debug Configuration set active profiles to `debug`

`Run/Debug Configuration -> ServicesRenderingService2Application -> active Profiles`

Active Profiles: `debug`


### set public host
For edu-sharing backend registration you need to set public host
Do not override application.properties! Use programm arguments

`Run/Debug Configuration -> ServicesRenderingService2Application -> modify Options -> Programm Arguments`

Add: `--app.public.host=<your ip address>`



## Rendering-Service: Swagger 

- http://localhost:8080/swagger-ui/index.html#/
