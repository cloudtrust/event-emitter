package io.cloudtrust.keycloak.eventemitter.customevent;


import org.keycloak.events.admin.AuthDetails;

/**
 * Extension of the {@link AuthDetails} for adding the agent username
 * (the copy constructor mirrors the fields of Keycloak AuthDetails, unchanged in Keycloak 26.8.1)
 */
public class ExtendedAuthDetails extends AuthDetails {

    private String username;

    public ExtendedAuthDetails(AuthDetails authDetails) {
        if (authDetails != null) {
            setClientId(authDetails.getClientId());
            setIpAddress(authDetails.getIpAddress());
            setRealmId(authDetails.getRealmId());
            setUserId(authDetails.getUserId());
        }
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
