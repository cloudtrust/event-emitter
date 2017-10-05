package io.cloudtrust.keycloak.module.eventemitter;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudtrust.keycloak.snowflake.IdGenerator;
import org.apache.http.*;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.keycloak.events.admin.AdminEvent;

public class EventEmitterProviderTest {

    private static final int LISTEN_PORT = 9994;
    private static final String TARGET = "http://localhost:"+LISTEN_PORT+"/test";
    private static int BUFFER_CAPACITY = 3;


    @Test
    public void testFlatbufferFormatOutput() throws IOException, InterruptedException {
        HttpFlatbufferReceiverHandler handler = new HttpFlatbufferReceiverHandler();
        HttpServer server = startHttpServer(handler);
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
        server.shutdown(2, TimeUnit.SECONDS);


        flatbuffers.events.Event receivedEvent = flatbuffers.events.Event.getRootAsEvent(handler.getResponse());
        Assert.assertEquals(event.getTime(), receivedEvent.time());
        Assert.assertEquals(event.getType().ordinal(), receivedEvent.type());
        Assert.assertEquals(event.getClientId(), receivedEvent.clientId());

    }

    @Test
    public void testJsonFormatOutput() throws IOException, InterruptedException {
        HttpJsonReceiverHandler handler =  new HttpJsonReceiverHandler();
        HttpServer server = startHttpServer(handler);
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
        server.shutdown(2, TimeUnit.SECONDS);

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
        HttpServer server = startHttpServer(handler);
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
        server.shutdown(2, TimeUnit.SECONDS);
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

        HttpServer server = startHttpServer(handler);

        Event event3 = createEvent();
        eventEmitterProvider.onEvent(event3);

        httpClient.close();
        server.stop();
        server.shutdown(2, TimeUnit.SECONDS);

        Assert.assertEquals(0, pendingEvents.size());
        Assert.assertEquals(3, handler.getCounter());
    }

    private HttpServer startHttpServer(HttpRequestHandler handler) throws IOException {
        HttpServer server = ServerBootstrap.bootstrap()
                .setListenerPort(LISTEN_PORT)
                .setServerInfo("Test/1.1")
                .registerHandler("*", handler)
                .create();

        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                server.shutdown(5, TimeUnit.SECONDS);
            }
        });

        return server;
    }


    class HttpFlatbufferReceiverHandler implements HttpRequestHandler  {
        ByteBuffer byteBuffer;
        int counter = 0;

        HttpFlatbufferReceiverHandler() {
            super();
        }

        @Override
        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {

            System.out.println("Received");

            if (request instanceof HttpEntityEnclosingRequest) {
                HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
                byte[] entityContent = EntityUtils.toByteArray(entity);
                byteBuffer = ByteBuffer.wrap(entityContent);
            }
        }

        ByteBuffer getResponse(){
            return byteBuffer;
        }

        int getCounter(){
            return counter;
        }

    }

    class HttpJsonReceiverHandler implements HttpRequestHandler  {
        String jsonReceived;
        int counter = 0;

        HttpJsonReceiverHandler() {
            super();
        }

        @Override
        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {

            System.out.println("Received");

            if (request instanceof HttpEntityEnclosingRequest) {
                HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
                jsonReceived = EntityUtils.toString(entity, "UTF-8");
                counter++;
            }
        }

        String getResponse(){
            return jsonReceived;
        }

        int getCounter(){
            return counter;
        }
    }

    class HttpErrorHandler implements HttpRequestHandler  {

        HttpErrorHandler() {
            super();
        }

        @Override
        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {

            response.setStatusCode(300);
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
