Fusepool P3 Spatial Search Demo
============================

A LDP-enabled web application that uses the data sets from Fusepool P3 LDP to search for points of interest or events.

[![Build Status](https://travis-ci.org/fusepoolP3/p3-spatialsearch-demo.svg?branch=master)](https://travis-ci.org/fusepoolP3/p3-spatialsearch-demo)

Compile the application running the command

    mvn install

Start the application using the command

    mvn exec:java

The webapp can be start using the release (executable jar)

    java -jar p3-spatialsearch-demo-<version>-standalone.jar

The default port is 8080. To change the port use the httpPort option, for example to use the port 7320 run the command

    java -jar p3-spatialsearch-demo-<version>-standalone.jar --httpPort=7320


The application consumes RDF data and shows object and event described by the schema.org ontology.
