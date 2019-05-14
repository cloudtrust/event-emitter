package io.cloudtrust.keycloak.eventemitter;

import io.cloudtrust.keycloak.snowflake.IdGenerator;
import org.apache.http.Header;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HttpContext;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;


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

    private final static int HTTP_OK = 200;

    private CloseableHttpClient httpClient;
    private HttpClientContext httpContext;
    private IdGenerator idGenerator;
    private ConcurrentEvictingQueue<IdentifiedEvent> pendingEvents;
    private ConcurrentEvictingQueue<IdentifiedAdminEvent> pendingAdminEvents;
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
                         ConcurrentEvictingQueue<IdentifiedEvent> pendingEvents,
                         ConcurrentEvictingQueue<IdentifiedAdminEvent> pendingAdminEvents){
        logger.debug("EventEmitterProvider contructor call");
        this.httpClient = httpClient;
        this.httpContext = HttpClientContext.create();
        this.idGenerator = idGenerator;
        this.targetUri = targetUri;
        this.format = format;
        this.pendingEvents = pendingEvents;
        this.pendingAdminEvents = pendingAdminEvents;

        // Secret token
        secretToken = System.getenv(KEYCLOAK_BRIDGE_SECRET_TOKEN);
        if (secretToken == null) {
            logger.error("Cannot find the environment variable '" + KEYCLOAK_BRIDGE_SECRET_TOKEN + "'");
            throw new IllegalStateException("Cannot find the environment variable '" + KEYCLOAK_BRIDGE_SECRET_TOKEN + "'");
        }

        // Hostname
        username = System.getenv(HOSTNAME);
        if (username == null) {
            logger.error("Cannot find the environment variable '" + HOSTNAME + "'");
            throw new IllegalStateException("Cannot find the environment variable '" + HOSTNAME + "'");
        }
    }

    public void onEvent(Event event) {
        logger.debug("EventEmitterProvider onEvent call for Event");
        logger.debugf("Pending Events to send stored in buffer: %d", pendingEvents.size());
        long uid = idGenerator.nextValidId();
        IdentifiedEvent identifiedEvent = new IdentifiedEvent(uid, event);
        pendingEvents.offer(identifiedEvent);

        sendEvents();
    }


    public void onEvent(AdminEvent adminEvent, boolean b) {
        logger.debug("EventEmitterProvider onEvent call for AdminEvent");
        logger.debugf("Pending AdminEvents to send stored in buffer: %d", pendingAdminEvents.size());
        long uid = idGenerator.nextValidId();
        IdentifiedAdminEvent identifiedAdminEvent = new IdentifiedAdminEvent(uid, adminEvent);
        pendingAdminEvents.offer(identifiedAdminEvent);

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


    private void sendEventsWithJsonFormat() {
        int pendingEventsSize = pendingEvents.size();
        for (int i=0; i < pendingEventsSize; i++){
            IdentifiedEvent event = pendingEvents.poll();

            try {
                String json = SerialisationUtils.toJson(event);
                sendJson(json);
            } catch (EventEmitterException | IOException e) {
                pendingEvents.offer(event);
                logger.infof("Failed to send event(ID=%s), try again later.", event.getUid());
                logger.debug("Failed to serialize or send event", e);
            }
        }

        int pendingAdminEventsSize = pendingAdminEvents.size();
        for (int i=0; i < pendingAdminEventsSize; i++){
            IdentifiedAdminEvent event = pendingAdminEvents.poll();

            try {
                String json = SerialisationUtils.toJson(event);
                sendJson(json);
            } catch (EventEmitterException | IOException e) {
                pendingAdminEvents.offer(event);
                logger.infof("Failed to send adminEvent(ID=%s), try again later.", event.getUid());
                logger.debug("Failed to serialize or send adminEvent", e);
            }
        }
    }


    private void sendEventsWithFlatbufferFormat() {
        int pendingEventsSize = pendingEvents.size();
        for (int i=0; i < pendingEventsSize; i++){
            IdentifiedEvent event = pendingEvents.poll();

            try {
                ByteBuffer buffer = SerialisationUtils.toFlat(event);
                sendBytes(buffer, Event.class.getSimpleName());
            } catch (EventEmitterException | IOException e) {
                pendingEvents.offer(event);
                logger.infof("Failed to send event(ID=%s), try again later.", event.getUid());
                logger.debug("Failed to serialize or send event", e);
            }
        }

        int pendingAdminEventsSize = pendingAdminEvents.size();
        for (int i=0; i < pendingAdminEventsSize; i++){
            IdentifiedAdminEvent event = pendingAdminEvents.poll();

            try {
                ByteBuffer buffer = SerialisationUtils.toFlat(event);
                sendBytes(buffer, AdminEvent.class.getSimpleName());
            } catch (EventEmitterException | IOException e) {
                pendingAdminEvents.offer(event);
                logger.infof("Failed to send adminEvent(ID=%s), try again later.", event.getUid());
                logger.debug("Failed to serialize or send adminEvent", e);
            }
        }
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
        HttpContext c = HttpClientContext.create();
        CloseableHttpResponse response = httpClient.execute(httpPost, httpContext);

        try {
            int status = response.getStatusLine().getStatusCode();

            if (status != HTTP_OK) {
                logger.errorv("Sending failure (Server response:{0})", status);
                throw new EventEmitterException("Target server failure.");
            }
        }finally{
            response.close();
        }
    }

}
