package io.cloudtrust.keycloak.module.eventemitter;

import org.junit.Assert;
import org.junit.Test;

import java.util.NoSuchElementException;

public class ConcurrentEvictingQueueTest {

    @Test
    public void testOffer() {
        int capacity = 2;
        ConcurrentEvictingQueue<Integer> fifo = new ConcurrentEvictingQueue<>(capacity);

        for (int i = 0; i < 2 * capacity; i++) {
            fifo.offer(i);
        }

        Assert.assertEquals(capacity, fifo.size());
    }


    @Test
    public void testPoll() {
        int capacity = 5;
        ConcurrentEvictingQueue<Integer> fifo = new ConcurrentEvictingQueue<>(capacity);

        for (int i = 0; i < capacity; i++) {
            fifo.offer(i);
        }

        for (int i = capacity; i > 0; i--) {
            Assert.assertEquals(i, fifo.size());
            fifo.poll();
        }

        Assert.assertEquals(null, fifo.poll());
    }
}
