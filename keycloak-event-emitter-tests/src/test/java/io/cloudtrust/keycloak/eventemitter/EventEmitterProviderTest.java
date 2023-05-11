package io.cloudtrust.keycloak.eventemitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.stefanbirkner.systemlambda.Statement;
import com.github.stefanbirkner.systemlambda.SystemLambda;

import io.cloudtrust.keycloak.customevent.ExtendedAdminEvent;
import io.cloudtrust.keycloak.customevent.IdentifiedEvent;
import io.cloudtrust.keycloak.snowflake.IdGenerator;
import io.cloudtrust.keycloak.test.AbstractInKeycloakTest;
import io.cloudtrust.keycloak.test.container.KeycloakDeploy;
import io.cloudtrust.keycloak.test.http.HttpServerManager;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import org.apache.commons.io.IOUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
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
import java.util.Base64;
import java.util.concurrent.LinkedBlockingQueue;

@ExtendWith(KeycloakDeploy.class)
class EventEmitterProviderTest extends AbstractInKeycloakTest {
    private static final int LISTEN_PORT = 9994;
    private static final String TARGET = "http://localhost:" + LISTEN_PORT + "/test";
    private static final int BUFFER_CAPACITY = 3;

    private static final String username = "toto";
    private static final String password = "passwordverylongandhardtoguess";

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private KeycloakSession keycloakSession;

    protected void runWithEnvironments(Statement stmt) throws Exception {
        SystemLambda.withEnvironmentVariable(EventEmitterProvider.KEYCLOAK_BRIDGE_SECRET_TOKEN, password)
                .and(EventEmitterProvider.HOSTNAME, username)
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
    public void initMock() {
        MockitoAnnotations.openMocks(this);
        // user
        UserEntity userEntity = new UserEntity();
        userEntity.setUsername("test-user");
        UserModel user = new UserAdapter(null, null, null, userEntity);
        Mockito.when(keycloakSession.users().getUserById((RealmModel) Mockito.any(), Mockito.any())).thenReturn(user);

        // Realm
        RealmEntity realmEntity = new RealmEntity();
        realmEntity.setId("realmId");
        RealmModel realm = new RealmAdapter(null, null, realmEntity);
        Mockito.when(keycloakSession.realms().getRealm(Mockito.any())).thenReturn(realm);
    }

    @Test
    void testFlatbufferFormatOutput() {
        final HttpJsonReceiverHandler handler = new HttpJsonReceiverHandler();
        runWithHttpHandler(handler, () -> {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            IdGenerator idGenerator = new IdGenerator(1, 1);
            LinkedBlockingQueue<IdentifiedEvent> pendingEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
            LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
            EventEmitterProvider eventEmitterProvider = new EventEmitterProvider(keycloakSession, httpClient,
                    idGenerator, TARGET, SerialisationFormat.FLATBUFFER, pendingEvents, pendingAdminEvents, null);

            Event event = createEvent();
            eventEmitterProvider.onEvent(event);

            httpClient.close();

            ObjectMapper mapper = new ObjectMapper();
            Container container = mapper.readValue(handler.getResponse(), Container.class);

            Assertions.assertEquals("Event", container.getType());

            byte[] b = Base64.getDecoder().decode(container.getObj());
            flatbuffers.events.Event receivedEvent = flatbuffers.events.Event.getRootAsEvent(ByteBuffer.wrap(b));
            Assertions.assertEquals(event.getTime(), receivedEvent.time());
            Assertions.assertEquals(event.getType().ordinal(), receivedEvent.type());
            Assertions.assertEquals(event.getClientId(), receivedEvent.clientId());
        });
    }

    @Test
    void testJsonFormatOutput() {
        HttpJsonReceiverHandler handler = new HttpJsonReceiverHandler();
        this.runWithHttpHandler(handler, () -> {
            HttpServerManager.getDefault().startHttpServer(LISTEN_PORT, handler);

            CloseableHttpClient httpClient = HttpClients.createDefault();
            IdGenerator idGenerator = new IdGenerator(1, 1);
            LinkedBlockingQueue<IdentifiedEvent> pendingEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
            LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
            EventEmitterProvider eventEmitterProvider = new EventEmitterProvider(keycloakSession, httpClient,
                    idGenerator, TARGET, SerialisationFormat.JSON, pendingEvents, pendingAdminEvents, null);

            Event event = createEvent();
            eventEmitterProvider.onEvent(event);

            httpClient.close();

            IdentifiedEvent receivedIdentifiedEvent = mapper.readValue(handler.getResponse(), IdentifiedEvent.class);

            Assertions.assertEquals(event.getTime(), receivedIdentifiedEvent.getTime());
            Assertions.assertEquals(event.getType(), receivedIdentifiedEvent.getType());
            Assertions.assertEquals(event.getClientId(), receivedIdentifiedEvent.getClientId());
        });
    }

    @Test
    void testNoConnection() throws Exception {
    	this.runWithEnvironments(() -> {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                IdGenerator idGenerator = new IdGenerator(1, 1);
                LinkedBlockingQueue<IdentifiedEvent> pendingEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
                LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
                EventEmitterProvider eventEmitterProvider = new EventEmitterProvider(keycloakSession, httpClient,
                        idGenerator, TARGET, SerialisationFormat.JSON, pendingEvents, pendingAdminEvents, null);

                Assertions.assertEquals(0, pendingEvents.size());

                Event event = createEvent();
                eventEmitterProvider.onEvent(event);

                Assertions.assertEquals(1, pendingEvents.size());

                Event event2 = createEvent();
                eventEmitterProvider.onEvent(event2);

                Assertions.assertEquals(2, pendingEvents.size());
            }
    	});
    }

