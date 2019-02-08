package io.cloudtrust.keycloak.eventemitter;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.io.IOUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.*;
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
public class MessageGenerationItTest {

    protected static final Logger logger = Logger.getLogger(MessageGenerationItTest.class);

    private static final String MODULE_NAME_WAR = "event-emitter.war";
    private static final String CLIENT = "admin-cli";
    private static final String TEST_USER = "user-test-event-emitter";

    private static HttpServer restTestServer;

    private static int nbLoginEvents = 0;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @BeforeClass
    public static void initRealmAndUsers() throws Exception {
        // Start REST endpoint
        startRestServer();

        // setup event listener
        Keycloak keycloak = Keycloak.getInstance(TestsHelper.keycloakBaseUrl, "master", "admin", "admin", CLIENT);
        RealmEventsConfigRepresentation eventConfig = keycloak.realm("master").getRealmEventsConfig();
        eventConfig.getEventsListeners().add("event-emitter");
        keycloak.realm("master").updateRealmEventsConfig(eventConfig);
    }

    @AfterClass
    public static void resetRealm(){
        // Stop REST endpoint
        stopRestServer();
    }

    @Deployment
    public static WebArchive deploy() {
        return ShrinkWrap.create(WebArchive.class, MODULE_NAME_WAR)
                .addClasses(
                        EventEmitterProvider.class,
                        EventEmitterProviderFactory.class)
                .addAsManifestResource(new File("src/test/resources", "manifest.xml"))
                .addAsServiceProvider(EventEmitterProviderFactory.class);
    }

    /**
     * Simulate login, and expect the event emitter to report this event
     */
    @Test
    public void testLoginEventReporting () throws Exception {
        Keycloak keycloak = Keycloak.getInstance(TestsHelper.keycloakBaseUrl, "master", "admin", "admin", CLIENT);

        // test login event
        List<UserRepresentation> user=keycloak.realm("master").users().search(TEST_USER);
        // wait for the event to be reported
        Thread.sleep(1000);
        Assert.assertEquals(1, nbLoginEvents);
    }

    private static void startRestServer() throws Exception {
        // embedded web server config must match the event-emitter config in keycloak-server.json
        restTestServer = HttpServer.create(new InetSocketAddress(8888), 0);
        restTestServer.createContext("/event/receiver", new RestHandler());
        restTestServer.setExecutor(null); // creates a default executor
        restTestServer.start();
    }

    private static void stopRestServer() {
        restTestServer.stop(0);
    }

    static class RestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {

            String jsonAsString = IOUtils.toString(t.getRequestBody(), "UTF-8");
            Gson g = new Gson();
            Event e = g.fromJson(jsonAsString, Event.class);
            switch (e.getType()) {
                case LOGIN:
                    nbLoginEvents++;
            }

            t.sendResponseHeaders(200,0);
        }
    }

}