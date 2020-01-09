package io.cloudtrust.keycloak.snowflake;


import com.google.common.base.MoreObjects;
import org.jboss.logging.Logger;

import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.cloudtrust.keycloak.snowflake.IdGeneratorConfig.DATACENTER_ID_BITS;
import static io.cloudtrust.keycloak.snowflake.IdGeneratorConfig.DATACENTER_ID_SHIFT;
import static io.cloudtrust.keycloak.snowflake.IdGeneratorConfig.KEYCLOAK_ID_BITS;
import static io.cloudtrust.keycloak.snowflake.IdGeneratorConfig.KEYCLOAK_ID_SHIFT;
import static io.cloudtrust.keycloak.snowflake.IdGeneratorConfig.MAX_DATACENTER_ID;
import static io.cloudtrust.keycloak.snowflake.IdGeneratorConfig.MAX_KEYCLOAK_ID;
import static io.cloudtrust.keycloak.snowflake.IdGeneratorConfig.SEQUENCE_BITS;
import static io.cloudtrust.keycloak.snowflake.IdGeneratorConfig.SEQUENCE_MASK;
import static io.cloudtrust.keycloak.snowflake.IdGeneratorConfig.START_EPOCH;
import static io.cloudtrust.keycloak.snowflake.IdGeneratorConfig.TIMESTAMP_LEFT_SHIFT;


/**
 * @author Sébastien Pasche
 */
public class IdGenerator {

    private static final Logger logger = Logger.getLogger(IdGenerator.class);

    private final int keycloakId;
    private final int datacenterId;

    private final AtomicLong lastTimestamp = new AtomicLong(-1L);
    private final AtomicLong sequence;

    public IdGenerator(final int keycloakId, final int datacenterId) {
        this(keycloakId, datacenterId, 0L);
    }

    public IdGenerator(final int keycloakId, final int datacenterId, final long startSequence) {

        checkNotNull(keycloakId);
        checkArgument(keycloakId >= 0, String.format("component Id can't be greater than %d or less than 0",
                MAX_KEYCLOAK_ID));
        checkArgument(keycloakId <= MAX_KEYCLOAK_ID, String.format("component Id can't be greater than %d "
                + "or less than 0", MAX_KEYCLOAK_ID));

        checkNotNull(datacenterId);
        checkArgument(datacenterId >= 0, String.format("Datacenter ID can't be greater than %d or less than 0",
                MAX_DATACENTER_ID));
        checkArgument(datacenterId <= MAX_DATACENTER_ID, String.format("Datacenter ID can't be greater than %d or "
                + "less than 0", MAX_DATACENTER_ID));

        checkNotNull(startSequence);

        this.keycloakId = keycloakId;
        this.datacenterId = datacenterId;

        logger.infof("IdGenerator general settings: timestamp left shift = %d, datacenter ID bits = %d, "
                        + "keycloak ID bits = %d, sequence bits = %d", TIMESTAMP_LEFT_SHIFT, DATACENTER_ID_BITS,
                KEYCLOAK_ID_BITS, SEQUENCE_BITS);
        logger.infof("IdGenerator instance settings: datacenter ID = %d, keycloak ID = %d", datacenterId, keycloakId);
        sequence = new AtomicLong(startSequence);

    }

    /**
     * Get the next ID
     *
     * @return Next ID
     * @throws InvalidSystemClock When the clock is moving backward
     */
    public synchronized long nextId() throws InvalidSystemClock {
        long timestamp = timeGen();
        long curSequence = 0L;

        final long prevTimestamp = lastTimestamp.get();

        if (timestamp < prevTimestamp) {
            logger.errorf("clock is moving backwards. Rejecting requests until %d", prevTimestamp);
            throw new InvalidSystemClock(String.format("Clock moved backwards. Refusing to generate id "
                    + "for %d milliseconds", prevTimestamp - timestamp));
        }

        if (prevTimestamp == timestamp) {
            curSequence = sequence.incrementAndGet() & SEQUENCE_MASK;
            if (curSequence == 0) {
                timestamp = tilNextMillis(prevTimestamp);
            }
        } else {
            curSequence = 0L;
            sequence.set(0L);
        }

        lastTimestamp.set(timestamp);
        final long id = ((timestamp - START_EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (keycloakId << KEYCLOAK_ID_SHIFT) | curSequence;

        logger.debugf(
                "prevTimestamp = %d, timestamp = %d, sequence = %d, id = %d",
                prevTimestamp, timestamp, sequence.get(), id);

        return id;
    }

    public long nextValidId() {
        long id = -1L;
        do {
            try {
                id = nextId();
            } catch (InvalidSystemClock invalidSystemClock) {
                logger.infof("InvalidSystemClock: %s", invalidSystemClock);
            }
        } while (id == -1L);
        return id;
    }

    /**
     * Return the next time in milliseconds
     *
     * @param prevTimestamp Last timestamp
     * @return Next timestamp in milliseconds
     */
    protected long tilNextMillis(final long prevTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= prevTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    protected long timeGen() {
        return System.currentTimeMillis();
    }


    public AtomicLong getSequence() {
        return sequence;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("componentId", keycloakId)
                .add("datacenterId", datacenterId)
                .add("timestamp left shift", TIMESTAMP_LEFT_SHIFT)
                .add("datacenter ID bits", DATACENTER_ID_BITS)
                .add("component ID bits", KEYCLOAK_ID_BITS)
                .add("sequence bits", SEQUENCE_BITS)
                .toString();
    }
}
