package io.cloudtrust.keycloak;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudtrust.keycloak.eventemitter.EventEmitterProvider;
import io.cloudtrust.keycloak.eventemitter.EventEmitterProviderFactory;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import org.apache.commons.io.IOUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.graphene.page.Page;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.EventRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.test.FluentTestsHelper;
import org.keycloak.testsuite.client.KeycloakTestingClient;
import org.keycloak.testsuite.pages.LoginPage;
import org.keycloak.testsuite.util.OAuthClient;
import org.openqa.selenium.WebDriver;
import org.xnio.streams.ChannelInputStream;

import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedList;

public abstract class AbstractTest {

    private KeycloakTestingClient testingClient = KeycloakTestingClient.getInstance(KEYCLOAK_URL);

    protected static Undertow server;
    protected static final int LISTEN_PORT = 9999;
    protected static final String KEYCLOAK_URL = getKeycloakUrl();
    private static final String MODULE_NAME_WAR = "event-emitter.war";

    private static final String username = "toto";
    private static final String password = "passwordverylongandhardtoguess";

    protected RealmResource testRealm;
    protected Keycloak keycloak;
    protected String token;

    @Drone
    protected WebDriver driver;

    @Page
    protected LoginPage loginPage;

    @ArquillianResource
    protected OAuthClient oauth;

    @ClassRule
    public static final EnvironmentVariables envVariables = new EnvironmentVariables();

    private static String getKeycloakUrl() {
        String url = FluentTestsHelper.DEFAULT_KEYCLOAK_URL;
        try {
            URI uri = new URI(FluentTestsHelper.DEFAULT_KEYCLOAK_URL);
            url = url.replace(String.valueOf(uri.getPort()), System.getProperty("auth.server.http.port", "8080"));
        } catch (Exception e) {
            // Ignore
        }
        return url;
    }

    @BeforeClass
    public static void initServer() throws IOException {
        if (server == null) {
            server = startHttpServer(handler);
        }
    }

    @BeforeClass
    public static void intEnv() throws IOException {
        envVariables.set(EventEmitterProvider.KEYCLOAK_BRIDGE_SECRET_TOKEN, password);
        envVariables.set(EventEmitterProvider.HOSTNAME, username);
    }

    @Before
    public void createTestRealm() throws Exception {
        oauth.init(driver);
        keycloak = Keycloak.getInstance(KEYCLOAK_URL, "master", "admin", "admin", "admin-cli");
        token = keycloak.tokenManager().getAccessTokenString();
        testRealm = importTestRealm(keycloak);
        setupTestRealm();
    }

    @After
    public void deleteTestRealm() {
        testRealm.remove();
    }

    @After
    public void clearEventQueue() {
        while (pollEvent() != null) ;
    }

    protected EventRepresentation pollEvent() {
        return testingClient.testing().pollEvent();
    }

    private RealmResource importTestRealm(Keycloak keycloak) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        RealmRepresentation realmRepresentation = mapper.readValue(
                getClass().getResourceAsStream("/testrealm.json"), RealmRepresentation.class);
        keycloak.realms().create(realmRepresentation);
        return keycloak.realm("test");
    }

    protected static HttpHandler handler = new HttpHandler() {
        private String jsonReceived;

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            String basicToken = exchange.getRequestHeaders().get("Authorization").element();
            String[] subParts = basicToken.split("Basic ");
            Assert.assertEquals(2, subParts.length);
            String decodedToken = new String(Base64.getDecoder().decode(subParts[1]));

            if (!(username + ":" + password).equals(decodedToken)) {
                exchange.setStatusCode(StatusCodes.FORBIDDEN);
                return;
            }

            exchange.setStatusCode(StatusCodes.OK);
            ChannelInputStream cis = new ChannelInputStream(exchange.getRequestChannel());
            jsonReceived = IOUtils.toString(cis, StandardCharsets.UTF_8);
        }

        public String toString() {
            return jsonReceived;
        }
    };


    protected static Undertow startHttpServer(HttpHandler handler) {
        Undertow server = Undertow.builder()
                .addHttpListener(LISTEN_PORT, "0.0.0.0", handler)
                .build();
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop()));
        return server;
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

    private void setupTestRealm() {
        RealmRepresentation realm = testRealm.toRepresentation();
        realm.getEventsListeners().add(EventEmitterProviderFactory.PROVIDER_ID);
        testRealm.update(realm);

        CredentialRepresentation credentialPass = new CredentialRepresentation();
        credentialPass.setType(CredentialRepresentation.PASSWORD);
        credentialPass.setValue("password");

        {
            UserRepresentation testUser = new UserRepresentation();
            testUser.setUsername("test.user");
            testUser.setEmail("test.user@test.com");
            testUser.setEnabled(true);
            if (testUser.getCredentials() == null) {
                testUser.setCredentials(new LinkedList<>());
            }
            testUser.getCredentials().add(credentialPass);
            try (Response createUser = testRealm.users().create(testUser)) {
                Assert.assertEquals(201, createUser.getStatus());
            }
        }
    }

}
