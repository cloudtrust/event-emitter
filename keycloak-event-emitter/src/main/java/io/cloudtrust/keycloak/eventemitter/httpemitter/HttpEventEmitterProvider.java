package io.cloudtrust.keycloak.eventemitter.httpemitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import io.cloudtrust.keycloak.eventemitter.Container;
import io.cloudtrust.keycloak.eventemitter.HasUid;
import io.cloudtrust.keycloak.eventemitter.customevent.ExtendedAdminEvent;
import io.cloudtrust.keycloak.eventemitter.customevent.ExtendedAuthDetails;
import io.cloudtrust.keycloak.eventemitter.customevent.IdentifiedAdminEvent;
import io.cloudtrust.keycloak.eventemitter.customevent.IdentifiedEvent;
import io.cloudtrust.keycloak.eventemitter.snowflake.IdGenerator;
import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.jboss.logging.Logger;
import org.keycloak.crypto.Algorithm;
import org.keycloak.crypto.KeyUse;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.events.Details;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.cloudtrust.keycloak.eventemitter.SerializationUtils.toFlat;
import static io.cloudtrust.keycloak.eventemitter.SerializationUtils.toJson;

/**
 * Provider which emits a flatbuffer serialized version of the event to the targetUri via HTTP.
 */
public class HttpEventEmitterProvider implements EventListenerProvider {
    private static final Logger logger = Logger.getLogger(HttpEventEmitterProvider.class);

    public static final String KEYCLOAK_BRIDGE_SECRET_TOKEN = "CT_KEYCLOAK_BRIDGE_SECRET_TOKEN";
    public static final String HOSTNAME = "HOSTNAME";

    private static final String BASIC = "Basic";
    private static final String AUTHORIZATION = "Authorization";
    private static final String AGW_ID = "AGW-ID";
    private static final String PKCS7_SIGNATURE = "PKCS7-Signature";

    private static final int HTTP_OK = 200;

    interface EventSender<T extends HasUid> {
        void send(T event) throws HttpEventEmitterException, IOException;
    }

    private final KeycloakSession keycloakSession;
    private final CloseableHttpClient httpClient;
    private final HttpClientContext httpContext;
    private final IdGenerator idGenerator;
    private final LinkedBlockingQueue<IdentifiedEvent> pendingEvents;
    private final LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEvents;
    private final String targetUri;
    private final String username;
    private final String secretToken;
    private final RequestConfig requestConfig;
    private final String agwId;

