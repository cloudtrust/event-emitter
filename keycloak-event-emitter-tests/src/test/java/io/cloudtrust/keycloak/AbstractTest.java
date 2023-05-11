package io.cloudtrust.keycloak;

import io.cloudtrust.keycloak.eventemitter.EventEmitterProviderFactory;
import io.cloudtrust.keycloak.test.AbstractInKeycloakTest;
import io.cloudtrust.keycloak.test.http.HttpServerManager;
import io.cloudtrust.keycloak.test.pages.LoginPage;
import io.cloudtrust.keycloak.test.pages.WebPage;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import org.apache.commons.io.IOUtils;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.xnio.streams.ChannelInputStream;

import javax.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedList;

public abstract class AbstractTest extends AbstractInKeycloakTest {
    private static final Logger logger = Logger.getLogger(AbstractTest.class);
    protected static final int LISTEN_PORT = 9999;

    private static final String username = "toto";
    private static final String password = "passwordverylongandhardtoguess";

    protected RealmResource testRealm;
    protected String token;

    @WebPage
    protected LoginPage loginPage;

    @BeforeAll
    public static void initServer() {
        HttpServerManager.getDefault().startHttpServer(LISTEN_PORT, handler);
    }

    @AfterAll
    public static void stopServer() {
        HttpServerManager.getDefault().stop();
    }

    @BeforeEach
    public void setup() throws Exception {
        this.token = this.getKeycloakAdminClient().tokenManager().getAccessTokenString();
        this.createRealm("/testrealm.json", this::setupTestRealm);
        this.testRealm = this.getRealm();

        this.injectComponents();
        this.events().activate("test");
    }

    @AfterEach
    public void deleteTestRealm() {
        testRealm.remove();
    }

    @AfterEach
    public void clearEventQueue() {
        this.events().clear();
    }

    protected static HttpHandler handler = new HttpHandler() {
        private String jsonReceived;

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            String basicToken = exchange.getRequestHeaders().get("Authorization").element();
            String[] subParts = basicToken.split("Basic ");
            Assertions.assertEquals(2, subParts.length);
            String decodedToken = new String(Base64.getDecoder().decode(subParts[1]));

            if (!(username + ":" + password).equals(decodedToken)) {
                logger.warnf("Event service received %s when expecting %s:%s", decodedToken, username, password);
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

    private void setupTestRealm(RealmResource testRealm) {
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
                Assertions.assertEquals(201, createUser.getStatus());
            }
        }
    }

}
