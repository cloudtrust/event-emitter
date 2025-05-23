package io.cloudtrust.keycloak.eventemitter.customevent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.cloudtrust.keycloak.eventemitter.HasUid;
import org.keycloak.events.admin.AdminEvent;

public class IdentifiedAdminEvent extends AdminEvent implements HasUid {
    private final long uid;

    /**
     * Constructor by copy which add a uid to AdminEvent.
     *
     * @param uid        for idempotence
     * @param adminEvent original adminEvent
     */
    public IdentifiedAdminEvent(long uid, AdminEvent adminEvent) {
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

    public long getUid() {
        return uid;
    }

    @Override
    @JsonIgnore
    public String getResourceTypeAsString() {
        return super.getResourceTypeAsString();
    }
}
