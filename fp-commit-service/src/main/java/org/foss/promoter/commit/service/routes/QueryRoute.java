package org.foss.promoter.commit.service.routes;

import java.util.List;

import com.fasterxml.jackson.core.JacksonException;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.component.micrometer.routepolicy.MicrometerRoutePolicyFactory;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.foss.promoter.commit.service.common.CassandraClient;
import org.foss.promoter.commit.service.common.ContributionsDao;
import org.foss.promoter.common.data.Contribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueryRoute extends RouteBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(QueryRoute.class);

    private final int servicePort;
    private final CassandraClient cassandraClient;
    private ContributionsDao contributionsDao;
    private final MeterRegistry registry;

    public QueryRoute(CassandraClient cassandraClient, int servicePort, MeterRegistry registry) {
        this.cassandraClient = cassandraClient;
        this.servicePort = servicePort;
        this.registry = registry;

        contributionsDao = cassandraClient.newContributionDao();
        contributionsDao.useKeySpace();
    }

    private void processQueryEmail(Exchange exchange) {
        String email = exchange.getMessage().getHeader("email", String.class);

        if (email == null || email.isEmpty()) {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_TEXT, "Invalid email: null or empty");

            return;
        }

        try {
            final List<Contribution> contributionList = contributionsDao.query(email);
            if (contributionList == null || contributionList.isEmpty()) {
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 204);

                return;
            }

            exchange.getMessage().setBody(contributionList);
        } catch (Exception e) {
            LOG.error("Unable to process request for email {}: {}", email, e.getMessage(), e);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
        }
    }

    @Override
    public void configure() throws Exception {
        getContext().getRegistry().bind(MicrometerConstants.METRICS_REGISTRY_NAME, registry);
        getContext().addRoutePolicyFactory(new MicrometerRoutePolicyFactory());

        restConfiguration().component("netty-http")
                .host("0.0.0.0")
                .port(servicePort)
                .enableCORS(true)
                .bindingMode(RestBindingMode.json);

        onException(JacksonException.class)
                .routeId("query-invalid-json")
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .log(LoggingLevel.ERROR,  "Failed to process data: ${body}")
                .setBody().constant("Invalid json data");

        rest("/api")
                .get("/hello").to("direct:hello")
                .get("/query/email/{email}").to("direct:queryEmail");

        from("direct:hello")
                .routeId("query-hello")
                .transform().constant("Hello World");

        from("direct:queryEmail")
                .routeId("query-author")
                .process(this::processQueryEmail)
                .choice()
                .when(header("has-tracking-data").isEqualTo(false)) // only marshal if there's data
                    .marshal().json(JsonLibrary.Jackson)
                .endChoice();

    }
}
