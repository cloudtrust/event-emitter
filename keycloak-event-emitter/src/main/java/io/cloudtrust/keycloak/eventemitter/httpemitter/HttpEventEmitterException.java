package io.cloudtrust.keycloak.eventemitter.httpemitter;

import java.io.Serial;

/**
 * Exception for HttpEventEmitter Provider
 */
class HttpEventEmitterException extends Exception {
    @Serial
    private static final long serialVersionUID = 7324694216864444008L;

    HttpEventEmitterException(String message) {
        super(message);
    }
}
