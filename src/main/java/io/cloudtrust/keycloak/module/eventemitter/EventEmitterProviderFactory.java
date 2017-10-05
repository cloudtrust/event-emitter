package io.cloudtrust.keycloak.module.eventemitter;

import io.cloudtrust.keycloak.snowflake.IdGenerator;
import org.apache.commons.collections4.queue.CircularFifoQueue;
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

/**
 * Factory for EventEmitterProvider.
 * Mandatory configuration parameters:
 * <li>
 *     <ul><b>targetUri</b>: valid HTTP target URI</ul>
 *     <ul><b>format</b>: serialization format (FLATBUFFERS or JSON)</ul>
 *     <ul><b>bufferCapacity</b>: capacity of buffer which stores events that cannot be sent to target</ul>
 *     <ul><b>keycloakId</b>: keycloak Id configuration for snowflake Id generator</ul>
 *     <ul><b>datacenterId</b>: datacenterId configuration for snowflake Id generator</ul>
 * </li>
 */
public class EventEmitterProviderFactory implements EventListenerProviderFactory, ServerInfoAwareProviderFactory{

    private static final Logger logger = Logger.getLogger(EventEmitterProviderFactory.class);

    private static final String PROVIDER_NAME = "Event Emitter";
    private static final String PROVIDER_ID = "event-emitter";
    private static final String PROVIDER_VERSION = "1.0";

    private static final String TARGET_URI_CONFIG_KEY = "targetUri";
    private static final String FORMAT_CONFIG_KEY = "format";
    private static final String BUFFER_CAPACITY_CONFIG_KEY = "bufferCapacity";
    private static final String SNOWFLAKE_KEYCLOAKID_CONFIG_KEY = "keycloakId";
    private static final String SNOWFLAKE_DATACENTERID_CONFIG_KEY = "datacenterId";

    private String targetUri;
    private SerialisationFormat format;
    private Integer bufferCapacity;
    private Integer keycloakId;
    private Integer datacenterId;

    private CloseableHttpClient httpClient;
    private IdGenerator idGenerator;
    private ConcurrentEvictingQueue<IdentifiedEvent> pendingEventsToSend;
    private ConcurrentEvictingQueue<IdentifiedAdminEvent> pendingAdminEventsToSend;



    public EventListenerProvider create(KeycloakSession keycloakSession) {
        logger.debug("EventEmitterProviderFactory creation");

        return new EventEmitterProvider(httpClient, idGenerator, targetUri,
                format, pendingEventsToSend, pendingAdminEventsToSend);
    }

    public void init(Config.Scope config) {
        logger.info("EventEmitter initialisation...");

        // Serializer config
        String formatConfig = config.get(FORMAT_CONFIG_KEY);

        if(formatConfig == null){
            logger.error("No serialization configuration found.");
            throw new IllegalArgumentException("No serialization configuration found.");
        }

        try {
            format = SerialisationFormat.valueOf(formatConfig);
        }catch(IllegalArgumentException e){
            logger.errorv(e, "Invalid serialization format: "+formatConfig);
            throw e;
        }

        // Target URI config
        targetUri = config.get(TARGET_URI_CONFIG_KEY);

        if(targetUri == null){
            logger.error("Target URI configuration is missing.");
            throw new IllegalArgumentException("Target URI configuration is missing.");
        }

        // Buffer capacity
        try{
            bufferCapacity = config.getInt(BUFFER_CAPACITY_CONFIG_KEY);
        }catch(NumberFormatException e){
            logger.errorv(e,"Invalid buffer capacity configuration parameter");
            throw e;
        }

        if(bufferCapacity == null){
            logger.error("Buffer capacity configuration is missing");
            throw new IllegalArgumentException("Buffer capacity configuration is missing");
        }

        // Snowflake ID generator configuration
        // KeycloakId
        try {
            keycloakId = config.getInt(SNOWFLAKE_KEYCLOAKID_CONFIG_KEY);
        }catch(NumberFormatException e){
            logger.errorv(e,"Invalid KeycloakId configuration parameter");
            throw e;
        }

        if(bufferCapacity == null){
            logger.error("KeycloakId configuration is missing");
            throw new IllegalArgumentException("KeycloakId configuration is missing");
        }

        // DatacenterId
        try{
            datacenterId = config.getInt(SNOWFLAKE_DATACENTERID_CONFIG_KEY);
        }catch(NumberFormatException e){
            logger.errorv(e,"Invalid datacenterId configuration parameter");
            throw e;
        }

        if(bufferCapacity == null){
            logger.error("DatacenterId configuration is missing");
            throw new IllegalArgumentException("DatacenterId configuration is missing");
        }

        // Initialisation
        httpClient = HttpClients.createDefault();
        idGenerator = new IdGenerator(keycloakId, datacenterId);
        pendingEventsToSend = new ConcurrentEvictingQueue<>(bufferCapacity);
        pendingAdminEventsToSend = new ConcurrentEvictingQueue<>(bufferCapacity);

    }

    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {

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
        ret.put("Serialisation format", format.toString());
        ret.put("Buffer capacity", Integer.toString(bufferCapacity));
        ret.put("Snowflake Id Generator - Keycloak ID", Integer.toString(keycloakId));
        ret.put("Snowflake Id Generator - Datacenter ID", Integer.toString(datacenterId));

        return ret;
    }
}
