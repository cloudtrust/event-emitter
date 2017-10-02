package io.cloudtrust.keycloak.module.eventemitter;

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

/**
 * Factory for EventEmitterProvider.
 * Mandatory configuration parameters:
 * <li>
 *     <ul><b>targetUri</b>: valid HTTP target URI</ul>
 *     <ul><b>format</b>: serialization format (FLATBUFFERS or JSON)</ul>
 * </li>
 */
public class EventEmitterProviderFactory implements EventListenerProviderFactory, ServerInfoAwareProviderFactory{

    private static final Logger logger = Logger.getLogger(EventEmitterProviderFactory.class);

    private static final String PROVIDER_NAME = "Event Emitter";
    private static final String PROVIDER_ID = "event-emitter";
    private static final String PROVIDER_VERSION = "1.0";

    private static final String TARGET_URI_CONFIG_KEY = "targetUri";
    private static final String FORMAT_CONFIG_KEY = "format";

    private String targetUri;
    private SerialisationFormat format;
    private CloseableHttpClient httpClient;


    public EventListenerProvider create(KeycloakSession keycloakSession) {
        logger.debug("EventEmitterProviderFactory create("+targetUri+", "+format.name()+")");
        httpClient = HttpClients.createDefault();
        return new EventEmitterProvider(httpClient, targetUri, format);
    }

    public void init(Config.Scope config) {
        // Serializer config
        String formatConfig = config.get(FORMAT_CONFIG_KEY);

        if(formatConfig == null){
            logger.error("No serialization configuration found.");
            // TODO Improve error handling
            throw new RuntimeException();
        }

        try {
            format = SerialisationFormat.valueOf(formatConfig);
        }catch(IllegalArgumentException e){
            logger.error("Invalid serialization format: "+formatConfig);
        }

        // Target URI config
        targetUri = config.get(TARGET_URI_CONFIG_KEY);

        if(targetUri == null){
            logger.error("Target URI configuration is missing.");
            // TODO Improve error handling
            throw new RuntimeException();
        }

    }

    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {

    }

    public void close() {
        try {
            httpClient.close();
        } catch (IOException e) {
            // TODO error handling
            e.printStackTrace();
        }
    }

    public String getId() {
        return PROVIDER_ID;
    }

    public Map<String, String> getOperationalInfo() {
        Map<String, String> ret = new LinkedHashMap();
        ret.put("Version", PROVIDER_VERSION);
        ret.put("Name", PROVIDER_NAME);
        ret.put("Target URI", targetUri);
        ret.put("Serialisation format", format.toString());
        return ret;
    }
}
