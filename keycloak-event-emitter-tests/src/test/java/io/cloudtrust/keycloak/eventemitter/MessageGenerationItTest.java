package io.cloudtrust.keycloak.eventemitter;

import com.google.gson.Gson;
import io.cloudtrust.keycloak.AbstractTest;
import io.cloudtrust.keycloak.customevent.ExtendedAdminEvent;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.events.Details;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.representations.idm.EventRepresentation;
import org.keycloak.representations.idm.RealmEventsConfigRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import javax.ws.rs.core.Response;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

@RunWith(Arquillian.class)
@RunAsClient
public class MessageGenerationItTest extends AbstractTest {

    protected static final Logger logger = Logger.getLogger(MessageGenerationItTest.class);

    private static final String CLIENT = "admin-cli";
    private static final String TEST_USER = "user-test-event-emitter";

    @BeforeClass
    public static void initRealmAndUsers() {
        // setup event listener
        Keycloak keycloak = Keycloak.getInstance(KEYCLOAK_URL, "master", "admin", "admin", CLIENT);
        RealmEventsConfigRepresentation eventConfig = keycloak.realm("master").getRealmEventsConfig();
        eventConfig.getEventsListeners().add("event-emitter");
        keycloak.realm("master").updateRealmEventsConfig(eventConfig);
    }


    /**
     * Simulate login, and expect the event emitter to report this event
     */
    @Test
    public void testLoginEventReporting() throws InterruptedException {
        int nbLoginEvents = 0;
        Keycloak keycloak = Keycloak.getInstance(KEYCLOAK_URL, "master", "admin", "admin", CLIENT);

        // test login event
        keycloak.realm("master").users().search(TEST_USER);
        // wait for the event to be reported
        Thread.sleep(1000);
        String jsonAsString = handler.toString();
        Gson g = new Gson();
        Event e = g.fromJson(jsonAsString, Event.class);
        if (e.getType() == EventType.LOGIN) {
            nbLoginEvents++;
        }
        Assert.assertEquals(1, nbLoginEvents);
    }

    /**
     * Simulate logout, and expect the event emitter to report this event
     */
    @Test
    public void testLogoutEventReporting() {
        loginPage.open();
        loginPage.login("test.user", "password");
        EventRepresentation event = pollEvent();
        assertThat(event.getType(), is(EventType.LOGIN.toString()));

        oauth.openLogout();
        event = pollEvent();
        assertThat(event.getType(), is(EventType.LOGOUT.toString()));
        assertThat(event.getUserId(), is(not(nullValue())));
        assertThat(event.getDetails().get(Details.USERNAME), is("test.user"));
        assertThat(pollEvent(), is(nullValue()));
    }


    /**
     * Simulate user creation and check that the username of the user created appears
     * in the event, as well as the username of the agent creating the user
     */
    @Test
    public void testCreateUserEventReporting() throws InterruptedException {
        UserRepresentation user = new UserRepresentation();
        user.setUsername("test_user_creation");
        Response rsp = keycloak.realm("test").users().create(user);
        String loc = rsp.getHeaderString("Location");
        String createdUserId = loc.substring(loc.lastIndexOf("/")+1);

        assertThat(rsp.getStatus(), is(201));

        // wait for the event to be reported
        Thread.sleep(1000);
        String jsonAsString = handler.toString();
        Gson g = new Gson();
        ExtendedAdminEvent e = g.fromJson(jsonAsString, ExtendedAdminEvent.class);
        assertThat(e.getAuthDetails().getUsername(), is("admin"));
        assertThat(e.getDetails().get("user_id"), is(createdUserId));
        assertThat(e.getDetails().get("username"), is("test_user_creation"));
    }
}