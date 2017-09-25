package io.cloudtrust.keycloak.module.eventemitter;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ServerInfoAwareProviderFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory for EventEmitterProvider.
 */
public class EventEmitterProviderFactory implements EventListenerProviderFactory, ServerInfoAwareProviderFactory{

    private final String PROVIDER_NAME = "Event Emitter";
    private final String PROVIDER_ID = "event-emitter";
    private final String PROVIDER_VERSION = "1.0";

    public EventListenerProvider create(KeycloakSession keycloakSession) {
        return new EventEmitterProvider();
    }


    public void init(Config.Scope scope) {

    }

    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {

    }

    public void close() {

    }

    public String getId() {
        return PROVIDER_ID;
    }

    public Map<String, String> getOperationalInfo() {
        Map<String, String> ret = new LinkedHashMap();
        ret.put("version", PROVIDER_VERSION);
        ret.put("name", PROVIDER_NAME);
        return ret;
    }
}
