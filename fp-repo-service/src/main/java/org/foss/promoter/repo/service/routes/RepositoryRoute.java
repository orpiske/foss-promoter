package org.foss.promoter.repo.service.routes;

import java.io.File;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.micrometer.eventnotifier.MicrometerRouteEventNotifier;
import org.apache.camel.component.micrometer.routepolicy.MicrometerRoutePolicyFactory;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.micrometer.MicrometerConstants.METRICS_REGISTRY_NAME;
import static org.foss.promoter.common.PrometheusRegistryUtil.getMetricRegistry;

public class RepositoryRoute extends RouteBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(RepositoryRoute.class);
    private final String bootstrapHost;
    private final int bootstrapPort;
    private final String dataDir;
    private ProducerTemplate producerTemplate;


    public RepositoryRoute(String bootstrapHost, int bootstrapPort, String dataDir) {
        this.bootstrapHost = bootstrapHost;
        this.bootstrapPort = bootstrapPort;
        this.dataDir = dataDir;
    }

    private void process(Exchange exchange) {
        String repo = exchange.getMessage().getBody(String.class);

        if (repo == null) {
            exchange.getMessage().setHeader("valid", false);
            return;
        }

        final String[] parts = repo.split("/");
        if (parts == null || parts.length == 0) {
            exchange.getMessage().setHeader("valid", false);
            return;
        }

        var name = parts[parts.length - 1];
        exchange.getMessage().setHeader("valid", true);
        exchange.getMessage().setHeader("name", name);

        File repoDir = new File(dataDir, name);
        exchange.getMessage().setHeader("exists", repoDir.exists() && repoDir.isDirectory());

        LOG.info("Processing repository {} with address {} ", name, repo);
    }


    private void doSend(RevCommit rc) {
        LOG.debug("Commit message: {}", rc.getShortMessage());

        producerTemplate.sendBody("direct:collected", rc.getShortMessage());
    }

    private void processLogEntry(Exchange exchange) {
        LOG.info("Processing log entries");
        RevWalk walk = exchange.getMessage().getBody(RevWalk.class);

        walk.forEach(this::doSend);
    }

    @Override
    public void configure() {
        producerTemplate = getContext().createProducerTemplate();

        MeterRegistry registry = getMetricRegistry();

        // Add the registry
        getContext().getRegistry().bind(METRICS_REGISTRY_NAME, registry);

        // Expose route statistics
        getContext().addRoutePolicyFactory(new MicrometerRoutePolicyFactory());
        // Count added / running routes
        getContext().getManagementStrategy().addEventNotifier(new MicrometerRouteEventNotifier());

        getContext().setLoadHealthChecks(true);

        // Handles the request body
        fromF("kafka:repositories?brokers=%s:%d", bootstrapHost, bootstrapPort)
                .routeId("repositories")
                .process(this::process)
                .choice()
                .when(header("valid").isEqualTo(true))
                    .to("direct:valid")
                .otherwise()
                    .to("direct:invalid");

        // If it's a valid repo, then either clone of pull (depending on whether the dest dir exists)
        from("direct:valid")
                .routeId("repositories-valid")
                .choice()
                .when(header("exists").isEqualTo(false))
                    .to("direct:clone")
                .otherwise()
                    .to("direct:pull")
                .end()
                .to("direct:log");

        from("direct:clone")
                .routeId("repositories-clone")
                .toD(String.format("git://%s/${header.name}?operation=clone&remotePath=${body}", dataDir));

        from("direct:pull")
                .routeId("repositories-pull")
                .toD(String.format("git://%s/${header.name}?operation=pull&remoteName=origin", dataDir));

        // Logs if invalid stuff is provided
        from("direct:invalid")
                .routeId("repositories-invalid")
                .log(LoggingLevel.ERROR, "Unable to process repository ${body}");

        // Handles each commit on the repository
        from("direct:log")
                .routeId("repositories-log")
                .toD(String.format("git://%s/${header.name}?operation=log", dataDir))
                .process(this::processLogEntry)
                .to("direct:collected");

        from("direct:collected")
                .routeId("repositories-collected")
                // For the demo: this one is really cool, because without it, sending to Kafka is quite slow
//                .aggregate(constant(true)).completionSize(20).aggregationStrategy(AggregationStrategies.groupedBody())
//                .threads(5)
                .toF("kafka:commits?brokers=%s:%d", bootstrapHost, bootstrapPort);
    }


}
