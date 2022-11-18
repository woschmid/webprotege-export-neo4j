WebProtégé
==========

PLEASE NOTE
===========

**This repository is in the process of being superceded** with a collection of other, more fine-grained, [repositories](https://github.com/search?q=topic%3Awebprotege+org%3Aprotegeproject&type=Repositories). We are moving WebProtégé to a microservice architecture and each microservice and each common library now has its own repository.  While this repository still serves as the repository for the current WebProtégé release, all active development is now taking place in these repositories.  You can read more about this on the [WebProtégé Next Gen Wiki](https://github.com/protegeproject/webprotege-next-gen/wiki/WebProtégé-Next-Generation-Overview).


What is WebProtégé?
-------------------

WebProtégé is a free, open source collaborative ontology development environment.

It provides the following features:
- Support for editing OWL 2 ontologies
- A default simple editing interface, which provides access to commonly used OWL constructs
- Full change tracking and revision history
- Collaboration tools such as, sharing and permissions, threaded notes and discussions, watches and email notifications
- Customizable user interface
- Support for editing OBO ontologies
- Multiple file formats for upload and download of ontologies (supported formats: RDF/XML, Turtle, OWL/XML, OBO, and others)

WebProtégé runs as a Web application. End users access it through their Web browsers.
They do not need to download or install any software. We encourage end-users to use

https://webprotege.stanford.edu

If you have downloaded the webprotege war file from GitHub, and would like to deploy it on your own server,
please follow the instructions at:

https://github.com/protegeproject/webprotege/wiki/WebProtégé-4.0.0-beta-x-Installation

Building
--------

To build WebProtégé from source

1) Clone the github repository
   ```
   git clone https://github.com/woschmid/webprotege-export-neo4j.git
   ```
2) Open a terminal in the directory where you clone the repository to
3) Use maven to package WebProtégé - skip the tests because they tend to fail because of date problems in some of the tests
   ```
   mvn clean package -DskipTests
   ```
4) The WebProtege .war file will be built into the webprotege-server directory

Building a Docker image
-------------------------

Open a terminal in the root directory of your cloned webprogete-export-neo4j folder where you will find the _Dockerfile_.

The following command creates a local docker image instance called _webprotege-export-neo4j_ which then can be used
to run from Docker (next section). **Note**: the building process requires 10 - 30 minutes!

   ```bash
   docker build -t webprotege-export-neo4j --build-arg WEBPROTEGE_VERSION=5.0.0-SNAPSHOT .
   ```

By default, the host for webprotege is named _webprotege_ and the host for neo4j is named _neo4j_ 
(defined in the _docker-compose.yml_ and in _edu.stanford.bmir.protege.web.server.export.ProjectExportService.java_)

Running from Docker using docker-compose
-------------------

**Requirement**: A local Docker image called _webprotege-export-neo4j_ already exists (see the previous section
Buiding a Docker image if not yet created).

Open a terminal in the directory where _docker-compose.yml_ is.

**Note**: The volume folders .neo4j and .protegedata will be created in the current directory where the docker-compose
command will be executed. 

#### Start WebProtégé, Neo4j and MongoDB using docker-compose:

1. Enter this following command in the Terminal to start the docker container in the background

   ```bash
   docker-compose up -d
   ```
2. Check with 
   ```bash
   docker stats
   ```
   or with Docker Desktop (Windows) if the 3 containers (webproetege-mongodb, neo4j and webprotege) are running

3. **Only necessary once**: Create the admin user (follow the questions prompted to provider username, email and password)

   ```bash
   docker exec -it webprotege java -jar /webprotege-cli.jar create-admin-account
   ```

