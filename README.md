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


# Maven Git Workflow

Maven artifacts, Docker images, Helm charts, etc., are versioned based on the Git branch name or tag.  
From `maven/fixes/10.0`, the artifact is built with the version `<artifactid>:maven-fixes-10.0-SNAPSHOT`.

## Feature Branch Workflow

Feature branches are treated specially.  
Feature branches following the pattern `maven/feature/<version>-My-Fancy-Feature` also produce Maven artifacts with the version  
`<artifactid>:maven-fixes-<version>-SNAPSHOT`.  
For example, from `maven/feature/10.0-My-Fancy-Feature`, the artifact will be built with the version  
`<artifactid>:maven-fixes-10.0-SNAPSHOT`.

This allows feature branches to be created without requiring all projects to use the same branch name.

