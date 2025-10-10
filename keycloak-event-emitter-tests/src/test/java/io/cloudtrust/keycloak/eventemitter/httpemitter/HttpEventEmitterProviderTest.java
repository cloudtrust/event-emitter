package io.cloudtrust.keycloak.eventemitter.httpemitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.stefanbirkner.systemlambda.Statement;
import com.github.stefanbirkner.systemlambda.SystemLambda;
import io.cloudtrust.keycloak.eventemitter.Container;
import io.cloudtrust.keycloak.eventemitter.customevent.ExtendedAdminEvent;
import io.cloudtrust.keycloak.eventemitter.customevent.IdentifiedEvent;
import io.cloudtrust.keycloak.eventemitter.snowflake.IdGenerator;
import io.cloudtrust.keycloak.test.http.HttpServerManager;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.StatusCodes;
import org.apache.commons.io.IOUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.crypto.Algorithm;
import org.keycloak.crypto.AsymmetricSignatureVerifierContext;
import org.keycloak.crypto.KeyUse;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.events.admin.OperationType;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.models.KeyManager;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.jpa.RealmAdapter;
import org.keycloak.models.jpa.UserAdapter;
import org.keycloak.models.jpa.entities.RealmEntity;
import org.keycloak.models.jpa.entities.UserEntity;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.xnio.streams.ChannelInputStream;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.LinkedBlockingQueue;

import static org.mockito.Mockito.mock;

class HttpEventEmitterProviderTest {
    private static final int LISTEN_PORT = 9994;
    private static final String TARGET = "http://localhost:" + LISTEN_PORT + "/test";
    private static final int BUFFER_CAPACITY = 3;
    private static final String IDP_ID = "test-idp";
    private static final String TID_REALM = "distant-realm";

    private static final String username = "toto";
    private static final String password = "passwordverylongandhardtoguess";
    private static final String userId = "394b0730-628f-11ec-9211-0242ac120005";

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private KeycloakSession keycloakSession;

    protected void runWithEnvironments(Statement stmt) throws Exception {
        SystemLambda.withEnvironmentVariable(HttpEventEmitterProvider.KEYCLOAK_BRIDGE_SECRET_TOKEN, password)
                .and(HttpEventEmitterProvider.HOSTNAME, username)
                .execute(stmt);
    }

    protected void runWithHttpHandler(HttpHandler handler, Statement stmt) {
        try {
            HttpServerManager.getDefault().startHttpServer(LISTEN_PORT, handler);
            this.runWithEnvironments(stmt);
        } catch (Exception e) {
            Assertions.fail(e);
        } finally {
            HttpServerManager.getDefault().stop(LISTEN_PORT);
        }
    }

