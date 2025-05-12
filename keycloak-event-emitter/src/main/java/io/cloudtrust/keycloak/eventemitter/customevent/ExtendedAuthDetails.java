package io.cloudtrust.keycloak.eventemitter.customevent;


import org.keycloak.events.admin.AuthDetails;

/**
 * Extension of the {@link AuthDetails} for adding the agent username
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
