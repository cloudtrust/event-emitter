package io.cloudtrust.keycloak.eventemitter;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudtrust.keycloak.customevent.ExtendedAdminEvent;
import io.cloudtrust.keycloak.customevent.IdentifiedEvent;
import io.cloudtrust.keycloak.snowflake.IdGenerator;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import org.apache.commons.io.IOUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.runner.RunWith;
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
import org.mockito.junit.MockitoJUnitRunner;
import org.xnio.streams.ChannelInputStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.LinkedBlockingQueue;

@RunWith(MockitoJUnitRunner.class)
public class EventEmitterProviderTest {

    private static final int LISTEN_PORT = 9994;
    private static final String TARGET = "http://localhost:" + LISTEN_PORT + "/test";
    private static int BUFFER_CAPACITY = 3;

    private static final String username = "toto";
    private static final String password = "passwordverylongandhardtoguess";

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private KeycloakSession keycloakSession;

    @ClassRule
    public static final EnvironmentVariables envVariables = new EnvironmentVariables();

    @BeforeClass
    public static void intEnv() throws IOException {
        envVariables.set(EventEmitterProvider.KEYCLOAK_BRIDGE_SECRET_TOKEN, password);
        envVariables.set(EventEmitterProvider.HOSTNAME, username);
    }

    protected static Undertow startHttpServer(HttpHandler handler) {
        Undertow server = Undertow.builder()
                .addHttpListener(LISTEN_PORT, "0.0.0.0", handler)
                .build();
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop()));
        return server;
    }

    @Before
    public void initMock() {
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
    }

    @Test
    public void testFlatbufferFormatOutput() throws IOException {
        HttpJsonReceiverHandler handler = new HttpJsonReceiverHandler();
        Undertow server = startHttpServer(handler);
        CloseableHttpClient httpClient = HttpClients.createDefault();
        IdGenerator idGenerator = new IdGenerator(1, 1);
        LinkedBlockingQueue<IdentifiedEvent> pendingEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
        LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
        EventEmitterProvider eventEmitterProvider = new EventEmitterProvider(keycloakSession, httpClient,
                idGenerator, TARGET, SerialisationFormat.FLATBUFFER, pendingEvents, pendingAdminEvents);

        Event event = createEvent();
        eventEmitterProvider.onEvent(event);

        httpClient.close();
        server.stop();

        ObjectMapper mapper = new ObjectMapper();
        Container container = mapper.readValue(handler.getResponse(), Container.class);

        Assert.assertEquals("Event", container.getType());

        byte[] b = Base64.getDecoder().decode(container.getObj());
        flatbuffers.events.Event receivedEvent = flatbuffers.events.Event.getRootAsEvent(ByteBuffer.wrap(b));
        Assert.assertEquals(event.getTime(), receivedEvent.time());
        Assert.assertEquals(event.getType().ordinal(), receivedEvent.type());
        Assert.assertEquals(event.getClientId(), receivedEvent.clientId());

    }

    @Test
    public void testJsonFormatOutput() throws IOException, InterruptedException {
        HttpJsonReceiverHandler handler = new HttpJsonReceiverHandler();
        Undertow server = startHttpServer(handler);
        CloseableHttpClient httpClient = HttpClients.createDefault();
        IdGenerator idGenerator = new IdGenerator(1, 1);
        LinkedBlockingQueue<IdentifiedEvent> pendingEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
        LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
        EventEmitterProvider eventEmitterProvider = new EventEmitterProvider(keycloakSession, httpClient,
                idGenerator, TARGET, SerialisationFormat.JSON, pendingEvents, pendingAdminEvents);

        Event event = createEvent();
        eventEmitterProvider.onEvent(event);

        httpClient.close();
        server.stop();

        ObjectMapper mapper = new ObjectMapper();
        IdentifiedEvent receivedIdentifiedEvent = mapper.readValue(handler.getResponse(), IdentifiedEvent.class);

        Assert.assertEquals(event.getTime(), receivedIdentifiedEvent.getTime());
        Assert.assertEquals(event.getType(), receivedIdentifiedEvent.getType());
        Assert.assertEquals(event.getClientId(), receivedIdentifiedEvent.getClientId());
    }

    @Test
    public void testNoConnection() throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        IdGenerator idGenerator = new IdGenerator(1, 1);
        LinkedBlockingQueue<IdentifiedEvent> pendingEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
        LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
        EventEmitterProvider eventEmitterProvider = new EventEmitterProvider(keycloakSession, httpClient,
                idGenerator, TARGET, SerialisationFormat.JSON, pendingEvents, pendingAdminEvents);

        Assert.assertEquals(0, pendingEvents.size());

        Event event = createEvent();
        eventEmitterProvider.onEvent(event);

        Assert.assertEquals(1, pendingEvents.size());

        Event event2 = createEvent();
        eventEmitterProvider.onEvent(event2);

        Assert.assertEquals(2, pendingEvents.size());
        httpClient.close();

    }


    @Test
    public void testServerError() throws IOException, InterruptedException {
        HttpErrorHandler handler = new HttpErrorHandler();
        Undertow server = startHttpServer(handler);
        CloseableHttpClient httpClient = HttpClients.createDefault();
        IdGenerator idGenerator = new IdGenerator(1, 1);
        LinkedBlockingQueue<IdentifiedEvent> pendingEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
        LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
        EventEmitterProvider eventEmitterProvider = new EventEmitterProvider(keycloakSession, httpClient,
                idGenerator, TARGET, SerialisationFormat.JSON, pendingEvents, pendingAdminEvents);


        Assert.assertEquals(0, pendingEvents.size());

        Event event = createEvent();
        eventEmitterProvider.onEvent(event);

        Assert.assertEquals(1, pendingEvents.size());

        Event event2 = createEvent();
        eventEmitterProvider.onEvent(event2);

        Assert.assertEquals(2, pendingEvents.size());
        httpClient.close();
        server.stop();
    }


    @Test
    public void testBufferAndSend() throws IOException, InterruptedException {
        HttpJsonReceiverHandler handler = new HttpJsonReceiverHandler();

        CloseableHttpClient httpClient = HttpClients.createDefault();
        IdGenerator idGenerator = new IdGenerator(1, 1);
        LinkedBlockingQueue<IdentifiedEvent> pendingEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
        LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
        EventEmitterProvider eventEmitterProvider = new EventEmitterProvider(keycloakSession, httpClient,
                idGenerator, TARGET, SerialisationFormat.JSON, pendingEvents, pendingAdminEvents);

        Assert.assertEquals(0, pendingEvents.size());

        Event event = createEvent();
        eventEmitterProvider.onEvent(event);

        Assert.assertEquals(1, pendingEvents.size());

        Event event2 = createEvent();
        eventEmitterProvider.onEvent(event2);

        Assert.assertEquals(2, pendingEvents.size());

        Undertow server = startHttpServer(handler);

        Event event3 = createEvent();
        eventEmitterProvider.onEvent(event3);

        httpClient.close();
        server.stop();

        Assert.assertEquals(0, pendingEvents.size());
        Assert.assertEquals(3, handler.getCounter());
    }


    class HttpJsonReceiverHandler implements HttpHandler {
        String jsonReceived;
        int counter = 0;

        HttpJsonReceiverHandler() {
            super();
        }

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

    class HttpErrorHandler implements HttpHandler {

        HttpErrorHandler() {
            super();
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
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
