package io.cloudtrust.keycloak.eventemitter.snowflake;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jboss.logging.Logger;

import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.Preconditions.checkArgument;

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
        checkArgument(keycloakId >= 0, String.format("component Id can't be greater than %d or less than 0",
                IdGeneratorConfig.MAX_KEYCLOAK_ID));
        checkArgument(keycloakId <= IdGeneratorConfig.MAX_KEYCLOAK_ID, String.format("component Id can't be greater than %d "
                + "or less than 0", IdGeneratorConfig.MAX_KEYCLOAK_ID));

        checkArgument(datacenterId >= 0, String.format("Datacenter ID can't be greater than %d or less than 0",
                IdGeneratorConfig.MAX_DATACENTER_ID));
        checkArgument(datacenterId <= IdGeneratorConfig.MAX_DATACENTER_ID, String.format("Datacenter ID can't be greater than %d or "
                + "less than 0", IdGeneratorConfig.MAX_DATACENTER_ID));

        this.keycloakId = keycloakId;
        this.datacenterId = datacenterId;

        logger.infof("IdGenerator general settings: timestamp left shift = %d, datacenter ID bits = %d, "
                        + "keycloak ID bits = %d, sequence bits = %d", IdGeneratorConfig.TIMESTAMP_LEFT_SHIFT, IdGeneratorConfig.DATACENTER_ID_BITS,
                IdGeneratorConfig.KEYCLOAK_ID_BITS, IdGeneratorConfig.SEQUENCE_BITS);
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
        long curSequence;

        final long prevTimestamp = lastTimestamp.get();

        if (timestamp < prevTimestamp) {
            logger.errorf("clock is moving backwards. Rejecting requests until %d", prevTimestamp);
            throw new InvalidSystemClock(String.format("Clock moved backwards. Refusing to generate id "
                    + "for %d milliseconds", prevTimestamp - timestamp));
        }

        if (prevTimestamp == timestamp) {
            curSequence = sequence.incrementAndGet() & IdGeneratorConfig.SEQUENCE_MASK;
            if (curSequence == 0) {
                timestamp = tilNextMillis(prevTimestamp);
            }
        } else {
            curSequence = 0L;
            sequence.set(0L);
        }

        lastTimestamp.set(timestamp);
        final long id = ((timestamp - IdGeneratorConfig.START_EPOCH) << IdGeneratorConfig.TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << IdGeneratorConfig.DATACENTER_ID_SHIFT)
                | (keycloakId << IdGeneratorConfig.KEYCLOAK_ID_SHIFT) | curSequence;

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
        return new ToStringBuilder(this)
                .append("componentId", keycloakId)
                .append("datacenterId", datacenterId)
                .append("timestamp left shift", IdGeneratorConfig.TIMESTAMP_LEFT_SHIFT)
                .append("datacenter ID bits", IdGeneratorConfig.DATACENTER_ID_BITS)
                .append("component ID bits", IdGeneratorConfig.KEYCLOAK_ID_BITS)
                .append("sequence bits", IdGeneratorConfig.SEQUENCE_BITS)
                .toString();
    }
}
