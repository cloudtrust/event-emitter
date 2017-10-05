package io.cloudtrust.keycloak.module.eventemitter;

/**
 * Exception for EventEmitter Provider
 */
class EventEmitterException extends Exception{

    EventEmitterException(String message){
        super(message);
    }
}
