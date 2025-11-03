package io.cloudtrust.keycloak.eventemitter.httpemitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.cloudtrust.keycloak.eventemitter.CompleteEventUtils;
import io.cloudtrust.keycloak.eventemitter.Container;
import io.cloudtrust.keycloak.eventemitter.HasUid;
import io.cloudtrust.keycloak.eventemitter.customevent.ExtendedAdminEvent;
import io.cloudtrust.keycloak.eventemitter.customevent.IdentifiedAdminEvent;
import io.cloudtrust.keycloak.eventemitter.customevent.IdentifiedEvent;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.jboss.logging.Logger;
import org.keycloak.crypto.Algorithm;
import org.keycloak.crypto.AsymmetricSignatureSignerContext;
import org.keycloak.crypto.KeyUse;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static io.cloudtrust.keycloak.eventemitter.SerializationUtils.toFlat;
import static io.cloudtrust.keycloak.eventemitter.SerializationUtils.toJson;

/**
 * Provider which emits a flatbuffer serialized version of the event to the targetUri via HTTP.
 */
public class HttpEventEmitterProvider implements EventListenerProvider {
    private static final Logger logger = Logger.getLogger(HttpEventEmitterProvider.class);

    public static final String KEYCLOAK_BRIDGE_SECRET_TOKEN = "CT_KEYCLOAK_BRIDGE_SECRET_TOKEN";
    public static final String HOSTNAME = "HOSTNAME";

    private static final String IDP_ID = "IDP-ID";
    private static final String TID_REALM = "TID-Realm";
    private static final String JWS_SIGNATURE = "JWS-Signature";

    interface EventSender<T extends HasUid> {
        void send(T event) throws HttpEventEmitterException, IOException;
    }

    private final KeycloakSession keycloakSession;
    private final CloseableHttpClient httpClient;
    private final HttpClientContext httpContext;
    private final HttpEventEmitterContext emitterContext;

    /**
     * Constructor.
     *
     * @param keycloakSession session context
     * @param httpClient      to send serialized events to target server
     * @param emitterContext  emitter context
     */
    HttpEventEmitterProvider(KeycloakSession keycloakSession, CloseableHttpClient httpClient, HttpEventEmitterContext emitterContext) {
        logger.debug("HttpEventEmitterProvider constructor call");
        this.keycloakSession = keycloakSession;
        this.httpClient = httpClient;
        this.httpContext = HttpClientContext.create();
        this.emitterContext = emitterContext;
    }

    public void onEvent(Event event) {
        logger.debug("HttpEventEmitterProvider onEvent call for Event");
        CompleteEventUtils.completeEventAttributes(keycloakSession, event);
        logger.debugf("Pending Events to send stored in buffer: %d", this.emitterContext.getPendingEvents().size());
        long uid = this.emitterContext.getIdGenerator().nextValidId();
        IdentifiedEvent identifiedEvent = new IdentifiedEvent(uid, event);

        while (!this.emitterContext.getPendingEvents().offer(identifiedEvent)) {
            Event skippedEvent = this.emitterContext.getPendingEvents().poll();
            if (skippedEvent != null) {
                String strEvent;
                try {
                    strEvent = toJson(skippedEvent);
                } catch (JsonProcessingException e) {
                    strEvent = "SerializationFailure";
                }
                logger.errorf("Event dropped(%s) due to full queue", strEvent);
            }
        }

        sendEvents();
    }


    public void onEvent(AdminEvent adminEvent, boolean b) {
        logger.debug("HttpEventEmitterProvider onEvent call for AdminEvent");
        logger.debugf("Pending AdminEvents to send stored in buffer: %d", this.emitterContext.getPendingAdminEvents().size());
        long uid = this.emitterContext.getIdGenerator().nextValidId();
        IdentifiedAdminEvent identifiedAdminEvent = new IdentifiedAdminEvent(uid, adminEvent);
        ExtendedAdminEvent customAdminEvent = CompleteEventUtils.completeAdminEventAttributes(keycloakSession, identifiedAdminEvent);

        while (!this.emitterContext.getPendingAdminEvents().offer(customAdminEvent)) {
            AdminEvent skippedAdminEvent = this.emitterContext.getPendingAdminEvents().poll();
            if (skippedAdminEvent != null) {
                String strAdminEvent;
                try {
                    strAdminEvent = toJson(skippedAdminEvent);
                } catch (JsonProcessingException e) {
                    strAdminEvent = "SerializationFailure";
                }
                logger.errorf("AdminEvent dropped(%s) due to full queue", strAdminEvent);
            }
        }

        sendEvents();
    }

