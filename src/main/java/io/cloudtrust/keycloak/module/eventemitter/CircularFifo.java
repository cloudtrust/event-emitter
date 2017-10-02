package io.cloudtrust.keycloak.module.eventemitter;


import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Fist in first out queue with limited capacity.
 * If the queue is full, the older element is dropped to add the new one.
 *
 */
public class CircularFifo<T> {
    Deque<T> deque = new ArrayDeque<>();
    int capacity;

    /**
     * Constructor
     * @param capacity maximum of the queue
     */
    public CircularFifo(int capacity){
        this.capacity = capacity;
    }

    /**
     * Add element to the FIFO queue. If the queue is full the oldest element is removed to make space for the new one.
     * @param elem to add to the queue
     */
    public void add(T elem){
        if(deque.size() >= capacity){
            deque.remove();
        }
        deque.add(elem);
    }

    /**
     * Remove the oldest element of the FIFO queue.
     * @return the oldest element
     * @throws java.util.NoSuchElementException if there is no element to remove
     */
    public T remove(){
        return deque.remove();
    }

    public int size(){
        return deque.size();
    }

    public boolean isEmpty(){
        return deque.isEmpty();
    }

}
