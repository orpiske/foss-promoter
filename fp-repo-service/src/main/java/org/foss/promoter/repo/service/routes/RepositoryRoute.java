package org.foss.promoter.repo.service.routes;

import java.io.File;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.micrometer.eventnotifier.MicrometerRouteEventNotifier;
import org.apache.camel.component.micrometer.routepolicy.MicrometerRoutePolicyFactory;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.foss.promoter.common.data.CommitInfo;
import org.foss.promoter.common.data.Repository;
import org.foss.promoter.common.data.Tracking;
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
        Repository repo = exchange.getMessage().getBody(Repository.class);

        if (repo == null) {
            exchange.getMessage().setHeader("valid", false);
            LOG.error("A null repository was provided");
            return;
        }

        final String[] parts = repo.getName().split("/");
        if (parts == null || parts.length == 0) {
            exchange.getMessage().setHeader("valid", false);
            LOG.error("An invalid repository was provided for transaction {}", repo.getTransactionId());
            return;
        }

        var name = parts[parts.length - 1];
        exchange.getMessage().setHeader("valid", true);
        exchange.getMessage().setHeader("name", name);
        exchange.getMessage().setHeader("transaction-id", repo.getTransactionId());

        File repoDir = new File(dataDir, name);
        exchange.getMessage().setHeader("exists", repoDir.exists() && repoDir.isDirectory());

        LOG.info("Processing repository {} with address {} ", name, repo);
    }


    private void doSend(RevCommit rc, String projectName) {
        CommitInfo commitInfo = new CommitInfo();
        commitInfo.setProjectName(projectName);

        final PersonIdent authorIdent = rc.getAuthorIdent();
        commitInfo.setAuthorName(authorIdent.getName());
        commitInfo.setAuthorEmail(authorIdent.getEmailAddress());
        commitInfo.setMessage(rc.getShortMessage());

        if (LOG.isDebugEnabled()) {
            LOG.debug("Commit message: {}", rc.getShortMessage());
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Commit date time: {}", authorIdent.getWhenAsInstant());
        }

        commitInfo.setDate(authorIdent.getWhenAsInstant().toString());

        producerTemplate.sendBody("direct:collected", commitInfo);
    }

    private void processLogEntry(Exchange exchange) {
        LOG.info("Processing log entries");
        RevWalk walk = exchange.getMessage().getBody(RevWalk.class);

        final String name = exchange.getMessage().getHeader("name", String.class);

        walk.forEach(r -> doSend(r, name));
    }

    private static void setTrackingState(String state, Exchange exchange) {
        Tracking tracking = new Tracking();

        tracking.setState(state);
        tracking.setTransactionId(exchange.getMessage().getHeader("transaction-id", String.class));

        exchange.getMessage().setBody(tracking);
    }

    private void processCloning(Exchange exchange) {
        setTrackingState("cloning", exchange);
    }

    private void processPulling(Exchange exchange) {
        setTrackingState("pulling", exchange);
    }

    private void processReadingLog(Exchange exchange) {
        setTrackingState("reading-log", exchange);
    }

    private void processReadLog(Exchange exchange) {
        setTrackingState("read-log", exchange);
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
                .unmarshal().json(JsonLibrary.Jackson, Repository.class)
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
                .multicast()
                    .to("direct:readingLog")
                    .to("direct:log");

        // Handles cloning the repository
        from("direct:clone")
                .routeId("repositories-clone")
                .multicast()
                    .to(ExchangePattern.InOnly, "direct:cloning")
                    .toD(String.format("git://%s/${header.name}?operation=clone&remotePath=${body.name}", dataDir));

        from("direct:cloning")
                .routeId("repositories-cloning")
                .process(this::processCloning)
                .marshal().json(JsonLibrary.Jackson)
                .toF("kafka:tracking?brokers=%s:%d", bootstrapHost, bootstrapPort);


        // Handles pulling the repository (i.e.: if it has been cloned in the past)
        from("direct:pull")
                .routeId("repositories-pull")
                .multicast()
                    .to(ExchangePattern.InOnly, "direct:pulling")
                    .toD(String.format("git://%s/${header.name}?operation=pull&remoteName=origin", dataDir));

        from("direct:pulling")
                .routeId("repositories-pulling")
                .process(this::processPulling)
                .marshal().json(JsonLibrary.Jackson)
                .toF("kafka:tracking?brokers=%s:%d", bootstrapHost, bootstrapPort);

        // Logs if invalid stuff is provided
        from("direct:invalid")
                .routeId("repositories-invalid")
                .log(LoggingLevel.ERROR, "Unable to process repository ${body}");

        // Handles each commit on the repository
        from("direct:log")
                .routeId("repositories-log")
                .toD(String.format("git://%s/${header.name}?operation=log", dataDir))
                .process(this::processLogEntry)
                .to("direct:readLog");

        from("direct:readingLog")
                .routeId("repositories-reading-log")
                .process(this::processReadingLog)
                .marshal().json(JsonLibrary.Jackson)
                .toF("kafka:tracking?brokers=%s:%d", bootstrapHost, bootstrapPort);

        from("direct:readLog")
                .routeId("repositories-read-log")
                .process(this::processReadLog)
                .marshal().json(JsonLibrary.Jackson)
                .toF("kafka:tracking?brokers=%s:%d", bootstrapHost, bootstrapPort);

        from("direct:collected")
                .routeId("repositories-collected")
                // For the demo: this one is really cool, because without it, sending to Kafka is quite slow
//                .aggregate(constant(true)).completionSize(20).aggregationStrategy(AggregationStrategies.groupedBody())
//                .threads(5)
                .marshal().json(JsonLibrary.Jackson)
                .toF("kafka:commits?brokers=%s:%d", bootstrapHost, bootstrapPort);
    }


}
