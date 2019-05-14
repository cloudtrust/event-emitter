package io.cloudtrust.keycloak.eventemitter;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudtrust.keycloak.AbstractTest;
import io.cloudtrust.keycloak.snowflake.IdGenerator;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import org.apache.commons.io.IOUtils;
import org.apache.http.*;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import org.keycloak.events.admin.AdminEvent;
import org.xnio.streams.ChannelInputStream;

public class EventEmitterProviderTest {

    private static final int LISTEN_PORT = 9994;
    private static final String TARGET = "http://localhost:"+LISTEN_PORT+"/test";
    private static int BUFFER_CAPACITY = 3;

    private static final String username = "toto";
    private static final String password = "passwordverylongandhardtoguess";

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

    @Test
    public void testFlatbufferFormatOutput() throws IOException, InterruptedException {
        HttpJsonReceiverHandler handler = new HttpJsonReceiverHandler();
        Undertow server = startHttpServer(handler);
        CloseableHttpClient httpClient = HttpClients.createDefault();
        IdGenerator idGenerator = new IdGenerator(1,1);
        ConcurrentEvictingQueue<IdentifiedEvent> pendingEvents = new ConcurrentEvictingQueue<>(BUFFER_CAPACITY);
        ConcurrentEvictingQueue<IdentifiedAdminEvent> pendingAdminEvents = new ConcurrentEvictingQueue<>(BUFFER_CAPACITY);
        EventEmitterProvider eventEmitterProvider = new EventEmitterProvider(httpClient,
                idGenerator, TARGET, SerialisationFormat.FLATBUFFER, pendingEvents,pendingAdminEvents);

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
        HttpJsonReceiverHandler handler =  new HttpJsonReceiverHandler();
        Undertow server = startHttpServer(handler);
        CloseableHttpClient httpClient = HttpClients.createDefault();
        IdGenerator idGenerator = new IdGenerator(1,1);
        ConcurrentEvictingQueue<IdentifiedEvent> pendingEvents = new ConcurrentEvictingQueue<>(BUFFER_CAPACITY);
        ConcurrentEvictingQueue<IdentifiedAdminEvent> pendingAdminEvents = new ConcurrentEvictingQueue<>(BUFFER_CAPACITY);
        EventEmitterProvider eventEmitterProvider = new EventEmitterProvider(httpClient,
                idGenerator, TARGET, SerialisationFormat.JSON, pendingEvents,pendingAdminEvents);

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
        IdGenerator idGenerator = new IdGenerator(1,1);
        ConcurrentEvictingQueue<IdentifiedEvent> pendingEvents = new ConcurrentEvictingQueue<>(BUFFER_CAPACITY);
        ConcurrentEvictingQueue<IdentifiedAdminEvent> pendingAdminEvents = new ConcurrentEvictingQueue<>(BUFFER_CAPACITY);
        EventEmitterProvider eventEmitterProvider = new EventEmitterProvider(httpClient,
                idGenerator, TARGET, SerialisationFormat.JSON, pendingEvents,pendingAdminEvents);

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
        HttpErrorHandler handler =  new HttpErrorHandler();
        Undertow server = startHttpServer(handler);
        CloseableHttpClient httpClient = HttpClients.createDefault();
        IdGenerator idGenerator = new IdGenerator(1,1);
        ConcurrentEvictingQueue<IdentifiedEvent> pendingEvents = new ConcurrentEvictingQueue<>(BUFFER_CAPACITY);
        ConcurrentEvictingQueue<IdentifiedAdminEvent> pendingAdminEvents = new ConcurrentEvictingQueue<>(BUFFER_CAPACITY);
        EventEmitterProvider eventEmitterProvider = new EventEmitterProvider(httpClient,
                idGenerator, TARGET, SerialisationFormat.JSON, pendingEvents,pendingAdminEvents);


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
        HttpJsonReceiverHandler handler =  new HttpJsonReceiverHandler();

        CloseableHttpClient httpClient = HttpClients.createDefault();
        IdGenerator idGenerator = new IdGenerator(1,1);
        ConcurrentEvictingQueue<IdentifiedEvent> pendingEvents = new ConcurrentEvictingQueue<>(BUFFER_CAPACITY);
        ConcurrentEvictingQueue<IdentifiedAdminEvent> pendingAdminEvents = new ConcurrentEvictingQueue<>(BUFFER_CAPACITY);
        EventEmitterProvider eventEmitterProvider = new EventEmitterProvider(httpClient,
                idGenerator, TARGET, SerialisationFormat.JSON, pendingEvents,pendingAdminEvents);

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

        String getResponse(){
            return jsonReceived;
        }

        int getCounter(){
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


    private Event createEvent(){
        Event event = new Event();
        event.setTime(120001);
        event.setType(EventType.CLIENT_LOGIN);
        return event;
    }

    private AdminEvent createAdminEvent(){
        AdminEvent adminEvent = new AdminEvent();
        adminEvent.setTime(120000);
        return adminEvent;
    }



}
