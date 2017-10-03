package io.cloudtrust.keycloak.snowflake;

/**
 * @author Sébastien Pasche
 */
public class InvalidSystemClock extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidSystemClock(final String message) {
        super(message);
    }
}
