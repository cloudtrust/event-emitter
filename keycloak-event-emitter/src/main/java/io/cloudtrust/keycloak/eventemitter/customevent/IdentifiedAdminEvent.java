package io.cloudtrust.keycloak.eventemitter.customevent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.cloudtrust.keycloak.eventemitter.HasUid;
import org.keycloak.events.admin.AdminEvent;

public class IdentifiedAdminEvent extends AdminEvent implements HasUid {
    private final long uid;

    /**
     * Constructor by copy which add a uid to AdminEvent.
     * Copies the fields of the Keycloak AdminEvent that exist in the FlatBuffers schema (not id, realmName nor details:
     * Keycloak 26.8.1 fills AdminEvent.details for more operations, we deliberately do not forward them; the
     * 26.8.1 getResourceId() is a derived getter, no field to copy) (unchanged in Keycloak 26.8.1)
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
