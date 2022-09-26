package org.foss.promoter.web.routes;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import com.fasterxml.jackson.core.JacksonException;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.component.micrometer.routepolicy.MicrometerRoutePolicyFactory;
import org.apache.camel.model.rest.RestBindingMode;
import org.foss.promoter.common.PrometheusRegistryUtil;
import org.foss.promoter.common.data.ProcessingResponse;
import org.foss.promoter.common.data.Repository;
import org.foss.promoter.common.data.SystemInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepositoryRoute extends RouteBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(RepositoryRoute.class);

    private final String bootstrapHost;
    private final int bootstrapPort;
    private final int servicePort;

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

        exchange.getMessage().setBody(body.getName());
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
        exchange.getMessage().setBody(response);
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
                        .toF("kafka:repositories?brokers=%s:%d", bootstrapHost, bootstrapPort)
                        .process(this::processSuccessfulResponse)
                .endChoice();
    }
}

