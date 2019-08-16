Fusepool P3 Spatial Search Demo
============================

A LDP-enabled web application that uses the data sets from Fusepool P3 LDP to search for points of interest or events.

[![Build Status](https://travis-ci.org/fusepoolP3/p3-spatialsearch-demo.svg?branch=master)](https://travis-ci.org/fusepoolP3/p3-spatialsearch-demo)

## Build
Compile the application running the command

    mvn install

## Run
Start the application using the command

    mvn exec:java

The webapp can be started using the release (executable jar)

    java -jar p3-spatialsearch-demo-<version>-standalone.jar

The default port is set in the pom.xml file (7302). To change the port use the httpPort option, for example to use the port 8080 run the command

    java -jar p3-spatialsearch-demo-<version>-standalone.jar --httpPort=8080

## Use
Open your browser at the URL

    http://localhost:7302/

The application consumes RDF data and shows objects and events described using the schema.org ontology.
