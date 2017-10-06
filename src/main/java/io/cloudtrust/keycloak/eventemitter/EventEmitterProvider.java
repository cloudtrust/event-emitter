package io.cloudtrust.keycloak.eventemitter;

import io.cloudtrust.keycloak.snowflake.IdGenerator;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;

import java.io.IOException;
import java.nio.ByteBuffer;


/**
 * EventEmitterProvider.
 * Provider which emit a serialized version of the event to the targetUri.
 * Serialization format can either be flatbuffer or json according to constructor parameter.
 */
public class EventEmitterProvider implements EventListenerProvider{

    private static final Logger logger = Logger.getLogger(EventEmitterProviderFactory.class);

    private final static int HTTP_OK = 200;

    private HttpClient httpClient;
    private IdGenerator idGenerator;
    private ConcurrentEvictingQueue<IdentifiedEvent> pendingEvents;
    private ConcurrentEvictingQueue<IdentifiedAdminEvent> pendingAdminEvents;
    private String targetUri;
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
    EventEmitterProvider(HttpClient httpClient, IdGenerator idGenerator,
                                String targetUri, SerialisationFormat format,
                         ConcurrentEvictingQueue<IdentifiedEvent> pendingEvents,
                         ConcurrentEvictingQueue<IdentifiedAdminEvent> pendingAdminEvents){
        logger.debug("EventEmitterProvider contructor call");
        this.httpClient = httpClient;
        this.idGenerator = idGenerator;
        this.targetUri = targetUri;
        this.format = format;
        this.pendingEvents = pendingEvents;
        this.pendingAdminEvents = pendingAdminEvents;
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
            }
        }
    }


    private void sendEventsWithFlatbufferFormat() {
        int pendingEventsSize = pendingEvents.size();
        for (int i=0; i < pendingEventsSize; i++){
            IdentifiedEvent event = pendingEvents.poll();

            try {
                ByteBuffer buffer = SerialisationUtils.toFlat(event);
                sendBytes(buffer);
            } catch (EventEmitterException | IOException e) {
                pendingEvents.offer(event);
                logger.infof("Failed to send event(ID=%s), try again later.", event.getUid());
            }
        }

        int pendingAdminEventsSize = pendingAdminEvents.size();
        for (int i=0; i < pendingAdminEventsSize; i++){
            IdentifiedAdminEvent event = pendingAdminEvents.poll();

            try {
                ByteBuffer buffer = SerialisationUtils.toFlat(event);
                sendBytes(buffer);
            } catch (EventEmitterException | IOException e) {
                pendingAdminEvents.offer(event);
                logger.infof("Failed to send adminEvent(ID=%s), try again later.", event.getUid());
            }
        }
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
