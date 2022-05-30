package io.cloudtrust.keycloak.kafkaeventemitter;

public class KafkaEventEmitterState {
    private String state;

    public void initialized(){
        state = "initialized";
    }

    public  void  starting(){
        state = "starting";
    }

    public void pending(){
        state = "pending";
    }

    public void working(){
        state = "working";
    }

    public boolean isInitialized(){
        return state == "initialized";
    }

    public boolean isStarting(){
        return state == "starting";
    }

    public boolean isPending(){
        return state == "pending";
    }

    public boolean isWorking(){
        return state == "working";
    }
}
