package io.cloudtrust.keycloak.eventemitter;

public class Container {

    private String type;
    private String obj;

    public Container() {
    }

    public Container(String type, String obj) {
        this.type = type;
        this.obj = obj;
    }

    public String getType() {
        return type;
    }

    public String getObj() {
        return obj;
    }
}