4. **Only necessary once**: Browse to WebProtégé Settings page in a Web browser by navigating to [http://localhost:5000/#application/settings](http://localhost:5000/#application/settings)
   1. Login as admin user (step 3.)
   2. Define the `System notification email address` and `application host URL`
   3. Enable `User creation`, `Project creation` and `Project import`
   4. Reload Browser

5. Login into **WebProtege** by navigating to http://localhost:5000/ and login with admin user

6. Open **Neo4j Browser** in [http://localhost:7474/browser/](http://localhost:7474/browser/) and login with 
username/password neo4j/test (can be changed in docker-compose.yml)

#### Stop WebProtégé, Neo4j and MongoDB:

   ```bash
   docker-compose down
   ```
#### Additional infos

Sharing the volumes used by the WebProtégé app and MongoDB allow to keep persistent data, even when the containers stop. Default shared data storage:

* WebProtégé will store its data in the source code folder at `./.protegedata/protege` where you run `docker-compose`
* MongoDB will store its data in the source code folder at `./.protegedata/mongodb` where you run `docker-compose`

> Path to the shared volumes can be changed in the `docker-compose.yml` file.

Running from Maven (localhost)
------------------

0) By default the installation is set up for Docker.
But as an alternatively WebProtege (this instance), Neo4j (desktop app) and MongoDB (desktop app) can be run on locally without Docker too.
This however requires some preparation and a modification in the source code. 

1) Neo4J is to be run separately as Desktop app and requires the manual installation of the NeoSemantics plugin. 
Check the online documentation for Neo4J and NeoSemantics.

2) Also install and run a local instance of MongoDB

3) WebProtege requieres an adaption in the source code and a recompilation: 
- Change the variables `NEO4JHOST` and `WEBPROTEGEHOST` in the class `edu.stanford.bmir.protege.web.server.export.ProjectExportService` 
to `localhost` and then recompile the source code (see Building).

4) Set up the necessary requirements for WebProtege as described in [https://github.com/protegeproject/webprotege/wiki/WebProt%C3%A9g%C3%A9-4.0.0-Installation](the installation instructions)
In short:
   - **JAVA 11**
   - WebProtégé requires a directory, that we call the data directory to store its data in. Create the data directory on your system. We recommend you use the standard location, `/srv/webprotege`. On a Windows system this corresponds to `C:\srv\webprotege`
   - Create the directory to hold the WebProtégé configuration files. On Linux/Unix/MacOS this should be `/etc/webprotege`. On Windows this should be `C:\ProgramData\WebProtege` (note that the ProgramData directory is a hidden directory).
   - Create a copy of the [https://github.com/protegeproject/webprotege/blob/master/webprotege-server-core/src/main/resources/webprotege.properties](webprotege.properties) file inside this configuration directory.
   - In the webprotege.properties file you should adjust the various properties as appropriate for your installation. In particular, you should ensure that the data.directory property is set to point to the WebProtégé data directory for your installation. For example, on Linux/Unix/MacOS,
`data.directory=/srv/webprotege` or on Windows, `data.directory=C:\\srv\\webprotege`
   - Create a copy of the mail.properties [https://github.com/protegeproject/webprotege/blob/master/webprotege-server-core/src/main/resources/mail.properties] 

5) Start the GWT code server in one terminal window
    ```
    mvn gwt:codeserver
    ```
6) In a different terminal window start the tomcat server
    ```
    mvn -Denv=dev tomcat7:run
    ```
7) Bootstrap WebProtégé with an Admin Account (see https://github.com/protegeproject/webprotege/wiki/WebProt%C3%A9g%C3%A9-4.0.0-Installation)
**HINT**: The client jar is located in the `webprotege-cli` target folder after a successful compilation, therefore just enter the following command into the terminal there: `java -jar webprotege-cli-5.0.0-SNAPSHOT.jar create-admin-account`
8) Browse to WebProtégé in a Web browser by navigating to [http://localhost:8080](http://localhost:8080)
9) Sign in with the admin account created before
10) If you get the Error message "WebProtégé is not configured properly" navigate to [http://localhost:8080/#application/settings] and
    1)  Specify all required settings such as Application name, Email Address, Scheme (http), Host (localhost) as well as Permission settings. Apply your Settings and the warning should disappear.
11) Start Neo4J separately with your local desktop application
    1) Note that Neo4J version has to support the Neosemantics (n10s) plugin. Last working version at the time of writing this is 4.4.12. No support of this plugin in version 5 yet.
    2) Create a new DB with 4.4.12 and password `test` (see `NEO4JUSER` and `NEO4JPASS` in class `edu.stanford.bmir.protege.web.server.export.ProjectExportService`)
12) Import an ONTIS ontology and export the ontology into neo4j the projects overview (Export to Neo4j... )
