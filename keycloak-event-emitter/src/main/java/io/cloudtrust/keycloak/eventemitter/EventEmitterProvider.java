package io.cloudtrust.keycloak.eventemitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import io.cloudtrust.keycloak.customevent.ExtendedAdminEvent;
import io.cloudtrust.keycloak.customevent.ExtendedAuthDetails;
import io.cloudtrust.keycloak.customevent.IdentifiedAdminEvent;
import io.cloudtrust.keycloak.customevent.IdentifiedEvent;
import io.cloudtrust.keycloak.snowflake.IdGenerator;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.jboss.logging.Logger;
import org.keycloak.events.Details;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * EventEmitterProvider.
 * Provider which emit a serialized version of the event to the targetUri.
 * Serialization format can either be flatbuffer or json according to constructor parameter.
 */
public class EventEmitterProvider implements EventListenerProvider {
    private static final Logger logger = Logger.getLogger(EventEmitterProvider.class);

    public static final String KEYCLOAK_BRIDGE_SECRET_TOKEN = "CT_KEYCLOAK_BRIDGE_SECRET_TOKEN";
    public static final String HOSTNAME = "HOSTNAME";

    private static final String BASIC = "Basic";
    private static final String AUTHORIZATION = "Authorization";

    private static final int HTTP_OK = 200;

    interface EventSender<T extends HasUid> {
        void send(T event) throws EventEmitterException, IOException;
    }

    private KeycloakSession keycloakSession;
    private CloseableHttpClient httpClient;
    private HttpClientContext httpContext;
    private IdGenerator idGenerator;
    private LinkedBlockingQueue<IdentifiedEvent> pendingEvents;
    private LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEvents;
    private String targetUri;
    private String username;
    private String secretToken;
    private SerialisationFormat format;
    private RequestConfig requestConfig;

    /**
     * Constructor.
     *
     * @param keycloakSession    session context
     * @param httpClient         to send serialized events to target server
     * @param idGenerator        for unique event ID genration
     * @param targetUri          where serialized events are sent
     * @param format             of the serialized events
     * @param pendingEvents      to send due to failure during previous trial
     * @param pendingAdminEvents to send due to failure during previous trial
     * @param requestConfig      configuration used for HTTP calls
     */
    EventEmitterProvider(KeycloakSession keycloakSession,
                         CloseableHttpClient httpClient, IdGenerator idGenerator,
                         String targetUri, SerialisationFormat format,
                         LinkedBlockingQueue<IdentifiedEvent> pendingEvents,
                         LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEvents,
                         RequestConfig requestConfig) {
        logger.debug("EventEmitterProvider constructor call");
        this.keycloakSession = keycloakSession;
        this.httpClient = httpClient;
        this.httpContext = HttpClientContext.create();
        this.idGenerator = idGenerator;
        this.targetUri = targetUri;
        this.format = format;
        this.pendingEvents = pendingEvents;
        this.pendingAdminEvents = pendingAdminEvents;
        this.requestConfig = requestConfig;

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
        completeEventAttributes(event);
        logger.debugf("Pending Events to send stored in buffer: %d", pendingEvents.size());
        long uid = idGenerator.nextValidId();
        IdentifiedEvent identifiedEvent = new IdentifiedEvent(uid, event);

        while (!pendingEvents.offer(identifiedEvent)) {
            Event skippedEvent = pendingEvents.poll();
            if (skippedEvent != null) {
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
        ExtendedAdminEvent customAdminEvent = completeAdminEventAttributes(identifiedAdminEvent);

        while (!pendingAdminEvents.offer(customAdminEvent)) {
            AdminEvent skippedAdminEvent = pendingAdminEvents.poll();
            if (skippedAdminEvent != null) {
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
            case JSON:
                sendEventsWithJsonFormat();
                break;
            default:
                logger.infov("Unknown format type (%s), JSON will be used", format.name());
                sendEventsWithJsonFormat();
        }
    }


    private <T extends HasUid> void sendEvents(LinkedBlockingQueue<T> queue, EventSender<T> eventProcessor) {
        int pendingEventsSize = queue.size();
        for (int i = 0; i < pendingEventsSize; i++) {
            T event = queue.poll();

            if (event == null) {
                return;
            }

            try {
                eventProcessor.send(event);
            } catch (EventEmitterException | IOException e) {
                String repushed = queue.offer(event) ? "success" : "failure";
                logger.infof("Failed to send %s(ID=%s), try again later. Re-push: %s", event.getClass().getName(), event.getUid(), repushed);
                logger.debug("Failed to serialize or send event", e);
                return;
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
        httpPost.setConfig(requestConfig);

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

    private void completeEventAttributes(Event event) {
        // add username if missing
        if (event.getDetails() == null) {
            event.setDetails(new HashMap<>());
        }
        String eventUsername = event.getDetails().get(Details.USERNAME);
        if (!Strings.isNullOrEmpty(event.getUserId()) && Strings.isNullOrEmpty(eventUsername)) {
            RealmModel realm = keycloakSession.realms().getRealm(event.getRealmId());
            // retrieve username from userId
            UserModel user = keycloakSession.users().getUserById(realm, event.getUserId());
            if (user != null) {
                event.getDetails().put(Details.USERNAME, user.getUsername());
            }
        }
    }

    private ExtendedAdminEvent completeAdminEventAttributes(IdentifiedAdminEvent adminEvent) {
        ExtendedAdminEvent extendedAdminEvent = new ExtendedAdminEvent(adminEvent);
        // add always missing agent username
        ExtendedAuthDetails extendedAuthDetails = extendedAdminEvent.getAuthDetails();
        if (!Strings.isNullOrEmpty(extendedAuthDetails.getUserId())) {
            RealmModel realm = keycloakSession.realms().getRealm(extendedAuthDetails.getRealmId());
            UserModel user = keycloakSession.users().getUserById(realm, extendedAuthDetails.getUserId());
            extendedAuthDetails.setUsername(user.getUsername());
        }
        // add username if resource is a user
        String resourcePath = extendedAdminEvent.getResourcePath();
        if (resourcePath != null && resourcePath.startsWith("users")) {
            // parse userID
            String pattern = "^users/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$";
            Pattern r = Pattern.compile(pattern);
            Matcher m = r.matcher(resourcePath);
            if (m.matches()) {
                String userId = m.group(1);
                RealmModel realm = keycloakSession.realms().getRealm(adminEvent.getRealmId());
                // retrieve user
                UserModel user = keycloakSession.users().getUserById(realm, userId);
                extendedAdminEvent.getDetails().put("user_id", userId);
                if (user != null) {
                    extendedAdminEvent.getDetails().put("username", user.getUsername());
                }
            }
        }

        return extendedAdminEvent;
    }
}