    public void close() {
        // Nothing to do
    }

    private void sendEvents() {
        sendEvents(this.emitterContext.getPendingEvents(), e -> sendBytes(toFlat(e), Event.class.getSimpleName()));
        sendEvents(this.emitterContext.getPendingAdminEvents(), e -> sendBytes(toFlat(e), AdminEvent.class.getSimpleName()));
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
            } catch (HttpEventEmitterException | IOException e) {
                String repushed = queue.offer(event) ? "success" : "failure";
                logger.infof("Failed to send %s(ID=%s), try again later. Re-push: %s", event.getClass().getName(),
                        event.getUid(), repushed);
                logger.debug("Failed to serialize or send event", e);
                return;
            }
        }
    }

    private void sendBytes(ByteBuffer buffer, String type) throws IOException, HttpEventEmitterException {
        byte[] b = new byte[buffer.remaining()];
        buffer.get(b);

        String obj = Base64.getEncoder().encodeToString(b);
        Container container = new Container(type, obj);
        String json = toJson(container);

        HttpPost httpPost = new HttpPost(this.emitterContext.getTargetUri());
        httpPost.setConfig(this.emitterContext.getRequestConfig());

        StringEntity stringEntity = new StringEntity(json);
        httpPost.setEntity(stringEntity);
        httpPost.setHeader("Content-type", "application/json");

        List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader(IDP_ID, this.emitterContext.getIdpId()));
        headers.add(new BasicHeader(TID_REALM, this.emitterContext.getTrustIDRealm()));

        // Sign the payload
        try {
            String signature = computeSignature(container);
            headers.add(new BasicHeader(JWS_SIGNATURE, signature));
        } catch (Exception e) {
            logger.error("Impossible to create signature on event", e);
            throw new HttpEventEmitterException(e.getMessage());
        }

        httpPost.setHeaders(headers.toArray(new Header[0]));
        send(httpPost);
    }

    private void send(HttpPost httpPost) throws HttpEventEmitterException, IOException {
        try (CloseableHttpResponse response = httpClient.execute(httpPost, httpContext)) {
            int status = response.getStatusLine().getStatusCode();

            if (!isSuccess(status)) {
                logger.errorv("Sending failure (Server response:{0})", status);
                throw new HttpEventEmitterException("Target server failure.");
            }
        }
    }

    private static boolean isSuccess(int httpStatus) {
        return httpStatus >= 200 && httpStatus < 300;
    }

    /**
     * Produces a JWS signature on data using the signature key of the current realm.
     *
     * @param data the string to sign
     */
    private String computeSignature(Object data) throws HttpEventEmitterException {
        RealmModel realm = keycloakSession.getContext().getRealm();
        KeyWrapper keyWrapper = keycloakSession.keys().getActiveKey(realm, KeyUse.SIG, Algorithm.EdDSA);

        String signature = new JWSBuilder()
                .type("JOSE")
                .x5c(getCertificates(keyWrapper))
                .jsonContent(data)
                .sign(new AsymmetricSignatureSignerContext(keyWrapper));
        // Detach the payload part
        int firstDot = signature.indexOf('.') + 1;
        int secondDot = signature.indexOf('.', firstDot);
        if (secondDot < firstDot) {
            throw new IllegalArgumentException("Failed to compute JOSE signature");
        }
        return signature.substring(0, firstDot) + signature.substring(secondDot);
    }

    private static List<X509Certificate> getCertificates(KeyWrapper keyWrapper) throws HttpEventEmitterException {
        if (keyWrapper.getCertificateChain() != null) {
            return keyWrapper.getCertificateChain();
        } else if (keyWrapper.getCertificate() != null) {
            return Collections.singletonList(keyWrapper.getCertificate());
        }
        throw new HttpEventEmitterException("No available certificate in key " + keyWrapper.getType() + "/" + keyWrapper.getKid());
    }
}