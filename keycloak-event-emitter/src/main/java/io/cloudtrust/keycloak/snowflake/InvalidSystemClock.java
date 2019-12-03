package io.cloudtrust.keycloak.snowflake;

/**
 * @author Sébastien Pasche
 */
public class InvalidSystemClock extends Exception {
    private static final long serialVersionUID = -6759653877710725413L;

    public InvalidSystemClock(final String message) {
        super(message);
    }
}
