package io.cloudtrust.keycloak.snowflake;


/**
 * @author Sébastien Pasche
 */

public final class IdGeneratorConfig {

    /*
      start epoch: 01 Jan 2015 00:00:00 GMT
      valid ids until Wed, 06 Sep 2084 15:47:35 GMT
   */
    public static final long START_EPOCH = 1420070400000L;

    public static final long KEYCLOAK_ID_BITS = 5L;
    public static final long DATACENTER_ID_BITS = 2L;
    public static final long MAX_KEYCLOAK_ID = -1L ^ (-1L << KEYCLOAK_ID_BITS);
    public static final long MAX_DATACENTER_ID = -1L ^ (-1L << DATACENTER_ID_BITS);
    public static final long SEQUENCE_BITS = 15L;

    public static final long KEYCLOAK_ID_SHIFT = SEQUENCE_BITS;
    public static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + KEYCLOAK_ID_BITS;
    public static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + KEYCLOAK_ID_BITS + DATACENTER_ID_BITS;
    public static final long SEQUENCE_MASK = -1L ^ (-1L << SEQUENCE_BITS);

    private IdGeneratorConfig() {
    }
}