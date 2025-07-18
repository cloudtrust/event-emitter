# Event Emitter

Event emitter is a Keycloak module based on EventListener service provider interface (SPI).
During the lifecycle of Keycloak, Event and AdminEvent are created when specific actions are created.
The aim of this module is to send those Events and AdminEvents to another server in a serialized format.

## Compilation

Java 21 is required.

### Build and Tests
This project contains 2 modules, one with the event emitter code (could contain unit tests) and one for integration tests using a parent inherited from Keycloak tests for simplifying the
POM content.

The integration tests rely on the arquillian-based Keycloak test framework. As Keycloak does not publish publicly
the related jars for testing, one needs to manually build them so that they are available for maven for testing.

### Binary
The build produces the JAR of the module, along with a TAR.GZ file that contains the dependencies to be installed
with the module.

## Installation
Event emitter module is expected to be installed as a module in a specific layer.

To install the release, go to event-emitter module directory and use the TAR.GZ file produced by the build; either run the `install.sh` script
or manually proceed as follows:

```Bash
# Install the module binaries
tar -zxf keycloak-event-emitter-<version>-dist.tar.gz --directory <PATH_TO_KEYCLOAK>/modules/system/layers

# Set the appropriate permissions on the new files
chmod -R 755 <PATH_TO_KEYCLOAK>/modules/system/layers/event-emitter
```

Configuration parameters of HTTP Event Emitter:
* targetUri: server endpoint where to send the serialized events
* bufferCapacity: window size of events kept in memory if failure occurs
* datacenterId: configuration parameter for snowflake unique ID generation, id of the datacenter
* idpId: name of the IDP, present in headers and used for unique ID generation
* connectTimeoutMillis: timeout in milliseconds until a connection is established. Parameter is optional
* connectionRequestTimeoutMillis: timeout in milliseconds used when requesting a connection from the connection manager. Parameter is optional
* socketTimeoutMillis: socket timeout (SO_TIMEOUT) in milliseconds. Parameter is optional

Configuration parameters of Kafka Event Emitter:
* bufferCapacity: window size of events kept in memory if failure occurs
* clientId Kafka client ID
* boostrapServers the list of Kafka brokers
* eventTopic: name of the topic where events will be sent
* adminEventTopic: name of the topic where admin events will be sent
* securityProtocol: security protocol to use inside kafka
* saslOauthbearerTokenEndpointUrl the URL of the token endpoint
* saslMechanism the SASL mode used by Kafka
* keycloakId: configuration parameter for snowflake unique ID generation, id of the keycloak instance
* datacenterId: configuration parameter for snowflake unique ID generation, id of the datacenter

All parameters are mandatory, if any of them is invalid or missing keycloak fails to start with a error message in the log about the cause.

After file edition, restart keycloak instance.

Finally, to make the event emitter functional we have to register it via the admin console.
In Manage - Events, go to Config tab and add event-emitter among the Event listeners.

Note that configuration parameters can be seen in Server Info, tab Providers.


This module will authenticate itself to the server endpoint with Basic authentication. The password value to use is set thanks to the 'CT_KEYCLOAK_BRIDGE_SECRET_TOKEN' environment variable.


## Development tips

### Flatbuffers

Go to event-emitter module directory.
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
