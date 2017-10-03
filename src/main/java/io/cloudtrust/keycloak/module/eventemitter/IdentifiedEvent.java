package io.cloudtrust.keycloak.module.eventemitter;


import org.keycloak.events.Event;

public class IdentifiedEvent extends Event {

    private long uid;

    /**
     * Constructor by copy which add a uid to Event
     * @param uid
     * @param event
     */
    public IdentifiedEvent(long uid, Event event){
        this.uid = uid;

        setTime(event.getTime());
        setType(event.getType());
        setRealmId(event.getRealmId());
        setClientId(event.getClientId());
        setUserId(event.getUserId());
        setSessionId(event.getSessionId());
        setIpAddress(event.getIpAddress());
        setError(event.getError());
        setDetails(event.getDetails());
    }

    public long getUid(){
        return uid;
    }
}
