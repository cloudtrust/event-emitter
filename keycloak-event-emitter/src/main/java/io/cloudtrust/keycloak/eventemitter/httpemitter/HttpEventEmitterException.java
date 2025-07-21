package io.cloudtrust.keycloak.eventemitter.httpemitter;

import io.cloudtrust.exception.CloudtrustException;

import java.io.Serial;
import java.io.Serializable;

/**
 * Exception for HttpEventEmitter Provider
 */
public class HttpEventEmitterException extends CloudtrustException implements Serializable {
    @Serial
    private static final long serialVersionUID = 7324694216864444008L;

    HttpEventEmitterException(String message) {
        super(message);
    }
}
