package io.cloudtrust.keycloak.snowflake;



import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;


/**
 * @author Sébastien Pasche
 */
public class IdGeneratorTest {

    @Test
    public void testGenerateId() throws Exception {
        final IdGenerator idGenerator = new IdGenerator(1, 1);
        final Long id = idGenerator.nextValidId();
        assertTrue(id > 0L);
    }

    @Test
    public void testIncreasingIds() throws Exception {
        final IdGenerator idGenerator = new IdGenerator(1, 1);
        Long lastId = 0L;
        for (int i = 0; i < 100; i++) {
            Long id = idGenerator.nextValidId();
            assertTrue(id > lastId);
            lastId = id;
        }
    }

    @Test
    public void testMillionIds() throws Exception {
        final IdGenerator idGenerator = new IdGenerator(31, 3);
        final Long startTime = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            idGenerator.nextValidId();
        }
        final Long endTime = System.currentTimeMillis();
        System.out.println(String.format(
                "generated 1000000 ids in %d ms, or %,.0f ids/second",
                (endTime - startTime), 1000000000.0 / (endTime - startTime)));
    }

    @Test
    public void testGenerateUniqueIds() throws Exception {
        final IdGenerator idGenerator = new IdGenerator(0, 0);
        final Set<Long> ids = new HashSet<>();
        final int count = 2000000;
        for (int i = 0; i < count; i++) {
            Long id = idGenerator.nextValidId();;
            if (ids.contains(id)) {
                System.out.println(Long.toBinaryString(id));
            } else {
                ids.add(id);
            }
        }
        assertTrue(ids.size() == count);
    }


    @Test
    public void testGenerateIdsOver50Billion() throws Exception {
        final IdGenerator idGenerator = new IdGenerator(0, 0);
        assertTrue(idGenerator.nextValidId() > 50000000000L);
    }

    @Test
    public void testUniqueIdsBackwardsTime() throws Exception {
        final long sequenceMask = -1L ^ (-1L << 15);
        final StaticTimeGenerator generator = new StaticTimeGenerator(0, 0);

        // first we generate 2 ids with the same time, so that we get the sequence to 1
        assertTrue(generator.getSequence().longValue() == 0L);
        assertTrue(generator.time == 1L);

        final Long id1 = generator.nextId();
        assertTrue(id1 >> 22 == 1L);
        assertTrue((id1 & sequenceMask) == 0L);

        assertTrue(generator.getSequence().longValue() == 0L);
        assertTrue(generator.time == 1L);

        final Long id2 = generator.nextId();
        assertTrue(id2 >> 22 == 1L);
        assertTrue((id2 & sequenceMask) == 1L);

        // then we set the time backwards
        generator.time = 0L;
        assertTrue(generator.getSequence().longValue() == 1L);

        Throwable e = null;
        try {
            generator.nextId();
        } catch (InvalidSystemClock ex) {
            e = ex;
            assertTrue(generator.getSequence().longValue() == 1L);
        }
        assertTrue(e instanceof InvalidSystemClock);

        generator.time = 1L;
        final Long id3 = generator.nextId();
        assertTrue(id3 >> 22 == 1L);
        assertTrue((id3 & sequenceMask) == 2L);
    }

    class StaticTimeGenerator extends IdGenerator {
        public long time = 1L;

        public StaticTimeGenerator(final Integer componentId, final Integer datacenterId) {
            super(componentId, datacenterId);
        }

        @Override
        protected long timeGen() {
            return time + IdGeneratorConfig.START_EPOCH;
        }

    }
}
