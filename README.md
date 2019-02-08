# Event Emitter

Event emitter is a Keycloak module based on EventListener service provider interface (SPI).
During the lifecycle of Keycloak, Event and AdminEvent are created when specific actions are created.
The aim of this module is to send those Events and AdminEvents to another server in a serialized format.

## Compilation

Java 8 is required (Java 9+ is not supported yet).

### Build and Tests
This module contains both unit and integration tests, and a parent inherited from Keycloak tests for simplifying the
POM content.

The integration tests rely on the arquillian-based Keycloak test framework. As Keycloak does not publish publicly
the related jars for testing, one needs to manually build them so that they are available for maven for testing.

For building these tests-jars, you need to:
* clone the official Keycloak repository (https://github.com/keycloak/keycloak)
* checkout the tag of the Keycloak version related to the version of the event-emitter module
* execute `mvn clean install -dskipTests` in the Keycloak repository to build the jars and put them in your maven repository

Once these test-jars are built, the event-emitter module can be built.

### Binary
The build produces the JAR of the module, along with a TAR.GZ file that contains the dependencies to be installed
with the module.

## Installation
Event emitter module is expected to be installed as a module in a specific layer.

To install the release, use the TAR.GZ file produced by the build; either run the `install.sh` script
or manually proceed as follows:

```Bash
# Install the module binaries
tar -zxf event-emitter-<version>.Final-dist.tar.gz --directory <PATH_TO_KEYCLOAK>/modules/system/layers

# Set the appropriate permissions on the new files
chmod -R 755 <PATH_TO_KEYCLOAK>/modules/system/layers/event-emitter
```

For enabling the newly created layer, edit __layers.conf__:
```Bash
layers=keycloak,event-emitter
```



## Enable & Configure

In __standalone.xml__, add the new module and configure it

```xml
<!--[...]-->
<subsystem xmlns="urn:jboss:domain:keycloak-server:1.1">
    <web-context>auth</web-context>
    <providers>
        <!--[...]-->
        <provider>module:io.cloudtrust.keycloak.eventemitter</provider>
        <!--[...]-->
    </providers>
    <!--[...]-->
    <spi name="eventsListener">
        <provider name="event-emitter" enabled="true">
            <properties>
               <property name="format" value="JSON"/>
               <property name="targetUri" value="http://localhost:8888/event/receiver"/>
               <property name="bufferCapacity" value="10"/>
               <property name="keycloakId" value="1"/>
               <property name="datacenterId" value="1"/>
            </properties>   
        </provider>
    </spi>
    <!--[...]-->
</subsystem>
```

Configuration parameters:
* format: JSON or FLATBUFFER
* targetUri: server endpoint where to send the serialized events
* bufferCapacity: window size of events kept in memory if failure occurs
* keycloakId: configuration parameter for snowflake unique ID generation, id of the keycloak instance
* datacenterId: configuration parameter for snowflake unique ID generation, id of the datacenter

For FLATBUFFER format, the serialized event in wrapped in a JSON to transmit type information. The wrapper has two keys, 'type' which is 'Event' or 'AdminEvent' and 'obj' which is the flatbuffer in base64 format.

All parameters are mandatory, if any of them is invalid or missing keycloak fails to start with a error message in the log about the cause.

After file edition, restart keycloak instance.

Finally, to make the event emitter functional we hate to register it via the admin console.
In Manage - Events, go to Config tab and add event-emitter among the Event listeners.

Note that configuration parameters can be seen in Server Info, tab Providers.


## Development tips

### Flatbuffers

Flatbuffers schema is located under src/main/flatbuffers/flatbuffers/event.fbs.

Compilation of the schema
```Bash
$FLATC_HOME/flatc --java events.fbs
```
Generated classes must be located in src/main/java/flatbuffers/events

*Quick note for flatc installation*
```Bashde 
$ git clone https://github.com/google/flatbuffers.git
$ cd flatbuffers
$ cmake -G "Unix Makefiles"
$ make
$ ./flattests # this is quick, and should print "ALL TESTS PASSED"
```
(Source: https://rwinslow.com/posts/how-to-install-flatbuffers/)


### Idempotence
A unique id is added to the serialized Events and AdminEvents in order to uniquely identify each of them and thus ensure the storage unicity on the target server.
The unique ID generation is ensured by Snowflake ID generation which ensure unicity of ID among multiple keycloak nodes and datacenters.


### Logging
Logging level usage:
* DEBUG : verbose information for debug/development purpose.
* INFO : Informative message about current lifecycle of the module.
* ERROR : Fatal error. Recoverable errors are logged at INFO level.

### Concurrency 
Factory is application-scoped while provider is request-scoped (hence single-threaded).
Different threads can use multiple providers concurrently.
Provider doesn't need to be thread-safe but factory should be, that's why the Queue used to store the events is concurrency-safe.
(Mailing list keycloak-dev, answer from Marek Posolda <mposolda@redhat.com>)

### Buffer
If the target server is not available, the Events and AdminEvents are stored in a Queue.
This queue has a configurable limited capacity. When the queue is full, the oldest event is dropped to store  the new one.
For each new events or adminEvents, the event-emitter will try to send all the events stored in the buffer.
Events remains in the buffer until they are successfully received by the target or dropped to make space for new ones.

## Update process
Each time a new Keycloak version is issued, the project must be updated:
* update the POM with the version of the components that matches the Keycloak version
* ensure that all libraries that are not included by default with Keycloak are added by hand
  * adapt the `assembly.xml` file to package the jars that are not included
  * add the appropriate `module-*.xml` files in the `src/assembly` folder
  * adapt the properties in the `filter.properties`
* check whether the code still compiles (run `mvn compile`)
* ensure that the enum values in `event.fbs` are complete by comparing with the Keycloak source code
* generate the flatbuffers stubs as described above (use a flatbuffers binary that matched the flatbuffers libraries in the POM)
* run the tests and generate the JAR module and the TAR.GZ distribution package: `mvn package`
  * ensure that the distribution package contains everything that is needed for the module to properly run