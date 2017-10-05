package io.cloudtrust.keycloak.module.eventemitter;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ConcurrentEvictingQueue<T> extends LinkedBlockingQueue<T> {

    public ConcurrentEvictingQueue(int capacity){
        super(capacity);
    }


    /**
     * Inserts the specified element at the tail of this queue.
     * If the queue is full, the oldest element is dropped.
     * @param t the element to add
     * @return true in any case as element addition is always possible du to described strategy.
     */
    @Override
    public boolean offer(T t) {

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
