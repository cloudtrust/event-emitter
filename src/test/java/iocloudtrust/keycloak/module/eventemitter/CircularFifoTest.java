package iocloudtrust.keycloak.module.eventemitter;

import io.cloudtrust.keycloak.module.eventemitter.CircularFifo;
import org.junit.Assert;
import org.junit.Test;
import org.keycloak.events.Event;

import java.util.NoSuchElementException;

public class CircularFifoTest {

    @Test
    public void testAdd() {
        int capacity = 2;
        CircularFifo<Integer> fifo = new CircularFifo<>(capacity);

        for (int i = 0; i < 2 * capacity; i++) {
            fifo.add(i);
        }

        Assert.assertEquals(capacity, fifo.size());
    }


    @Test
    public void testRemove() {
        int capacity = 5;
        CircularFifo<Integer> fifo = new CircularFifo<>(capacity);

        for (int i = 0; i < capacity; i++) {
            fifo.add(i);
        }

        for (int i = capacity; i > 0; i--) {
            fifo.remove();
            Assert.assertEquals(i-1, fifo.size());
        }

        try {
            fifo.remove();
            Assert.fail("NoSuchElementException should be thrown");
        } catch (NoSuchElementException e) {
            //Expected exception
            return;
        }

        Assert.fail("NoSuchElementException should be thrown");
    }

    @Test
    public void testIsEmpty() {
        int capacity = 2;
        CircularFifo<Integer> fifo = new CircularFifo<>(capacity);

        Assert.assertTrue(fifo.isEmpty());
        fifo.add(1);
        Assert.assertFalse(fifo.isEmpty());
        fifo.remove();
        Assert.assertTrue(fifo.isEmpty());
    }

    @Test
    public void testCapacity(){
        int capacity = 2;
        CircularFifo<Integer> fifo = new CircularFifo<>(capacity);

        fifo.add(1);
        fifo.add(2);
        Assert.assertEquals(2, fifo.size());
        fifo.add(3);
        Assert.assertEquals(2, fifo.size());
        Assert.assertEquals(2, (long)fifo.remove());
        Assert.assertEquals(3, (long)fifo.remove());
    }
}
