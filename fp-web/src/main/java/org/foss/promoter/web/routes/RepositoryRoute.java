package org.foss.promoter.web.routes;

import com.fasterxml.jackson.core.JacksonException;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.component.micrometer.routepolicy.MicrometerRoutePolicyFactory;
import org.apache.camel.model.rest.RestBindingMode;
import org.foss.promoter.common.PrometheusRegistryUtil;
import org.foss.promoter.common.data.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepositoryRoute extends RouteBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(RepositoryRoute.class);

    private final String bootstrapHost;
    private final int bootstrapPort;
    private final int servicePort;

    public RepositoryRoute(String bootstrapHost, int bootstrapPort, int servicePort) {
        this.bootstrapHost = bootstrapHost;
        this.bootstrapPort = bootstrapPort;
        this.servicePort = servicePort;
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

    @Override
    public void configure() {
        MeterRegistry registry = PrometheusRegistryUtil.getMetricRegistry();

        getContext().getRegistry().bind(MicrometerConstants.METRICS_REGISTRY_NAME, registry);
        getContext().addRoutePolicyFactory(new MicrometerRoutePolicyFactory());

        restConfiguration().component("netty-http").host("0.0.0.0").port(servicePort).bindingMode(RestBindingMode.json);

        onException(JacksonException.class)
                .routeId("web-invalid-json")
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .setBody().constant("Invalid json data");

        rest("/api")
                .get("/hello").to("direct:hello")
                .post("/repository").type(Repository.class).to("direct:repository");

        from("direct:hello")
                .routeId("web-hello")
                .transform().constant("Hello World");

        from("direct:repository")
                .routeId("web-repository")
                .process(this::processRepository)
                .choice()
                    .when(header("valid").isEqualTo(true))
                        .toF("kafka:repositories?brokers=%s:%d", bootstrapHost, bootstrapPort)
                .endChoice();
    }
}

