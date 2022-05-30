package io.cloudtrust.keycloak.kafkaeventemitter;

import io.cloudtrust.keycloak.snowflake.IdGenerator;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.security.oauthbearer.secured.OAuthBearerLoginCallbackHandler;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ServerInfoAwareProviderFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class KafkaEventEmitterProviderFactory implements EventListenerProviderFactory, ServerInfoAwareProviderFactory {
    private static final Logger logger = Logger.getLogger(KafkaEventEmitterProviderFactory.class);

    private static final String PROVIDER_NAME = "Kafka Event Emitter";
    private static final String PROVIDER_ID = "kafka-event-emitter";
    private static final String PROVIDER_VERSION = "1.0";

    private static final String SECURITY_PROTOCOL_CONFIG = "security.protocol";

    private static final String BUFFER_CAPACITY_CONFIG_KEY = "bufferCapacity";
    private static final String CLIENT_ID_CONFIG_KEY = "clientId";
    private static final String BOOTSTRAP_SERVERS_CONFIG_KEY = "bootstrapServers";
    private static final String EVENT_TOPIC_CONFIG_KEY = "eventTopic";
    private static final String ADMIN_EVENT_TOPIC_CONFIG_KEY = "adminEventTopic";
    private static final String SECURITY_PROTOCOL_KEY = "securityProtocol";
    private static final String SASL_JAAS_CONFIG_KEY = "saslJaasConfig";
    private static final String SASL_OAUTHBEARER_TOKEN_ENDPOINT_URL_KEY = "saslOauthbearerTokenEndpointUrl";
    private static final String SASL_MECHANISM_KEY = "saslMechanism";
    private static final String SNOWFLAKE_KEYCLOAKID_CONFIG_KEY = "keycloakId";
    private static final String SNOWFLAKE_DATACENTERID_CONFIG_KEY = "datacenterId";

    private Integer keycloakId;
    private Integer datacenterId;
    private String eventTopic;
    private String adminEventTopic;

    private Properties kafkaProperties;
    private Producer<String, String> producer;
    private IdGenerator idGenerator;
    //Queue only used during STARTING state
    private LinkedBlockingQueue<ProducerRecord<String, String>> pendingEvents;
    private KafkaEventEmitterState state;
    private Lock stateLock;

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        if(state.isInitialized()) {
            state.starting();
            producer = new KafkaProducer<String, String>(kafkaProperties);
        }
        return new KafkaEventEmitterProvider(session, producer, eventTopic, adminEventTopic, idGenerator, pendingEvents, state, stateLock);
    }

    @Override
    public void init(Config.Scope config) {
        // Kafka producer configuration
        eventTopic = getStringConfig(config, EVENT_TOPIC_CONFIG_KEY);
        adminEventTopic = getStringConfig(config, ADMIN_EVENT_TOPIC_CONFIG_KEY);

        kafkaProperties = new Properties();
        kafkaProperties.put(SECURITY_PROTOCOL_CONFIG, getStringConfig(config, SECURITY_PROTOCOL_KEY));
        kafkaProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getStringConfig(config, BOOTSTRAP_SERVERS_CONFIG_KEY));
        kafkaProperties.put(ProducerConfig.CLIENT_ID_CONFIG, getStringConfig(config, CLIENT_ID_CONFIG_KEY));
        kafkaProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        kafkaProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        kafkaProperties.put(SaslConfigs.SASL_JAAS_CONFIG, getStringConfig(config, SASL_JAAS_CONFIG_KEY));
        kafkaProperties.put(SaslConfigs.SASL_OAUTHBEARER_TOKEN_ENDPOINT_URL, getStringConfig(config, SASL_OAUTHBEARER_TOKEN_ENDPOINT_URL_KEY));
        kafkaProperties.put(SaslConfigs.SASL_MECHANISM, getStringConfig(config, SASL_MECHANISM_KEY));
        kafkaProperties.put(SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS, OAuthBearerLoginCallbackHandler.class.getName());

        // Snowflake ID generator configuration
        keycloakId = getIntConfig(config, SNOWFLAKE_KEYCLOAKID_CONFIG_KEY, true);
        datacenterId = getIntConfig(config, SNOWFLAKE_DATACENTERID_CONFIG_KEY, true);
        idGenerator = new IdGenerator(keycloakId, datacenterId);
        pendingEvents = new LinkedBlockingQueue<>(getIntConfig(config, BUFFER_CAPACITY_CONFIG_KEY, true));
        state = new KafkaEventEmitterState();
        state.initialized();
        stateLock = new ReentrantLock();
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {
        if (producer != null){
            producer.close();
        }
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public Map<String, String> getOperationalInfo() {
        Map<String, String> ret = new LinkedHashMap<>();
        ret.put("Version", PROVIDER_VERSION);
        ret.put("Name", PROVIDER_NAME);
        ret.put("Event topic", eventTopic);
        ret.put("Admin event topic", adminEventTopic);
        ret.put("Snowflake Id Generator - Keycloak ID", Integer.toString(keycloakId));
        ret.put("Snowflake Id Generator - Datacenter ID", Integer.toString(datacenterId));
        return ret;
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

    private String getStringConfig(Config.Scope config, String name) {
        String value = config.get(name);
        if (value == null) {
            String message = name + " configuration is missing";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
