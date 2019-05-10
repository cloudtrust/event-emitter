package io.cloudtrust.keycloak.eventemitter;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.cloudtrust.keycloak.AbstractTest;
import org.apache.commons.io.IOUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.events.Event;
import org.keycloak.representations.idm.RealmEventsConfigRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.test.TestsHelper;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

@RunWith(Arquillian.class)
@RunAsClient
public class MessageGenerationItTest extends AbstractTest {

    protected static final Logger logger = Logger.getLogger(MessageGenerationItTest.class);

    private static final String CLIENT = "admin-cli";
    private static final String TEST_USER = "user-test-event-emitter";

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @BeforeClass
    public static void initRealmAndUsers() throws Exception {
        // setup event listener
        Keycloak keycloak = Keycloak.getInstance(TestsHelper.keycloakBaseUrl, "master", "admin", "admin", CLIENT);
        RealmEventsConfigRepresentation eventConfig = keycloak.realm("master").getRealmEventsConfig();
        eventConfig.getEventsListeners().add("event-emitter");
        keycloak.realm("master").updateRealmEventsConfig(eventConfig);
    }


    /**
     * Simulate login, and expect the event emitter to report this event
     */
    @Test
    public void testLoginEventReporting () throws Exception {
        int nbLoginEvents = 0;
        Keycloak keycloak = Keycloak.getInstance(TestsHelper.keycloakBaseUrl, "master", "admin", "admin", CLIENT);

        // test login event
        List<UserRepresentation> user=keycloak.realm("master").users().search(TEST_USER);
        // wait for the event to be reported
        Thread.sleep(1000);
        String jsonAsString = handler.toString();
        Gson g = new Gson();
        Event e = g.fromJson(jsonAsString, Event.class);
        switch (e.getType()) {
            case LOGIN:
                nbLoginEvents++;
        }
        Assert.assertEquals(1, nbLoginEvents);
    }

}