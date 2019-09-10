package io.cloudtrust.keycloak;

import io.cloudtrust.keycloak.eventemitter.EventEmitterProvider;
import io.cloudtrust.keycloak.eventemitter.EventEmitterProviderFactory;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import org.apache.commons.io.IOUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.keycloak.test.FluentTestsHelper;
import org.xnio.streams.ChannelInputStream;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public abstract class AbstractTest {
    protected static Undertow server;
    protected static final int LISTEN_PORT = 8888;
    protected static final String KEYCLOAK_URL = getKeycloakUrl();
    private static final String MODULE_NAME_WAR = "event-emitter.war";

    private static final String username = "toto";
    private static final String password = "passwordverylongandhardtoguess";

    @ClassRule
    public static final EnvironmentVariables envVariables = new EnvironmentVariables();

    private static String getKeycloakUrl() {
        String url = FluentTestsHelper.DEFAULT_KEYCLOAK_URL;
        try {
            URI uri = new URI(FluentTestsHelper.DEFAULT_KEYCLOAK_URL);
            url = url.replace(String.valueOf(uri.getPort()), System.getProperty("auth.server.http.port", "8080"));
        } catch (Exception e) {
            // Ignore
        }
        return url;
    }

    @BeforeClass
    public static void initServer() throws IOException {
        if (server == null) {
            server = startHttpServer(handler);
        }
    }

    @BeforeClass
    public static void intEnv() throws IOException {
        envVariables.set(EventEmitterProvider.KEYCLOAK_BRIDGE_SECRET_TOKEN, password);
        envVariables.set(EventEmitterProvider.HOSTNAME, username);
    }

    protected static HttpHandler handler = new HttpHandler() {
        private String jsonReceived;
        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            String basicToken = exchange.getRequestHeaders().get("Authorization").element();
            String[] subParts = basicToken.split("Basic ");
            Assert.assertEquals(2, subParts.length);
            String decodedToken = new String(Base64.getDecoder().decode(subParts[1]));

            if (!(username+":"+password).equals(decodedToken)) {
                exchange.setStatusCode(StatusCodes.FORBIDDEN);
                return;
            }

            exchange.setStatusCode(StatusCodes.OK);
            ChannelInputStream cis = new ChannelInputStream(exchange.getRequestChannel());
            jsonReceived = IOUtils.toString(cis, StandardCharsets.UTF_8);
        }
        public String toString() {
            return jsonReceived;
        }
    };


    protected static Undertow startHttpServer(HttpHandler handler) {
        Undertow server = Undertow.builder()
                .addHttpListener(LISTEN_PORT, "0.0.0.0", handler)
                .build();
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop()));
        return server;
    }

    @Deployment
    public static WebArchive deploy() {
        return ShrinkWrap.create(WebArchive.class, MODULE_NAME_WAR)
                .addClasses(
                        EventEmitterProvider.class,
                        EventEmitterProviderFactory.class)
                .addAsManifestResource(new File("src/test/resources", "manifest.xml"))
                .addAsServiceProvider(EventEmitterProviderFactory.class);
    }

}