    /**
     * Constructor.
     *
     * @param keycloakSession    session context
     * @param httpClient         to send serialized events to target server
     * @param idGenerator        for unique event ID genration
     * @param targetUri          where serialized events are sent
     * @param pendingEvents      to send due to failure during previous trial
     * @param pendingAdminEvents to send due to failure during previous trial
     * @param requestConfig      configuration used for HTTP calls
     */
    HttpEventEmitterProvider(KeycloakSession keycloakSession, CloseableHttpClient httpClient, IdGenerator idGenerator,
                             String targetUri, LinkedBlockingQueue<IdentifiedEvent> pendingEvents,
                             LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEvents, RequestConfig requestConfig,
                             String agwId) {
        logger.debug("HttpEventEmitterProvider constructor call");
        this.keycloakSession = keycloakSession;
        this.httpClient = httpClient;
        this.httpContext = HttpClientContext.create();
        this.idGenerator = idGenerator;
        this.targetUri = targetUri;
        this.pendingEvents = pendingEvents;
        this.pendingAdminEvents = pendingAdminEvents;
        this.requestConfig = requestConfig;
        this.agwId = agwId;

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
        logger.debug("HttpEventEmitterProvider onEvent call for Event");
        completeEventAttributes(event);
        logger.debugf("Pending Events to send stored in buffer: %d", pendingEvents.size());
        long uid = idGenerator.nextValidId();
        IdentifiedEvent identifiedEvent = new IdentifiedEvent(uid, event);

        while (!pendingEvents.offer(identifiedEvent)) {
            Event skippedEvent = pendingEvents.poll();
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
        logger.debugf("Pending AdminEvents to send stored in buffer: %d", pendingAdminEvents.size());
        long uid = idGenerator.nextValidId();
        IdentifiedAdminEvent identifiedAdminEvent = new IdentifiedAdminEvent(uid, adminEvent);
        ExtendedAdminEvent customAdminEvent = completeAdminEventAttributes(identifiedAdminEvent);

        while (!pendingAdminEvents.offer(customAdminEvent)) {
            AdminEvent skippedAdminEvent = pendingAdminEvents.poll();
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

    private void sendEvents() {
        sendEvents(pendingEvents, e -> sendBytes(toFlat(e), Event.class.getSimpleName()));
        sendEvents(pendingAdminEvents, e -> sendBytes(toFlat(e), AdminEvent.class.getSimpleName()));
    }

    private void sendBytes(ByteBuffer buffer, String type) throws IOException, HttpEventEmitterException {
        byte[] b = new byte[buffer.remaining()];
        buffer.get(b);

        String obj = Base64.getEncoder().encodeToString(b);
        Container c = new Container(type, obj);
        String json = toJson(c);
        sendJson(json);
    }

    private void sendJson(String json) throws IOException, HttpEventEmitterException {
        HttpPost httpPost = new HttpPost(targetUri);
        httpPost.setConfig(requestConfig);

        StringEntity stringEntity = new StringEntity(json);
        httpPost.setEntity(stringEntity);
        httpPost.setHeader("Content-type", "application/json");

        String token = username + ":" + secretToken;
        String b64Token = Base64.getEncoder().encodeToString(token.getBytes());
        List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader(AUTHORIZATION, BASIC + " " + b64Token));
        headers.add(new BasicHeader(AGW_ID, agwId));

        // Sign the payload
        try {
            String signature = computeSignature(json);
            headers.add(new BasicHeader(PKCS7_SIGNATURE, signature));
        } catch (Exception e) {
            throw new HttpEventEmitterException(e.getMessage());
        }

        httpPost.setHeaders(headers.toArray(new Header[0]));
        send(httpPost);
    }

    private void send(HttpPost httpPost) throws HttpEventEmitterException, IOException {
        try (CloseableHttpResponse response = httpClient.execute(httpPost, httpContext)) {
            int status = response.getStatusLine().getStatusCode();

            if (status != HTTP_OK) {
                logger.errorv("Sending failure (Server response:{0})", status);
                throw new HttpEventEmitterException("Target server failure.");
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

    /**
     * Produces a PKCS7 signature on data using the signature key of the current realm.
     *
     * @param data the string to sign
     */
    private String computeSignature(String data) throws CertificateEncodingException, IOException, OperatorCreationException, CMSException {
        Security.addProvider(new BouncyCastleProvider());

        RealmModel realm = keycloakSession.getContext().getRealm();
        KeyWrapper keyWrapper = keycloakSession.keys().getActiveKey(realm, KeyUse.SIG, Algorithm.RS256);
        PrivateKey privateKey = (PrivateKey) keyWrapper.getPrivateKey();
        X509Certificate cert = keyWrapper.getCertificate();
        Provider provider = Security.getProvider("BC");

        CMSTypedData msg = new CMSProcessableByteArray(data.getBytes());
        CMSSignedDataGenerator signedDataGen = new CMSSignedDataGenerator();
        X509CertificateHolder signCert = new X509CertificateHolder(cert.getEncoded());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider(provider).build(privateKey);
        signedDataGen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().setProvider(provider).build()).build(signer, signCert));

        JcaCertStore certs = new JcaCertStore(List.of(cert));
        signedDataGen.addCertificates(certs);

        CMSSignedData signedData = signedDataGen.generate(msg, false);
        byte[] signatureBytes = signedData.getEncoded();

        return Base64.getEncoder().encodeToString(signatureBytes);
    }
}


