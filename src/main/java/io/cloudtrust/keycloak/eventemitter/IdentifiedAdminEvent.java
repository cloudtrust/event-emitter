package io.cloudtrust.keycloak.eventemitter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.keycloak.events.admin.AdminEvent;

public class IdentifiedAdminEvent extends AdminEvent{

    private long uid;

    /**
     * Constructor by copy which add a uid to AdminEvent.
     *
     * @param uid for idempotence
     * @param adminEvent original adminEvent
     */
    IdentifiedAdminEvent(long uid, AdminEvent adminEvent){
        this.uid = uid;

        setTime(adminEvent.getTime());
        setRealmId(adminEvent.getRealmId());
        setAuthDetails(adminEvent.getAuthDetails());
        setResourceType(adminEvent.getResourceType());
        setOperationType(adminEvent.getOperationType());
        setResourcePath(adminEvent.getResourcePath());
        setRepresentation(adminEvent.getRepresentation());
        setError(adminEvent.getError());
    }

    public long getUid(){
        return uid;
    }

    @Override
    @JsonIgnore
    public String getResourceTypeAsString() {
        return super.getResourceTypeAsString();
    }
}
