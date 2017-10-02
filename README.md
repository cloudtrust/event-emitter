# Event Emitter

This is a keycloak module that sends events to other components

### Idempotence
A snowflake ID is associated to each event, to identify them uniquely and allow idempotence to the bridge.


## Buffer
In case of failure to send the event to the target, the event is stored in buffer
If buffer is full, older event is dropped.
If keycloak restart, buffer is dropped as it is stored in memory.

# Log
Only debug, info or error are used. Error is only used for fatal issues.

#Configuration

Config exemple
All parameters are mandatory

If any parameter is missing or invalid, keycloak will fails to start.
