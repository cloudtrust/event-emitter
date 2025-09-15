package io.cloudtrust.keycloak.eventemitter.httpemitter;

import io.cloudtrust.keycloak.eventemitter.customevent.ExtendedAdminEvent;
import io.cloudtrust.keycloak.eventemitter.customevent.IdentifiedEvent;
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

    private HttpEventEmitterContext emitterContext;
    private CloseableHttpClient httpClient;

    public EventListenerProvider create(KeycloakSession keycloakSession) {
        logger.debug("HttpEventEmitterProviderFactory creation");

        return new HttpEventEmitterProvider(keycloakSession, httpClient, emitterContext);
    }

    public void init(Config.Scope config) {
        logger.info("HttpEventEmitter initialisation...");
        this.emitterContext = new HttpEventEmitterContext(config);
        httpClient = HttpClients.createDefault();
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
        ret.put("Target URI", this.emitterContext.getTargetUri());
        ret.put("Buffer capacity", Integer.toString(this.emitterContext.getBufferCapacity()));
        ret.put("IDP ID", this.emitterContext.getIdpId());
        ret.put("Snowflake Id Generator - Datacenter ID", Integer.toString(this.emitterContext.getDatacenterId()));

        return ret;
    }
}
