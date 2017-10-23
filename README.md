# Event Emitter

Event emitter is a Keycloak module based on EventListener service provider interface (SPI).
During the lifecycle of Keycloak, Event and AdminEvent are created when specific actions are created.
The aim of this module is to send those Events and AdminEvents to another server in a serialized format.

## Compilation
To produce the JAR of this module just use maven in a standard way:
```Bash
mvn package
```

## Installation
Event emitter module is expected to be installed as a module in a specific layer.

```Bash
#Create layer in keycloak setup
# Note: in our case <PATH_TO_KEYCLOAK> = /opt/keycloak/keycloak
install -d -v -m755 <PATH_TO_KEYCLOAK>/modules/system/layers/eventemitter -o keycloak -g keycloak

#Setup the module directory
install -d -v -m755 <PATH_TO_KEYCLOAK>/modules/system/layers/eventemitter/io/cloudtrust/keycloak/eventemitter/main/ -o keycloak -g keycloak

#Install jar
install -v -m0755 -o keycloak -g keycloak -D target/event-emitter-1.0.Final.jar <PATH_TO_KEYCLOAK>/modules/system/layers/eventemitter/io/cloudtrust/keycloak/eventemitter/main/

#Install module file
install -v -m0755 -o keycloak -g keycloak -D src/main/resources/module.xml <PATH_TO_KEYCLOAK>/modules/system/layers/eventemitter/io/cloudtrust/keycloak/eventemitter/main/
```

module.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<module xmlns="urn:jboss:module:1.3" name="io.cloudtrust.keycloak.eventemitter">
    <resources>
        <resource-root path="event-emitter-1.0-Final.jar"/>
    </resources>
    <dependencies>
        <module name="org.keycloak.keycloak-core"/>
        <module name="org.keycloak.keycloak-server-spi"/>
        <module name="org.keycloak.keycloak-server-spi-private"/>
        <module name="org.jboss.logging"/>
        <module name="org.apache.httpcomponents"/>
        <module name="com.fasterxml.jackson.core.jackson-databind"/>
        <module name="com.fasterxml.jackson.core.jackson-core"/>
        <module name="org.apache.commons.collections4"/>
        <module name="com.google.guava"/>
    </dependencies>
</module>
```

As far as possible, existing dependencies are used by this module but some of them are new ones that need to be added.
Download JAR dependency of commons-collections4 and Guava with the version specified in the pom.xml.

Dependencies to add:
* commons-collections4
* guava

```Bash
#Create the module directory for collections4
install -d -v -m755 <PATH_TO_KEYCLOAK>/modules/system/layers/eventemitter/org/apache/commons/collections4/main -o keycloak -g keycloak

#Create the module directory for Guava
install -d -v -m755 <PATH_TO_KEYCLOAK>/modules/system/layers/eventemitter/com/google/guava/main -o keycloak -g keycloak

#Install jar
install -v -m0755 -o keycloak -g keycloak -D commons-collections4-4.1.jar <PATH_TO_KEYCLOAK>/modules/system/layers/eventemitter/org/apache/commons/collections4/main
install -v -m0755 -o keycloak -g keycloak -D guava-23.1-jre.jar <PATH_TO_KEYCLOAK>/modules/system/layers/eventemitter/com/google/guava/main


#Install module file
install -v -m0755 -o keycloak -g keycloak -D module.xml <PATH_TO_KEYCLOAK>/modules/system/layers/eventemitter/org/apache/commons/collections4/main
install -v -m0755 -o keycloak -g keycloak -D module.xml <PATH_TO_KEYCLOAK>/modules/system/layers/eventemitter/com/google/guava/main

```

module.xml for Collections4
```xml
<?xml version="1.0" encoding="UTF-8"?>
<module xmlns="urn:jboss:module:1.3" name="org.apache.commons.collections4">
    <properties>
        <property name="jboss.api" value="private"/>
    </properties>

    <resources>
        <resource-root path="commons-collections4-4.1.jar"/>
    </resources>

    <dependencies>
    </dependencies>
</module>
```


module.xml for Guava
```xml
<?xml version="1.0" encoding="UTF-8"?>
<module xmlns="urn:jboss:module:1.3" name="com.google.guava">
    <properties>
        <property name="jboss.api" value="private"/>
    </properties>

    <resources>
        <resource-root path="guava-23.1-jre.jar"/>
    </resources>

    <dependencies>
    </dependencies>
</module>
```

Enable the newly created layer, edit __layers.conf__:
```Bash
layers=keycloak,eventemitter
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
               <property name="targetUri" value="http://localhost:8888/event-receiver"/>
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

To use flatbuffers, some classes are needed. No package are available to manage those dependencies, thus classes must be copy paste directly in the project.


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
Events remains in the buffer until they are sucessfully received by the target or dropped to make space for new ones.
