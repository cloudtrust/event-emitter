package io.cloudtrust.keycloak.eventemitter;

import com.google.gson.Gson;
import io.cloudtrust.keycloak.AbstractTest;
import io.cloudtrust.keycloak.customevent.ExtendedAdminEvent;
import io.cloudtrust.keycloak.pages.LogoutPage;
import io.cloudtrust.keycloak.test.container.KeycloakDeploy;
import io.cloudtrust.keycloak.test.pages.WebPage;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.representations.idm.RealmEventsConfigRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import javax.ws.rs.core.Response;

import static io.cloudtrust.keycloak.test.matchers.CtMatchers.and;
import static io.cloudtrust.keycloak.test.matchers.EventMatchers.doesNotExist;
import static io.cloudtrust.keycloak.test.matchers.EventMatchers.hasUserId;
import static io.cloudtrust.keycloak.test.matchers.EventMatchers.isKeycloakType;
import static io.cloudtrust.keycloak.test.matchers.PageMatchers.isCurrent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ExtendWith(KeycloakDeploy.class)
public class MessageGenerationItTest extends AbstractTest {
    protected static final Logger logger = Logger.getLogger(MessageGenerationItTest.class);

    private static final String CLIENT = "admin-cli";
    private static final String TEST_USER = "user-test-event-emitter";

    @WebPage
    LogoutPage logoutPage;

    @BeforeAll
    public static void initRealmAndUsers() {
        // setup event listener
        String url = KeycloakDeploy.getContainer().getBaseUrl();
        Keycloak keycloak = Keycloak.getInstance(url, "master", "admin", "admin", CLIENT);
        RealmEventsConfigRepresentation eventConfig = keycloak.realm("master").getRealmEventsConfig();
        eventConfig.getEventsListeners().add("event-emitter");
        keycloak.realm("master").updateRealmEventsConfig(eventConfig);
    }

    /**
     * Simulate login, and expect the event emitter to report this event
     */
    @Test
    void testLoginEventReporting() {
        int nbLoginEvents = 0;
        Keycloak keycloak = Keycloak.getInstance(this.getKeycloakURL(), "master", "admin", "admin", CLIENT);

        // test login event
        keycloak.realm("master").users().search(TEST_USER);
        // wait for the event to be reported
        this.sleep(1000);
        String jsonAsString = handler.toString();
        Event e = new Gson().fromJson(jsonAsString, Event.class);
        if (e.getType() == EventType.LOGIN) {
            nbLoginEvents++;
        }
        Assertions.assertEquals(1, nbLoginEvents);
    }

    /**
     * Simulate logout, and expect the event emitter to report this event
     */
    @Test
    void testLogoutEventReporting() {
        System.out.println(loginPage.getOAuthClient().getLoginFormUrl());
        System.out.println(loginPage.getOAuthClient().getLogoutFormUrl());
        loginPage.open();
        loginPage.login("test.user", "password");
        assertThat(events().poll(), isKeycloakType(EventType.LOGIN));

        loginPage.openLogout(false);
        assertThat(logoutPage, isCurrent());
        logoutPage.logout();

        assertThat(events().poll(), and(isKeycloakType(EventType.LOGOUT), hasUserId()/*, hasDetail(Details.USERNAME, "test.user")*/));
        assertThat(events().poll(), doesNotExist());
    }

    /**
     * Simulate user creation and check that the username of the user created appears
     * in the event, as well as the username of the agent creating the user
     */
    @Test
    void testCreateUserEventReporting() {
        UserRepresentation user = new UserRepresentation();
        user.setUsername("test_user_creation");
        Response rsp = this.getRealm("test").users().create(user);
        String loc = rsp.getHeaderString("Location");
        String createdUserId = loc.substring(loc.lastIndexOf("/") + 1);

        assertThat(rsp.getStatus(), is(201));

        // wait for the event to be reported
        this.sleep(1000);
        String jsonAsString = handler.toString();
        ExtendedAdminEvent e = new Gson().fromJson(jsonAsString, ExtendedAdminEvent.class);
        assertThat(e.getAuthDetails().getUsername(), is("admin"));
        assertThat(e.getDetails().get("user_id"), is(createdUserId));
        assertThat(e.getDetails().get("username"), is("test_user_creation"));
    }
}