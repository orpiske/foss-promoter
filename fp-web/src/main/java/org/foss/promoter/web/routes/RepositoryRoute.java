package org.foss.promoter.web.routes;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import com.fasterxml.jackson.core.JacksonException;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.component.micrometer.routepolicy.MicrometerRoutePolicyFactory;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.commons.lang3.RandomStringUtils;
import org.foss.promoter.common.PrometheusRegistryUtil;
import org.foss.promoter.common.data.ProcessingResponse;
import org.foss.promoter.common.data.Repository;
import org.foss.promoter.common.data.SystemInfo;
import org.foss.promoter.common.data.Tracking;
import org.foss.promoter.common.data.TrackingState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepositoryRoute extends RouteBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(RepositoryRoute.class);

    private final String bootstrapHost;
    private final int bootstrapPort;
    private final int servicePort;
    private final Map<String, TrackingState> tracking = new HashMap<>();

    private final Properties properties;

    public RepositoryRoute(String bootstrapHost, int bootstrapPort, int servicePort) {
        this.bootstrapHost = bootstrapHost;
        this.bootstrapPort = bootstrapPort;
        this.servicePort = servicePort;


        this.properties = new Properties();
        try (final InputStream versionStream = this.getClass().getResourceAsStream("/version.properties")) {
            properties.load(versionStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void processRepository(Exchange exchange) {
        Repository body = exchange.getMessage().getBody(Repository.class);

        if (body == null || body.getName().isEmpty()) {
            exchange.getMessage().setHeader("valid", false);
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "text/plain");
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
            exchange.getMessage().setBody("Invalid request");

            return;
        }

        exchange.getMessage().setHeader("valid", true);

        if (body instanceof Repository) {
            LOG.debug("Received -> repository {}", body);
        }

        if (body.getTransactionId() == null || body.getTransactionId().isEmpty()) {
            String transactionId = RandomStringUtils.randomAlphanumeric(12);
            body.setTransactionId(transactionId);
            exchange.getMessage().setHeader("transaction-id", transactionId);
        }
    }

    public SystemInfo getSystemInfo() {
        return new SystemInfo(properties.getProperty("project.version"), properties.getProperty("camel.version"), properties.getProperty("kafka.client.version"));
    }

    private void processSystemInfo(Exchange exchange) {
        SystemInfo systemInfo = getSystemInfo();
        exchange.getMessage().setBody(systemInfo);
    }

    private void processSuccessfulResponse(Exchange exchange) {
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);

        ProcessingResponse response = new ProcessingResponse();
        response.setState("OK");
        response.setTransactionId(exchange.getMessage().getHeader("transaction-id", String.class));
        exchange.getMessage().setBody(response);
    }

    private void updateTracking(Exchange exchange) {
        final Tracking trackingBody = exchange.getMessage().getBody(Tracking.class);
        LOG.info("Adding new tracking info: {}", trackingBody);

        tracking.computeIfAbsent(trackingBody.getTransactionId(), k -> new TrackingState(k)).getStates().add(trackingBody.getState());
    }

    private void processTracking(Exchange exchange) {
        final Optional<TrackingState> stateOptional = tracking.values().stream().findFirst();

        if (stateOptional.isPresent()) {
            final TrackingState trackingState = stateOptional.get();
            LOG.info("Retrieving tracking info: {}", trackingState);

            exchange.getMessage().setBody(trackingState);
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader("has-tracking-data", true);
        } else {
            // The request is OK, but there's no content yet. So we return 204 (NO CONTENT)
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 204);
        }
    }

    @Override
    public void configure() {
        MeterRegistry registry = PrometheusRegistryUtil.getMetricRegistry();

        getContext().getRegistry().bind(MicrometerConstants.METRICS_REGISTRY_NAME, registry);
        getContext().addRoutePolicyFactory(new MicrometerRoutePolicyFactory());

        restConfiguration().component("netty-http")
                .host("0.0.0.0")
                .port(servicePort)
                .enableCORS(true)
                .bindingMode(RestBindingMode.json);

        onException(JacksonException.class)
                .routeId("web-invalid-json")
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .log(LoggingLevel.ERROR,  "Failed to process data: ${body}")
                .setBody().constant("Invalid json data");

        rest("/api")
                .get("/hello").to("direct:hello")
                .get("/info").to("direct:info")
                .get("/tracking").to("direct:tracking")
                .post("/repository").type(Repository.class).to("direct:repository");

        from("direct:hello")
                .routeId("web-hello")
                .transform().constant("Hello World");

        from("direct:info")
                .routeId("web-info")
                .process(this::processSystemInfo);

        from("direct:repository")
                .routeId("web-repository")
                .process(this::processRepository)
                .choice()
                    .when(header("valid").isEqualTo(true))
                        .marshal().json(JsonLibrary.Jackson)
                        .toF("kafka:repositories?brokers=%s:%d", bootstrapHost, bootstrapPort)
                        .process(this::processSuccessfulResponse)
                .endChoice();

        fromF("kafka:tracking?brokers=%s:%d", bootstrapHost, bootstrapPort)
                .routeId("web-tracking-consume")
                .unmarshal().json(JsonLibrary.Jackson, Tracking.class)
                .process(this::updateTracking);

        from("direct:tracking")
                .routeId("web-tracking-process")
                .process(this::processTracking)
                .choice()
                    .when(header("has-tracking-data").isEqualTo(false)) // only marshal if there's data
                        .marshal().json(JsonLibrary.Jackson)
                .endChoice();
    }
}

