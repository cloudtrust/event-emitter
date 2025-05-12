package io.cloudtrust.keycloak.eventemitter.customevent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.keycloak.events.admin.AuthDetails;

import java.util.HashMap;
import java.util.Map;

/**
 * Extension of the AdminEvent for adding the missing agent username and the potential
 * username of the user (created, updated or deleted)
 */
public class ExtendedAdminEvent extends IdentifiedAdminEvent {
    @JsonIgnore
    private ExtendedAuthDetails extAuthDetails;

    private Map<String, String> details = new HashMap<>();

    public ExtendedAdminEvent(IdentifiedAdminEvent event) {
        super(event.getUid(), event);
    }

    @JsonProperty("details")
    public Map<String, String> getDetails() {
        return details;
    }

    public void setDetails(Map<String, String> details) {
        this.details = details;
    }

    @JsonProperty("extAuthDetails")
    @Override
    public ExtendedAuthDetails getAuthDetails() {
        return extAuthDetails;
    }

    @Override
    public void setAuthDetails(AuthDetails authDetails) {
        if (authDetails == null) {
            this.extAuthDetails = null;
        }
        else {
            this.extAuthDetails = new ExtendedAuthDetails(authDetails);
        }
    }
}
