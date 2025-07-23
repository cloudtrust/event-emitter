package io.cloudtrust.keycloak.eventemitter.snowflake;

import java.io.Serial;

/**
 * @author Sébastien Pasche
 */
public class InvalidSystemClock extends Exception {
    @Serial
    private static final long serialVersionUID = -6759653877710725413L;

    public InvalidSystemClock(final String message) {
        super(message);
    }
}
