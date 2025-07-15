package io.cloudtrust.keycloak.eventemitter.httpemitter;

import io.cloudtrust.keycloak.eventemitter.customevent.ExtendedAdminEvent;
import io.cloudtrust.keycloak.eventemitter.customevent.IdentifiedEvent;
import io.cloudtrust.keycloak.eventemitter.snowflake.IdGenerator;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ServerInfoAwareProviderFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.IntConsumer;

/**
 * Factory for HttpEventEmitterProvider.
 * Mandatory configuration parameters:
 * - targetUri: valid HTTP target URI
 * - bufferCapacity: capacity of buffer which stores events that cannot be sent to target
 * - keycloakId: keycloak ID configuration for snowflake Id generator
 * - datacenterId: datacenterId configuration for snowflake Id generator
 * - agwId: ID of the AGW sending events
 */
public class HttpEventEmitterProviderFactory implements EventListenerProviderFactory, ServerInfoAwareProviderFactory {

    private static final Logger logger = Logger.getLogger(HttpEventEmitterProviderFactory.class);

    private static final String PROVIDER_NAME = "HTTP Event Emitter";
    public static final String PROVIDER_ID = "http-event-emitter";
    private static final String PROVIDER_VERSION = "1.0";

    private static final String TARGET_URI_CONFIG_KEY = "targetUri";
    private static final String BUFFER_CAPACITY_CONFIG_KEY = "bufferCapacity";
    private static final String SNOWFLAKE_KEYCLOAKID_CONFIG_KEY = "keycloakId";
    private static final String SNOWFLAKE_DATACENTERID_CONFIG_KEY = "datacenterId";
    private static final String AGW_ID_CONFIG_KEY = "agwId";
    private static final String CONNECT_TIMEOUT_MILLIS = "connectTimeoutMillis";
    private static final String CONNECTION_REQUEST_TIMEOUT_MILLIS = "connectionRequestTimeoutMillis";
    private static final String SOCKET_TIMEOUT_MILLIS = "socketTimeoutMillis";

    private String targetUri;
    private Integer bufferCapacity;
    private Integer keycloakId;
    private Integer datacenterId;
    private String agwId;
    private RequestConfig requestConfig;

    private CloseableHttpClient httpClient;
    private IdGenerator idGenerator;
    private LinkedBlockingQueue<IdentifiedEvent> pendingEventsToSend;
    private LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEventsToSend;


    public EventListenerProvider create(KeycloakSession keycloakSession) {
        logger.debug("HttpEventEmitterProviderFactory creation");

        return new HttpEventEmitterProvider(keycloakSession, httpClient, idGenerator, targetUri, pendingEventsToSend,
                pendingAdminEventsToSend, requestConfig, agwId);
    }

    public void init(Config.Scope config) {
        logger.info("HttpEventEmitter initialisation...");

        targetUri = config.get(TARGET_URI_CONFIG_KEY);

        if (targetUri == null) {
            logger.error("Target URI configuration is missing.");
            throw new IllegalArgumentException("Target URI configuration is missing.");
        }

        bufferCapacity = getIntConfig(config, BUFFER_CAPACITY_CONFIG_KEY, true);
        keycloakId = getIntConfig(config, SNOWFLAKE_KEYCLOAKID_CONFIG_KEY, true);
        datacenterId = getIntConfig(config, SNOWFLAKE_DATACENTERID_CONFIG_KEY, true);
        agwId = config.get(AGW_ID_CONFIG_KEY);

        // Creates request configuration
        Builder requestConfigBuilder = RequestConfig.custom();
        applyValueWhenDefined(config, CONNECT_TIMEOUT_MILLIS, requestConfigBuilder::setConnectTimeout);
        applyValueWhenDefined(config, CONNECTION_REQUEST_TIMEOUT_MILLIS, requestConfigBuilder::setConnectionRequestTimeout);
        applyValueWhenDefined(config, SOCKET_TIMEOUT_MILLIS, requestConfigBuilder::setSocketTimeout);
        this.requestConfig = requestConfigBuilder.build();

        // Initialisation
        httpClient = HttpClients.createDefault();
        idGenerator = new IdGenerator(keycloakId, datacenterId);
        pendingEventsToSend = new LinkedBlockingQueue<>(bufferCapacity);
        pendingAdminEventsToSend = new LinkedBlockingQueue<>(bufferCapacity);
    }

    private void applyValueWhenDefined(Config.Scope config, String name, IntConsumer consumer) {
        Integer value = getIntConfig(config, name, false);
        if (value != null) {
            consumer.accept(value);
        }
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

    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {
        // Nothing to do
    }

    public void close() {
        try {
            httpClient.close();
        } catch (IOException e) {
            logger.infof(e, "Error while closing.");
        }
    }

    public String getId() {
        return PROVIDER_ID;
    }

    public Map<String, String> getOperationalInfo() {
        Map<String, String> ret = new LinkedHashMap<>();
        ret.put("Version", PROVIDER_VERSION);
        ret.put("Name", PROVIDER_NAME);
        ret.put("Target URI", targetUri);
        ret.put("Buffer capacity", Integer.toString(bufferCapacity));
        ret.put("Snowflake Id Generator - Keycloak ID", Integer.toString(keycloakId));
        ret.put("Snowflake Id Generator - Datacenter ID", Integer.toString(datacenterId));

        return ret;
    }
}
