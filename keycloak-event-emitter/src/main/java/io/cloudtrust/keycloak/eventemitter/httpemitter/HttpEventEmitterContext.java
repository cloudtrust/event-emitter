package io.cloudtrust.keycloak.eventemitter.httpemitter;

import io.cloudtrust.keycloak.eventemitter.customevent.ExtendedAdminEvent;
import io.cloudtrust.keycloak.eventemitter.customevent.IdentifiedEvent;
import io.cloudtrust.keycloak.eventemitter.snowflake.IdGenerator;
import org.apache.http.client.config.RequestConfig;
import org.jboss.logging.Logger;
import org.keycloak.Config;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.IntConsumer;

public class HttpEventEmitterContext {
    private static final Logger logger = Logger.getLogger(HttpEventEmitterContext.class);

    private static final String TARGET_URI_CONFIG_KEY = "targetUri";
    private static final String BUFFER_CAPACITY_CONFIG_KEY = "bufferCapacity";
    private static final String DATACENTER_ID_CONFIG_KEY = "datacenterId";
    private static final String IDP_ID_CONFIG_KEY = "idpId";
    private static final String TID_REALM = "tidRealm"; // KC_xxx_TID_REALM
    private static final String CONNECT_TIMEOUT_MILLIS = "connectTimeoutMillis";
    private static final String CONNECTION_REQUEST_TIMEOUT_MILLIS = "connectionRequestTimeoutMillis";
    private static final String SOCKET_TIMEOUT_MILLIS = "socketTimeoutMillis";

    private final String targetUri;
    private final int bufferCapacity;
    private final int datacenterId;
    private final String idpId;
    private final String trustIDRealm;

    private final RequestConfig requestConfig;
    private final IdGenerator idGenerator;
    private final LinkedBlockingQueue<IdentifiedEvent> pendingEventsToSend;
    private final LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEventsToSend;

    public HttpEventEmitterContext(Config.Scope config) {
        this.targetUri = getStrConfig(config, TARGET_URI_CONFIG_KEY, true);
        this.bufferCapacity = getIntConfig(config, BUFFER_CAPACITY_CONFIG_KEY, true);
        this.datacenterId = getIntConfig(config, DATACENTER_ID_CONFIG_KEY, true);
        this.idpId = getStrConfig(config, IDP_ID_CONFIG_KEY, true);
        this.trustIDRealm = getStrConfig(config, TID_REALM, true);

        this.requestConfig = createRequestConfig(config);
        this.idGenerator = createIdGenerator();
        pendingEventsToSend = new LinkedBlockingQueue<>(bufferCapacity);
        pendingAdminEventsToSend = new LinkedBlockingQueue<>(bufferCapacity);
    }

    private String getStrConfig(Config.Scope config, String name, boolean mandatory) {
        String value = config.get(name);
        if (value==null && mandatory) {
            String message = name + " configuration is missing";
            logger.errorf(message);
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private Integer getIntConfig(Config.Scope config, String name, boolean mandatory) {
        try {
            Integer value = config.getInt(name);
            if (value == null && mandatory) {
                String message = name + " configuration is missing";
                logger.errorf(message);
                throw new IllegalArgumentException(message);
            }
            return value;
        } catch (NumberFormatException e) {
            logger.errorv(e, "Invalid %s configuration parameter", name);
            throw e;
        }
    }

    private RequestConfig createRequestConfig(Config.Scope config) {
        Integer connectTimeoutMillis = getIntConfig(config, CONNECT_TIMEOUT_MILLIS, false);
        Integer connectionRequestTimeoutMillis = getIntConfig(config, CONNECTION_REQUEST_TIMEOUT_MILLIS, false);
        Integer socketTimeoutMillis = getIntConfig(config, SOCKET_TIMEOUT_MILLIS, false);

        // Creates request configuration
        RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();
        applyValueWhenDefined(connectTimeoutMillis, requestConfigBuilder::setConnectTimeout);
        applyValueWhenDefined(connectionRequestTimeoutMillis, requestConfigBuilder::setConnectionRequestTimeout);
        applyValueWhenDefined(socketTimeoutMillis, requestConfigBuilder::setSocketTimeout);

        return requestConfigBuilder.build();
    }

    private IdGenerator createIdGenerator() {
        // Hash idpId and keep 5 last bits as keycloakId
        int keycloakId = this.idpId.hashCode() % 32;
        if (keycloakId < 0) {
            // According to Java the result of a modulo operation can be negative :)
            keycloakId += 32;
        }

        return new IdGenerator(keycloakId, this.datacenterId);
    }

    private void applyValueWhenDefined(Integer value, IntConsumer consumer) {
        if (value != null) {
            consumer.accept(value);
        }
    }

    public String getTargetUri() {
        return this.targetUri;
    }

    public int getBufferCapacity() {
        return this.bufferCapacity;
    }

    public int getDatacenterId() {
        return this.datacenterId;
    }

    public String getTrustIDRealm() {
        return this.trustIDRealm;
    }

    public String getIdpId() {
        return this.idpId;
    }

    public RequestConfig getRequestConfig() {
        return this.requestConfig;
    }

    public IdGenerator getIdGenerator() {
        return this.idGenerator;
    }

    public LinkedBlockingQueue<IdentifiedEvent> getPendingEvents() {
        return this.pendingEventsToSend;
    }

    public LinkedBlockingQueue<ExtendedAdminEvent> getPendingAdminEvents() {
        return this.pendingAdminEventsToSend;
    }
}
