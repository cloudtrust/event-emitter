package io.cloudtrust.keycloak.module.eventemitter;

/**
 * Exception for EventEmitter Provider
 */
public class EventEmitterException extends Exception{

    private String message;

    public EventEmitterException(String message){
        this.message = message;
    }

}
