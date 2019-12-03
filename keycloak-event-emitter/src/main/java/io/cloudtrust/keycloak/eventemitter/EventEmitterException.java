package io.cloudtrust.keycloak.eventemitter;

/**
 * Exception for EventEmitter Provider
 */
class EventEmitterException extends Exception{
    private static final long serialVersionUID = 7324694216864444008L;

    EventEmitterException(String message){
        super(message);
    }
}
