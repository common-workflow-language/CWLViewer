# CWL Viewer

This is a [Spring Boot](http://projects.spring.io/spring-boot/) MVC application which fetches [Common Workflow Language](http://www.commonwl.org/) files from a Github repository and creates a page for it detailing the main workflow and its inputs, outputs and steps.

[![Build Status](https://github.com/common-workflow-language/cwlviewer/workflows/CWL%20Viewer%20Build/badge.svg?branch=main)](https://github.com/common-workflow-language/cwlviewer/actions?query=workflow%3A%22CWL%20Viewer%20Build%22) [![Coverage Status](https://coveralls.io/repos/github/common-workflow-language/cwlviewer/badge.svg)](https://coveralls.io/github/common-workflow-language/cwlviewer) [![Gitter](https://img.shields.io/gitter/room/gitterHQ/gitter.svg)](https://gitter.im/common-workflow-language/cwlviewer?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![](https://images.microbadger.com/badges/image/commonworkflowlanguage/cwlviewer.svg)](https://microbadger.com/images/commonworkflowlanguage/cwlviewer "MicroBadger commonworkflowlanguage/cwlviewer") [![Docker image commonworkflowlanguage/cwlviewer](https://images.microbadger.com/badges/version/commonworkflowlanguage/cwlviewer.svg)](https://hub.docker.com/r/commonworkflowlanguage/cwlviewer/ "Docker Hub commonworkflowlanguage/cwlviewer")
[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.823534.svg)](https://doi.org/10.5281/zenodo.823534)



## License

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).
See the file [LICENSE.md](LICENSE.md) for details, and
[NOTICE.md](NOTICE.md) for required attribution notices.

## Contribute

Feel free to contribute! You may [raise an issue](https://github.com/common-workflow-language/cwlviewer/issues),
provide a [pull request](https://github.com/common-workflow-language/cwlviewer/pulls)
or join the [gitter chat for cwlviewer](https://gitter.im/common-workflow-language/cwlviewer)!


# Using CWL Viewer

You are recommended to use the **production instance** of CWL Viewer at https://view.commonwl.org/ which runs the latest [release](https://github.com/common-workflow-language/cwlviewer/releases). Any downtime should be reported on the [gitter chat for cwlviewer](https://gitter.im/common-workflow-language/cwlviewer).

<!--- I don't think this is a thing any more.
The **dev instance** at http://view.commonwl.org:8082/ corresponds to the current `master` branch, and is updated every 6 minutes to run the latest [commonworkflowlanguage/cwlviewer docker image](https://hub.docker.com/r/commonworkflowlanguage/cwlviewer/builds/). Note that this instance is NOT secured and might break at any time.
-->

# Running

If you are a developer, or you want to use the CWL Viewer in a closed environment, then you can run your own instance.

## Recommended - Running with Docker

This application can be started with [Docker](https://www.docker.com/) and [Docker Compose](https://docs.docker.com/compose/install/) and Docker Compose is the recommended method of running or developing this codebase.

Then run the following commands to clone the project in your local system.

   ```
    git clone https://github.com/common-workflow-language/cwlviewer.git
    cd cwlviewer
  ```

In the project directory, to start CWLViewer exposed on port `8080`, run:

    docker-compose up

The web server will connect to a local host, you'll see the message saying "Tomacat started on port(s):8080".

To see the locally running CWL Viewer app, visit http://localhost:8080/ in your web browser.

To stop and remove:

    docker-compose down


If you change the source code, then use this `docker-compose.override.yml` and
re-build with `docker-compose build`:

```yaml
version: '3.2'
services:
  spring:
    build: .
```

See the [docker-compose.yml](docker-compose.yml) file for details.

If you have modified the source code, then you may want to build the docker image locally first:

    docker build -t commonworkflowlanguage/cwlviewer .

## Running Spring Boot locally for development, with MongoDB and Jena Fuseki in Docker

Create `docker-compose.override.yml`:

```
version: '3.2'
services:
  mongo:
    ports:
     - "27017:27017"
  sparql:
    ports:
     - "3030:3030"
```

Then start the containers:

```
docker-compose up
```

Then start Spring Boot locally:

```
mvn spring-boot:run -Dserver.port=7999
```

Now you can connect to http://localhost:7999 in your browser.

## Deleting the data volumes to reset state

To completely reset the state, you must delete the data volumes:

```
docker-compose down
docker volume rm  cwlviewer_bundle cwlviewer_git cwlviewer_graphviz cwlviewer_mongo cwlviewer_sparql
```

## Running without Docker

### Requirements

#### MongoDB

You will need to have [MongoDB](https://www.mongodb.com/) running,
by default on `localhost:27017`

If you are running from the command line, you can override this by supplying
system properties like `-Dspring.data.mongodb.host=mongo.example.org` and
`-Dspring.data.mongodb.port=1337`

#### Apache Jena Fuseki (or alternative SPARQL server)

You will also need to have a SPARQL server such as [Apache Jena Fuseki](https://jena.apache.org/documentation/fuseki2/) running,
by default on `localhost:3030`

## Compiling and Running

To compile you will need [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html) or OpenJDK 8 (`apt install openjdk-8-jdk`),
as well as [Apache Maven 3](https://maven.apache.org/download.cgi) (`apt install maven`).

Spring Boot uses an embedded HTTP server. The Spring Boot Maven plugin includes a run goal which can be used to quickly compile and run it:

```
$ mvn spring-boot:run
```


Alternatively, you can run the application from your IDE as a simple Java application by importing the Maven project.

You need to install [Graphviz](http://www.graphviz.org/) for all unit tests to pass.

You can create an executable JAR file by using:

    mvn clean install

Afterwards, run:

    java -jar target/cwlviewer*.jar

(The exact filename will vary per version)

Once CWL Viewer is running, you should see log output somewhat like:

```
()..)
s.b.c.e.t.TomcatEmbeddedServletContainer : Tomcat started on port(s): 8080 (http)
org.researchobject.CwlViewerApplication  : Started CwlViewerApplication in 28.604 seconds
```

Now check out http://localhost:8080/ to access CWL Viewer.

## Configuration

There are a variety of configuration options detailed in the [application configuration file](https://github.com/common-workflow-language/cwlviewer/blob/master/src/main/resources/application.properties) which can be adjusted.

When deploying with docker, these can be overriden externally by creating/modifying `docker-compose.override.yml` as follows:

```yaml
version: '3.2'
services:
  spring:
    environment:
            applicationName: Common Workflow Language Viewer
            applicationURL: https://view.commonwl.org
            cacheDays: 1
```

The properties can alternatively be provided as system properties on the
command line, e.g. `-DcacheDays=1` or via a [variety of other methods supported by Spring Boot](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html)

# Dump/restore

While you can perform backup of the Docker volumes,
for larger upgrades of CWL Viewer it is recommended instead to do a JSON dump
and re-load, which will force CWL Viewer to fetch and parse again.

The script `dump.sh` can be used for regular backups, it will store the full
output of /workflows as a timestamped gzip-compressed JSON file:

    $ ./dump.sh https://view.commonwl.org/ /var/backups/cwl
    /var/backups/cwl/2018-06-06T135133+0000.json.gz

The script `load.py` (requires Python 3) can be used to restore from such JSON dumps:

    ./load.py /var/backups/cwl/2018-06-06T135133+0000.json.gz https://view.commonwl.org/

The optional parameter `--no-commits` can be added to skip those entries that
look like a commit ID. Note that this might break previous permalinks.

# CONNECTING JAVA DEBUGGER WITH DOCKER COMPOSE USING INTELLIJ IDE

Connecting to a java debugger can be broken down into the following steps:
### 1. Run the application using docker-compose
Create a Dockerfile_debug at the root of the directory with the same content as the Dockerfile of the application

Create a docker-compose-debug.yml file with the same content as the docker-compose.yml file at the root of the directory replacing this in the docker-compose-debug.yml
```yaml
    image: commonworkflowlanguage/cwlviewer:v1.3.0
```

with this:

```yaml
build:
  context: .
  dockerfile: Dockerfile_debug
```
This is because we want docker to build with our source code on our local instead of the image on docker hub.

In the port section we want to specify a different port to listen to debug connections.

Here, we are using address as 5005.

In the command section as well, we configure JVM to allow debugging and We enable the agent Java Debug Wire Protocol (JDWP) inside the JVM.

At the end you should have something looking like this

```yaml
build:
 context: .
 dockerfile: Dockerfile_debug
ports:
 - "8080:8080"
 - "5005:5005"
command: java -Djava.security.egd=file:/dev/./urandom -jar /usr/lib/cwlviewer.jar
```
If on intellij, open the docker-compose-debug.yml and click on the ![img_2.png](img_2.png)  in the gutter.

You should see this message:

    “Compose: docker-compose-debug.yml has been deployed successfully”

you can access the application on localhost port 8080(which is the default port here) and the application should start up as expected.

You can make HTTP requests on it and expect it to work

### 2. Create a remote debug config
In the docker-compose-debug.yml, click on the “insect looking image”, ![img_1.png](img_1.png)  on the side gutters

Select the module in the Use module classpath list, in this case “cwlviewer”

Double-click the Docker Compose run configuration in the Before launch list.

If it is not in the list, click + and select Launch Docker before debug

Make sure that the Docker Compose run configuration is selected with the app service. 

Also check the Custom Command field: it should contain the -agentlib option and options from the command field in the docker-compose-debug.yml file:

    java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 -Djava.security.egd=file:/dev/./urandom -jar /usr/lib/cwlviewer.jar


#### NB
If the default port is allocated to something else, change the container port for the Java debugger to connect to and make sure that the port in the remote debug configuration is the same.

If the application is already running, do not run the debug configuration. Apply the settings and click Cancel.

### 3. Launch the debug configuration﻿
If the application is already running, stop it. 

This can be done by selecting the Docker Compose node in the Services tool window below and click  ![img.png](img.png)in the toolbar.

You can also right-click and delete the containers under the corresponding services.

Open the docker-compose-debug.yml file.

Click ![img_3.png](img_3.png) Debug with Remote in the gutter and start the debug configuration.

Once the application starts and the debugger attaches to it, the Debug tool window will open.

 ### 4. Setting breakpoints and Debugging the application

Open the file to be debugged and set breakpoints at different points in the method

Execute the http request

The application will stop at the breakpoint and you can examine the current variable values and frames in the Debug tool window.

Click ![img_4.png](img_4.png) until it executes the request and you get the returned values.

You can also step into, step over or step out of methods depending on your preference.





















# Documentation

2017 Poster https://doi.org/10.7490/f1000research.1114375.1

2017 Video overview https://youtu.be/_yjhVTmvxLU

2017 Technical Report https://doi.org/10.5281/zenodo.823295

# Thanks

Developers and [contributors](https://github.com/common-workflow-language/cwlviewer/graphs/contributors) include:

* **Mark Robinson** http://orcid.org/0000-0002-8184-7507
* Stian Soiland-Reyes http://orcid.org/0000-0001-9842-9718
* Michael Crusoe http://orcid.org/0000-0002-2961-9670
* Carole Goble http://orcid.org/0000-0003-1219-2137
* Charles Overbeck https://github.com/coverbeck
* Finn Bacall http://orcid.org/0000-0002-0048-3300

Thanks to:

* [eScience Lab](http://www.esciencelab.org.uk/) at [The University of Manchester](http://www.cs.manchester.ac.uk/)
* [BioExcel Center of Excellence for Computational Biomolecular Research](http://bioexcel.eu/)
* * European Commission's [H2020 grant 675728](http://cordis.europa.eu/projects/675728)
* [Common Workflow Language](http://www.commonwl.org/) community and the [CWL Gitter room](https://gitter.im/common-workflow-language/common-workflow-language)
