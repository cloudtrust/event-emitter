package iocloudtrust.keycloak.module.eventemitter;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudtrust.keycloak.module.eventemitter.EventEmitterProvider;
import io.cloudtrust.keycloak.module.eventemitter.SerialisationFormat;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

public class EventEmitterProviderTest {

    private static final String targetUri = "http://localhost:9999/test";


    public void testToFlatEvent(){
        EventEmitterProvider eventEmitterProvider = new EventEmitterProvider(
                null,null, SerialisationFormat.FLATBUFFER, 10);
    }

    @Test
    public void flatbuffer(){

        final HttpServer server = ServerBootstrap.bootstrap()
                .setListenerPort(9999)
                .setServerInfo("Test/1.1")
                .registerHandler("*", new HttpReceiverHandler())
                .create();

        try {
            server.start();
        } catch(IOException e){
            e.printStackTrace();
        }

        HttpClient httpClient = HttpClients.createDefault();
        EventEmitterProvider eventEmitterProvider = new EventEmitterProvider(httpClient, targetUri, SerialisationFormat.FLATBUFFER, 10);

        Event event = new Event();
        event.setTime(120000);
        event.setType(EventType.CLIENT_LOGIN);
        event.setClientId("clientId");

        eventEmitterProvider.onEvent(event);

    }



    static class HttpReceiverHandler implements HttpRequestHandler  {

        public HttpReceiverHandler() {
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
                System.out.println("Incoming entity content (bytes): " + entityContent.length);
                flatbuffers.events.Event event = flatbuffers.events.Event.getRootAsEvent(ByteBuffer.wrap(entityContent));
                System.out.println(event.time());
                System.out.println(event.type());
                System.out.println(event.clientId());
            }


        }

    }

    @Test
    public void testJson(){
        ObjectMapper mapper = new ObjectMapper();
        Event event = new Event();
        event.setTime(120000);
        event.setType(EventType.CLIENT_LOGIN);
        event.setClientId("clientId");


        //Object to JSON in String
        String jsonInString = null;
        try {
            jsonInString = mapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        System.out.println(jsonInString);
    }


}
