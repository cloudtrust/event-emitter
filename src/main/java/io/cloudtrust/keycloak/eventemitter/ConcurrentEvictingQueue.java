package io.cloudtrust.keycloak.eventemitter;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Concurrency safe non blocking queue.
 * This class is a lazy modification of the LinkedBlockingQueue with a modification of the behavior
 * of the offer method to evict the oldest leement is the queue is full.
 * This implementation only expects the usage of offer and poll methods to manage elements in the queue.
 *
 */
public class ConcurrentEvictingQueue<T> extends LinkedBlockingQueue<T> {

    public ConcurrentEvictingQueue(int capacity){
        super(capacity);
    }


    /**
     * Inserts the specified element at the tail of this queue.
     * If the queue is full, the oldest element is dropped.
     *
     * @param t the element to add
     * @return true in any case as element addition is always possible du to described strategy.
     */
    @Override
    public boolean offer(T t) {

        // Dummy implementation, multiple iterations may be needed to be able to add the element
        while(!super.offer(t)){
            super.poll();
        }

        return true;
    }

    /**
     * @throw UnsupportedOperationException
     */
    @Override
    public boolean offer(T t, long timeout, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException();
    }
}