    @BeforeEach
    public void initMock() throws NoSuchAlgorithmException {
        MockitoAnnotations.openMocks(this);
        // user
        UserEntity userEntity = new UserEntity();
        userEntity.setUsername("test-user");
        UserModel user = new UserAdapter(null, null, null, userEntity);
        Mockito.when(keycloakSession.users().getUserById(Mockito.any(), Mockito.any())).thenReturn(user);

        // Realm
        RealmEntity realmEntity = new RealmEntity();
        realmEntity.setId("realmId");
        RealmModel realm = new RealmAdapter(null, null, realmEntity);
        Mockito.when(keycloakSession.realms().getRealm(Mockito.any())).thenReturn(realm);

        // Signature keys
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        KeyWrapper keyWrapper = mock(KeyWrapper.class);
        Mockito.when(keyWrapper.getPrivateKey()).thenReturn(keyPair.getPrivate());
        Mockito.when(keyWrapper.getPublicKey()).thenReturn(keyPair.getPublic());
        Mockito.when(keyWrapper.getAlgorithmOrDefault()).thenReturn(Algorithm.RS256);

        KeyManager keyManager = mock(KeyManager.class);
        Mockito.when(keyManager.getActiveKey(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(keyWrapper);
        Mockito.when(keycloakSession.keys()).thenReturn(keyManager);
    }

    private HttpEventEmitterContext createEmitterContext() {
        IdGenerator idGenerator = new IdGenerator(1, 1);
        LinkedBlockingQueue<IdentifiedEvent> pendingEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
        LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);

        HttpEventEmitterContext emitterContext = Mockito.mock(HttpEventEmitterContext.class);
        Mockito.when(emitterContext.getTrustIDRealm()).thenReturn(TID_REALM);
        Mockito.when(emitterContext.getTargetUri()).thenReturn(TARGET);
        Mockito.when(emitterContext.getIdpId()).thenReturn(IDP_ID);
        Mockito.when(emitterContext.getIdGenerator()).thenReturn(idGenerator);
        Mockito.when(emitterContext.getPendingEvents()).thenReturn(pendingEvents);
        Mockito.when(emitterContext.getPendingAdminEvents()).thenReturn(pendingAdminEvents);
        return emitterContext;
    }

    @Test
    void testFlatbufferFormatOutput() {
        final HttpJsonReceiverHandler handler = new HttpJsonReceiverHandler();
        runWithHttpHandler(handler, () -> {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpEventEmitterContext emitterCfg = createEmitterContext();
            HttpEventEmitterProvider HttpEventEmitterProvider = new HttpEventEmitterProvider(keycloakSession, httpClient, emitterCfg);

            Event event = createEvent();
            HttpEventEmitterProvider.onEvent(event);

            httpClient.close();

            ObjectMapper mapper = new ObjectMapper();
            Container container = mapper.readValue(handler.getResponse(), Container.class);

            byte[] b = Base64.getDecoder().decode(container.obj());
            flatbuffers.events.Event receivedEvent = flatbuffers.events.Event.getRootAsEvent(ByteBuffer.wrap(b));
            Assertions.assertEquals(event.getTime(), receivedEvent.time());
            Assertions.assertEquals(event.getType().ordinal(), receivedEvent.type());
            Assertions.assertEquals(event.getClientId(), receivedEvent.clientId());
            Assertions.assertEquals(handler.getHeader("IDP-ID"), IDP_ID);
            Assertions.assertEquals(handler.getHeader("TID-Realm"), TID_REALM);
        });
    }

    @Test
    void testAdminEventFlatbufferFormatOutput() {
        final HttpJsonReceiverHandler handler = new HttpJsonReceiverHandler();
        runWithHttpHandler(handler, () -> {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpEventEmitterContext emitterContext = createEmitterContext();
            HttpEventEmitterProvider HttpEventEmitterProvider = new HttpEventEmitterProvider(keycloakSession, httpClient, emitterContext);

            AdminEvent event = createAdminEvent();
            HttpEventEmitterProvider.onEvent(event, true);

            httpClient.close();

            ObjectMapper mapper = new ObjectMapper();
            Container container = mapper.readValue(handler.getResponse(), Container.class);

            byte[] b = Base64.getDecoder().decode(container.obj());
            flatbuffers.events.AdminEvent receivedEvent = flatbuffers.events.AdminEvent.getRootAsAdminEvent(ByteBuffer.wrap(b));
            Assertions.assertEquals(event.getTime(), receivedEvent.time());
            Assertions.assertEquals(event.getOperationType().ordinal(), receivedEvent.operationType());
            Assertions.assertEquals(event.getAuthDetails().getUserId(), receivedEvent.authDetails().userId());
            Assertions.assertEquals(handler.getHeader("IDP-ID"), IDP_ID);
            Assertions.assertEquals(handler.getHeader("TID-Realm"), TID_REALM);
        });
    }

    @Test
    void testNoConnection() throws Exception {
        runWithEnvironments(() -> {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpEventEmitterContext emitterContext = createEmitterContext();
                HttpEventEmitterProvider HttpEventEmitterProvider = new HttpEventEmitterProvider(keycloakSession, httpClient, emitterContext);

                Assertions.assertEquals(0, emitterContext.getPendingEvents().size());

                Event event = createEvent();
                HttpEventEmitterProvider.onEvent(event);

                Assertions.assertEquals(1, emitterContext.getPendingEvents().size());

                Event event2 = createEvent();
                HttpEventEmitterProvider.onEvent(event2);

                Assertions.assertEquals(2, emitterContext.getPendingEvents().size());
            }
        });
    }

