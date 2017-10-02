package io.cloudtrust.keycloak.module.eventemitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.flatbuffers.FlatBufferBuilder;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.nio.ByteBuffer;


/**
 * EventEmitterProvider.
 * Provider which emit a serialized version of the event to the targetUri.
 * Serialization format can either be flatbuffer or json according to constructor parameter.

 */
public class EventEmitterProvider implements EventListenerProvider{

    private static final Logger logger = Logger.getLogger(EventEmitterProviderFactory.class);

    private final static int HTTP_OK = 200;

    private FlatBufferBuilder builder = new FlatBufferBuilder(1024);

    HttpClient httpClient;

    CircularFifo<Event> eventsBuffer;
    CircularFifo<AdminEvent> adminEventsBuffer;


    String targetUri;
    SerialisationFormat format;

    /**
     * Constructor.
     *
     * @param httpClient to send serialized evetns to target server
     * @param targetUri where serialized events are sent
     * @param format of the serialized events
     * @param bufferCapacity maximum events stored in buffer in case of failure to send to target
     */
    public EventEmitterProvider(HttpClient httpClient, String targetUri, SerialisationFormat format, int bufferCapacity){
        this.httpClient = httpClient;
        this.targetUri = targetUri;
        this.format = format;
        eventsBuffer = new CircularFifo<>(bufferCapacity);
        adminEventsBuffer = new CircularFifo<>(bufferCapacity);
    }

    public void onEvent(Event event) {
        try {
            switch (format) {
                case FLATBUFFER:
                    String json = SerialisationUtils.toJson(event);
                    sendJson(json);
                    break;
                case JSON:
                    ByteBuffer buffer = SerialisationUtils.toFlat(event);
                    sendBytes(buffer);
                    break;
            }
        }catch(IOException | EventEmitterException e){
            logger.info("Failed to send event to target", e);
        }
    }

    public void onEvent(AdminEvent adminEvent, boolean b) {
        try {
            switch (format) {
                case FLATBUFFER:
                    String json = SerialisationUtils.toJson(adminEvent);
                    sendJson(json);
                    break;
                case JSON:
                    ByteBuffer buffer = SerialisationUtils.toFlat(adminEvent);
                    sendBytes(buffer);
                    break;
            }
        }catch(IOException | EventEmitterException e){
            logger.info("Failed to send event to target", e);
        }
    }

    public void close() {

    }

    private void sendBytes(ByteBuffer buffer) throws IOException, EventEmitterException {
        byte[] b = new byte[buffer.remaining()];
        buffer.get(b);

        ByteArrayEntity entityByteArray = new ByteArrayEntity(b);

        HttpPost httpPost = new HttpPost(targetUri);
        httpPost.setEntity(entityByteArray);
        httpPost.setHeader("Content-type", "application/octet-stream");

        send(httpPost);
    }

    private void sendJson(String json) throws IOException, EventEmitterException {
        HttpPost httpPost = new HttpPost(targetUri);
        StringEntity stringEntity = new StringEntity(json);
        httpPost.setEntity(stringEntity);
        httpPost.setHeader("Content-type", "application/json");

        send(httpPost);
    }

    private void send(HttpPost httpPost) throws EventEmitterException, IOException {
        HttpResponse response = httpClient.execute(httpPost);

        int status = response.getStatusLine().getStatusCode();

        if (status != HTTP_OK) {
            logger.errorv("Sending failure (Server response:%s)", status);
            throw new EventEmitterException("Target server failure.");
        }
    }

}
