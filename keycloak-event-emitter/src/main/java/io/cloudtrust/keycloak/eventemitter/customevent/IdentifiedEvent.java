package io.cloudtrust.keycloak.eventemitter.customevent;


import io.cloudtrust.keycloak.eventemitter.HasUid;
import org.keycloak.events.Event;

public class IdentifiedEvent extends Event implements HasUid {

    private long uid;

    IdentifiedEvent() {
        super();
    }

    /**
     * Constructor by copy which add a uid to Event
     *
     * @param uid   for idempotence
     * @param event original event
     */
    public IdentifiedEvent(long uid, Event event) {
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

    public long getUid() {
        return uid;
    }

    public void setUid(long uid) {
        this.uid = uid;
    }
}
