package io.cloudtrust.keycloak.eventemitter;

import io.cloudtrust.keycloak.eventemitter.customevent.ExtendedAdminEvent;
import io.cloudtrust.keycloak.eventemitter.customevent.ExtendedAuthDetails;
import io.cloudtrust.keycloak.eventemitter.customevent.IdentifiedAdminEvent;
import org.apache.commons.lang3.StringUtils;
import org.keycloak.events.Details;
import org.keycloak.events.Event;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.HashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to complete attributes of Events an AdminEvents.
 */
public class CompleteEventUtils {
    KeycloakSession keycloakSession;

    public CompleteEventUtils(KeycloakSession keycloakSession) {
        this.keycloakSession = keycloakSession;
    }

    public void completeEventAttributes(Event event) {
        // add username if missing
        if (event.getDetails() == null) {
            event.setDetails(new HashMap<>());
        }
        String eventUsername = event.getDetails().get(Details.USERNAME);
        if (StringUtils.isNotBlank(event.getUserId()) && StringUtils.isBlank(eventUsername)) {
            findUser(event.getUserId(), event.getRealmId(), u -> event.getDetails().put(Details.USERNAME, u.getUsername()));
        }
    }

    public ExtendedAdminEvent completeAdminEventAttributes(IdentifiedAdminEvent adminEvent) {
        ExtendedAdminEvent extendedAdminEvent = new ExtendedAdminEvent(adminEvent);
        // add always missing agent username
        ExtendedAuthDetails extendedAuthDetails = extendedAdminEvent.getAuthDetails();
        if (StringUtils.isNotBlank(extendedAuthDetails.getUserId())) {
            findUser(extendedAuthDetails.getUserId(), extendedAuthDetails.getRealmId(), u -> extendedAuthDetails.setUsername(u.getUsername()));
        }
        // add username if resource is a user
        String resourcePath = extendedAdminEvent.getResourcePath();
        if (resourcePath != null && resourcePath.contains("users")) {
            // parse userID
            String pattern = ".*users/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$";
            Pattern r = Pattern.compile(pattern);
            Matcher m = r.matcher(resourcePath);
            if (m.matches()) {
                String userId = m.group(1);
                extendedAdminEvent.getDetails().put("target_user_id", userId);
                findUser(userId, adminEvent.getRealmId(), u -> extendedAdminEvent.getDetails().put("target_username", u.getUsername()));
            }
        }

        return extendedAdminEvent;
    }

    private void findUser(String userId, String realmId, Consumer<UserModel> whenUserFound) {
        RealmModel realm = keycloakSession.realms().getRealm(realmId);
        if (realm != null) {
            UserModel user = keycloakSession.users().getUserById(realm, userId);

            if (user != null) {
                whenUserFound.accept(user);
            }
        }
    }
}