    @Test
    void testServerError() {
        runWithHttpHandler(
                ex -> ex.setStatusCode(StatusCodes.MULTIPLE_CHOICES),
                () -> {
                    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                        HttpEventEmitterContext emitterContext = createEmitterContext();
                        HttpEventEmitterProvider HttpEventEmitterProvider = new HttpEventEmitterProvider(keycloakSession, httpClient, emitterContext);

                        Assertions.assertEquals(0, emitterContext.getPendingEvents().size());

                        Event event = createEvent();
                        HttpEventEmitterProvider.onEvent(event);

                        Assertions.assertEquals(1, emitterContext.getPendingEvents().size());

                        Event event2 = createEvent();
                        HttpEventEmitterProvider.onEvent(event2);

                        Assertions.assertEquals(2, emitterContext.getPendingEvents().size());
                    }
                });
    }

    @Test
    void testBufferAndSend() throws Exception {
        runWithEnvironments(() -> {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpEventEmitterContext emitterContext = createEmitterContext();
            HttpEventEmitterProvider HttpEventEmitterProvider = new HttpEventEmitterProvider(keycloakSession, httpClient, emitterContext);

            Assertions.assertEquals(0, emitterContext.getPendingEvents().size());

            Event event = createEvent();
            HttpEventEmitterProvider.onEvent(event);

            Assertions.assertEquals(1, emitterContext.getPendingEvents().size());

            Event event2 = createEvent();
            HttpEventEmitterProvider.onEvent(event2);

            Assertions.assertEquals(2, emitterContext.getPendingEvents().size());

            try {
                HttpJsonReceiverHandler handler = new HttpJsonReceiverHandler();
                HttpServerManager.getDefault().startHttpServer(LISTEN_PORT, handler);

                Event event3 = createEvent();
                HttpEventEmitterProvider.onEvent(event3);

                httpClient.close();

                Assertions.assertEquals(0, emitterContext.getPendingEvents().size());
                Assertions.assertEquals(3, handler.getCounter());
            } finally {
                HttpServerManager.getDefault().stop(LISTEN_PORT);
            }
        });
    }

    @Test
    void testSignature() {
        final HttpJsonReceiverHandler handler = new HttpJsonReceiverHandler();
        runWithHttpHandler(handler, () -> {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpEventEmitterContext emitterContext = createEmitterContext();
            HttpEventEmitterProvider HttpEventEmitterProvider = new HttpEventEmitterProvider(keycloakSession, httpClient, emitterContext);

            AdminEvent event = createAdminEvent();
            HttpEventEmitterProvider.onEvent(event, true);

            httpClient.close();

            // Verify signature with realm public key
            KeyWrapper keyWrapper = keycloakSession.keys().getActiveKey(keycloakSession.getContext().getRealm(),
                    KeyUse.SIG, Algorithm.EdDSA);

            String detachedSignature = handler.getHeader("JWS-Signature");
            String fullJws = injectPayload(detachedSignature, handler.getResponse());
            try {
                JWSInput input = new JWSInput(fullJws);
                byte[] data = input.getEncodedSignatureInput().getBytes(StandardCharsets.UTF_8);
                Assertions.assertTrue(new AsymmetricSignatureVerifierContext(keyWrapper).verify(data, input.getSignature()));
            } catch (Exception e) {
                e.printStackTrace();
                Assertions.fail();
            }
        });
    }

    private String injectPayload(String detachedSignature, String payload) {
        String responseB64 = Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8)).replace("=", "");
        int firstDot = detachedSignature.indexOf("..") + 1;
        return detachedSignature.substring(0, firstDot) + responseB64 + detachedSignature.substring(firstDot);
    }

    static class HttpJsonReceiverHandler implements HttpHandler {
        HeaderMap headers;
        String jsonReceived;
        int counter = 0;

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            exchange.setStatusCode(StatusCodes.OK);
            ChannelInputStream cis = new ChannelInputStream(exchange.getRequestChannel());
            headers = exchange.getRequestHeaders();
            jsonReceived = IOUtils.toString(cis, StandardCharsets.UTF_8);
            counter++;
        }

        String getResponse() {
            return jsonReceived;
        }

        String getHeader(String headerName) {
            return headers.get(headerName).getFirst();
        }

        int getCounter() {
            return counter;
        }
    }

    private Event createEvent() {
        Event event = new Event();
        event.setTime(120001);
        event.setType(EventType.CLIENT_LOGIN);
        return event;
    }

    private AdminEvent createAdminEvent() {
        AdminEvent event = new AdminEvent();
        event.setTime(120001);
        event.setOperationType(OperationType.CREATE);
        AuthDetails authDetails = new AuthDetails();
        authDetails.setUserId(userId);
        event.setAuthDetails(authDetails);
        return event;
    }
}
