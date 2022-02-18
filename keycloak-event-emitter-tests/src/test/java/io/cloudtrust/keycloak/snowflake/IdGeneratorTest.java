package io.cloudtrust.keycloak.snowflake;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * @author Sébastien Pasche
 */
class IdGeneratorTest {
    @Test
    void testGenerateId() {
        final IdGenerator idGenerator = new IdGenerator(1, 1);
        final Long id = idGenerator.nextValidId();
        assertThat(id>0, is(true));
    }

    @Test
    void testIncreasingIds() {
        final IdGenerator idGenerator = new IdGenerator(1, 1);
        Long lastId = 0L;
        for (int i = 0; i < 100; i++) {
            Long id = idGenerator.nextValidId();
            assertThat(id > lastId, is(true));
            lastId = id;
        }
    }

    @Test
    void testMillionIds() {
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
    void testGenerateUniqueIds() {
        final IdGenerator idGenerator = new IdGenerator(0, 0);
        final Set<Long> ids = new HashSet<>();
        final int count = 2000000;
        for (int i = 0; i < count; i++) {
            Long id = idGenerator.nextValidId();
            if (ids.contains(id)) {
                System.out.println(Long.toBinaryString(id));
            } else {
                ids.add(id);
            }
        }
        assertThat(ids.size(), is(count));
    }

    @Test
    void testGenerateIdsOver50Billion() {
        final IdGenerator idGenerator = new IdGenerator(0, 0);
        assertThat(idGenerator.nextValidId() > 50000000000L, is(true));
    }

    @Test
    void testUniqueIdsBackwardsTime() throws Exception {
        final long sequenceMask = -1L ^ (-1L << 15);
        final StaticTimeGenerator generator = new StaticTimeGenerator(0, 0);

        // first we generate 2 ids with the same time, so that we get the sequence to 1
        assertThat(generator.getSequence().longValue(), is(0L));
        assertThat(generator.time, is(1L));

        final Long id1 = generator.nextId();
        assertThat(id1 >> 22, is(1L));
        assertThat(id1 & sequenceMask, is(0L));

        assertThat(generator.getSequence().longValue(), is(0L));
        assertThat(generator.time, is(1L));

        final Long id2 = generator.nextId();
        assertThat(id2 >> 22, is(1L));
        assertThat(id2 & sequenceMask, is(1L));

        // then we set the time backwards
        generator.time = 0L;
        assertThat(generator.getSequence().longValue(), is(1L));

        Throwable e = null;
        try {
            generator.nextId();
        } catch (InvalidSystemClock ex) {
            e = ex;
            assertThat(generator.getSequence().longValue(), is(1L));
        }
        Assertions.assertTrue(e instanceof InvalidSystemClock);

        generator.time = 1L;
        final Long id3 = generator.nextId();
        assertThat(id3 >> 22, is(1L));
        assertThat(id3 & sequenceMask, is(2L));
    }

    class StaticTimeGenerator extends IdGenerator {
        long time = 1L;

        StaticTimeGenerator(final Integer componentId, final Integer datacenterId) {
            super(componentId, datacenterId);
        }

        @Override
        protected long timeGen() {
            return time + IdGeneratorConfig.START_EPOCH;
        }
    }
}
