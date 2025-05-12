package io.cloudtrust.keycloak.eventemitter.kafkaemitter;

public class KafkaEventEmitterState {
    private state currentState;

    public void initialized() {
        currentState = state.INITIALIZED;
    }

    public void starting() {
        currentState = state.STARTING;
    }

    public void pending() {
        currentState = state.PENDING;
    }

    public void working() {
        currentState = state.WORKING;
    }

    public boolean isInitialized() {
        return currentState == state.INITIALIZED;
    }

    public boolean isStarting() {
        return currentState == state.STARTING;
    }

    public boolean isPending() {
        return currentState == state.PENDING;
    }

    public boolean isWorking() {
        return currentState == state.WORKING;
    }

    private enum state {
        INITIALIZED, STARTING, PENDING, WORKING;
    }
}