    @Test
    void testServerError() {
        final HttpErrorHandler handler = new HttpErrorHandler();
        this.runWithHttpHandler(handler, () -> {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                IdGenerator idGenerator = new IdGenerator(1, 1);
                LinkedBlockingQueue<IdentifiedEvent> pendingEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
                LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
                EventEmitterProvider eventEmitterProvider = new EventEmitterProvider(keycloakSession, httpClient,
                        idGenerator, TARGET, SerialisationFormat.JSON, pendingEvents, pendingAdminEvents, null);

                Assertions.assertEquals(0, pendingEvents.size());

                Event event = createEvent();
                eventEmitterProvider.onEvent(event);

                Assertions.assertEquals(1, pendingEvents.size());

                Event event2 = createEvent();
                eventEmitterProvider.onEvent(event2);

                Assertions.assertEquals(2, pendingEvents.size());
            }
        });
    }

    @Test
    void testBufferAndSend() throws Exception {
    	runWithEnvironments(() -> {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            IdGenerator idGenerator = new IdGenerator(1, 1);
            LinkedBlockingQueue<IdentifiedEvent> pendingEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
            LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
            EventEmitterProvider eventEmitterProvider = new EventEmitterProvider(keycloakSession, httpClient,
                    idGenerator, TARGET, SerialisationFormat.JSON, pendingEvents, pendingAdminEvents, null);

            Assertions.assertEquals(0, pendingEvents.size());

            Event event = createEvent();
            eventEmitterProvider.onEvent(event);

            Assertions.assertEquals(1, pendingEvents.size());

            Event event2 = createEvent();
            eventEmitterProvider.onEvent(event2);

            Assertions.assertEquals(2, pendingEvents.size());

            try {
                HttpJsonReceiverHandler handler = new HttpJsonReceiverHandler();
                HttpServerManager.getDefault().startHttpServer(LISTEN_PORT, handler);

                Event event3 = createEvent();
                eventEmitterProvider.onEvent(event3);

                httpClient.close();

                Assertions.assertEquals(0, pendingEvents.size());
                Assertions.assertEquals(3, handler.getCounter());
            } finally {
                HttpServerManager.getDefault().stop(LISTEN_PORT);
            }
    	});
    }

    static class HttpJsonReceiverHandler implements HttpHandler {
        String jsonReceived;
        int counter = 0;

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            exchange.setStatusCode(StatusCodes.OK);
            ChannelInputStream cis = new ChannelInputStream(exchange.getRequestChannel());
            jsonReceived = IOUtils.toString(cis, StandardCharsets.UTF_8);
            counter++;
        }

        String getResponse() {
            return jsonReceived;
        }

        int getCounter() {
            return counter;
        }
    }

    static class HttpErrorHandler implements HttpHandler {
        @Override
        public void handleRequest(HttpServerExchange exchange) {
            exchange.setStatusCode(StatusCodes.MULTIPLE_CHOICES);
        }
    }

    private Event createEvent() {
        Event event = new Event();
        event.setTime(120001);
        event.setType(EventType.CLIENT_LOGIN);
        return event;
    }
}
