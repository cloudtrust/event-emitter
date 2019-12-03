package io.cloudtrust.keycloak.eventemitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.cloudtrust.keycloak.snowflake.IdGenerator;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * EventEmitterProvider.
 * Provider which emit a serialized version of the event to the targetUri.
 * Serialization format can either be flatbuffer or json according to constructor parameter.
 */
public class EventEmitterProvider implements EventListenerProvider{

    private static final Logger logger = Logger.getLogger(EventEmitterProvider.class);

    public static final String KEYCLOAK_BRIDGE_SECRET_TOKEN = "CT_KEYCLOAK_BRIDGE_SECRET_TOKEN";
    public static final String HOSTNAME = "HOSTNAME";

    private static final String BASIC = "Basic";
    private static final String AUTHORIZATION = "Authorization";

    private static final int HTTP_OK = 200;

    interface EventSender<T extends HasUid> {
        void send(T event) throws EventEmitterException, IOException;
    }

    private CloseableHttpClient httpClient;
    private HttpClientContext httpContext;
    private IdGenerator idGenerator;
    private LinkedBlockingQueue<IdentifiedEvent> pendingEvents;
    private LinkedBlockingQueue<IdentifiedAdminEvent> pendingAdminEvents;
    private String targetUri;
    private String username;
    private String secretToken;
    private SerialisationFormat format;

    /**
     * Constructor.
     *
     * @param httpClient to send serialized events to target server
     * @param idGenerator for unique event ID genration
     * @param targetUri where serialized events are sent
     * @param format of the serialized events
     * @param pendingEvents to send due to failure during previous trial
     * @param pendingAdminEvents to send due to failure during previous trial
     */
    EventEmitterProvider(CloseableHttpClient httpClient, IdGenerator idGenerator,
                                String targetUri, SerialisationFormat format,
                         LinkedBlockingQueue<IdentifiedEvent> pendingEvents,
                         LinkedBlockingQueue<IdentifiedAdminEvent> pendingAdminEvents){
        logger.debug("EventEmitterProvider contructor call");
        this.httpClient = httpClient;
        this.httpContext = HttpClientContext.create();
        this.idGenerator = idGenerator;
        this.targetUri = targetUri;
        this.format = format;
        this.pendingEvents = pendingEvents;
        this.pendingAdminEvents = pendingAdminEvents;

        // Secret token
        secretToken = checkEnv(KEYCLOAK_BRIDGE_SECRET_TOKEN);
        // Hostname
        username = checkEnv(HOSTNAME);
    }

    private String checkEnv(String key) {
        String env = System.getenv(key);
        if (env != null) {
            return env;
        }
        String message = "Cannot find the environment variable '" + key + "'";
        logger.error(message);
        throw new IllegalStateException(message);
    }

    public void onEvent(Event event) {
        logger.debug("EventEmitterProvider onEvent call for Event");
        logger.debugf("Pending Events to send stored in buffer: %d", pendingEvents.size());
        long uid = idGenerator.nextValidId();
        IdentifiedEvent identifiedEvent = new IdentifiedEvent(uid, event);

        while(!pendingEvents.offer(identifiedEvent)){
            Event skippedEvent = pendingEvents.poll();
            if(skippedEvent != null) {
                String strEvent = null;
                try {
                    strEvent = SerialisationUtils.toJson(skippedEvent);
                } catch (JsonProcessingException e) {
                    strEvent = "SerializationFailure";
                }
                logger.errorf("Event dropped(%s) due to full queue", strEvent);
            }
        }

        sendEvents();
    }


    public void onEvent(AdminEvent adminEvent, boolean b) {
        logger.debug("EventEmitterProvider onEvent call for AdminEvent");
        logger.debugf("Pending AdminEvents to send stored in buffer: %d", pendingAdminEvents.size());
        long uid = idGenerator.nextValidId();
        IdentifiedAdminEvent identifiedAdminEvent = new IdentifiedAdminEvent(uid, adminEvent);

        while(!pendingAdminEvents.offer(identifiedAdminEvent)){
            AdminEvent skippedAdminEvent = pendingAdminEvents.poll();
            if(skippedAdminEvent != null) {
                String strAdminEvent = null;
                try {
                    strAdminEvent = SerialisationUtils.toJson(skippedAdminEvent);
                } catch (JsonProcessingException e) {
                    strAdminEvent = "SerializationFailure";
                }
                logger.errorf("AdminEvent dropped(%s) due to full queue", strAdminEvent);
            }
        }

        sendEvents();
    }

    public void close() {
        //Nothing to do
    }

    private void sendEvents() {
        switch (format) {
            case FLATBUFFER:
                sendEventsWithFlatbufferFormat();
                break;
            default:
                logger.infov("Unknown format type (%s), JSON will be used", format.name());
            case JSON:
                sendEventsWithJsonFormat();
                break;
        }
    }


    private <T extends HasUid> void sendEvents(LinkedBlockingQueue<T> queue, EventSender<T> eventProcessor) {
        int pendingEventsSize = queue.size();
        for (int i=0; i < pendingEventsSize; i++){
            T event = queue.poll();

            if(event == null){
                break;
            }

            try {
                eventProcessor.send(event);
            } catch (EventEmitterException | IOException e) {
                String repushed = queue.offer(event) ? "success" : "failure";
                logger.infof("Failed to send %s(ID=%s), try again later. Re-push: %s", event.getClass().getName(), event.getUid(), repushed);
                logger.debug("Failed to serialize or send event", e);
                break;
            }
        }
    }

    private void sendEventsWithJsonFormat() {
        sendEvents(pendingEvents, e -> sendJson(SerialisationUtils.toJson(e)));
        sendEvents(pendingAdminEvents, e -> sendJson(SerialisationUtils.toJson(e)));
    }

    private void sendEventsWithFlatbufferFormat() {
        sendEvents(pendingEvents, e -> sendBytes(SerialisationUtils.toFlat(e), Event.class.getSimpleName()));
        sendEvents(pendingAdminEvents, e -> sendBytes(SerialisationUtils.toFlat(e), AdminEvent.class.getSimpleName()));
    }

    private void sendBytes(ByteBuffer buffer, String type) throws IOException, EventEmitterException {
        byte[] b = new byte[buffer.remaining()];
        buffer.get(b);

        String obj = Base64.getEncoder().encodeToString(b);
        Container c = new Container(type, obj);
        String json = SerialisationUtils.toJson(c);
        sendJson(json);
    }

    private void sendJson(String json) throws IOException, EventEmitterException {
        HttpPost httpPost = new HttpPost(targetUri);
        StringEntity stringEntity = new StringEntity(json);
        httpPost.setEntity(stringEntity);
        httpPost.setHeader("Content-type", "application/json");

        String token = username + ":" + secretToken;
        String b64Token = Base64.getEncoder().encodeToString(token.getBytes());
        httpPost.setHeader(AUTHORIZATION, BASIC + " " + b64Token);

        send(httpPost);
    }

    private void send(HttpPost httpPost) throws EventEmitterException, IOException {
        try (CloseableHttpResponse response = httpClient.execute(httpPost, httpContext)) {
            int status = response.getStatusLine().getStatusCode();

            if (status != HTTP_OK) {
                logger.errorv("Sending failure (Server response:{0})", status);
                throw new EventEmitterException("Target server failure.");
            }
        }
    }

}
