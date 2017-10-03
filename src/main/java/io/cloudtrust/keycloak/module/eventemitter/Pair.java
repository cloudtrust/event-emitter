package io.cloudtrust.keycloak.module.eventemitter;

public class Pair<K,V> {

    public K key;
    public V value;

    public Pair(K k, V v){
        this.key = k;
        this.value = v;
    }

}
