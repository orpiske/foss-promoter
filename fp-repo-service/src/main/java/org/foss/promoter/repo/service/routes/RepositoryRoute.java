package org.foss.promoter.repo.service.routes;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepositoryRoute extends RouteBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(RepositoryRoute.class);
    private final String bootstrapHost;
    private final int bootstrapPort;
    private ProducerTemplate producerTemplate;

    public RepositoryRoute(String bootstrapHost, int bootstrapPort) {
        this.bootstrapHost = bootstrapHost;
        this.bootstrapPort = bootstrapPort;

    }

    private void process(Exchange exchange) {
        LOG.info("Processing repository: {}", exchange.getMessage().getBody());
    }

    private void doSend(RevCommit rc) {
        LOG.info("Commit message: {}", rc.getShortMessage());

//        producerTemplate.sendBody(String.format("kafka:commits?brokers=%s:%d", bootstrapHost, bootstrapPort),
//                rc.getShortMessage());
        producerTemplate.sendBody("direct:collected", rc.getShortMessage());
    }

    private void processLogEntry(Exchange exchange) {
        RevWalk walk = exchange.getMessage().getBody(RevWalk.class);

        walk.forEach(this::doSend);
    }

    @Override
    public void configure() {
        producerTemplate = getContext().createProducerTemplate();

        fromF("kafka:repositories?brokers=%s:%d", bootstrapHost, bootstrapPort)
// To avoid downloading the repository all the time
//                .process(this::process)
//                .toD("git:///Users/opiske/tmp/testRepo?operation=clone&remotePath=${body}")
//                .setHeader("repository", simple("${body}"))
                .to("direct:pull");

        from("direct:pull")
                .to("git:///Users/opiske/tmp/testRepo?operation=log")
                .process(this::processLogEntry)
                .to("direct:collected");

        from("direct:collected")
                // For the demo: this one is really cool, because without it, sending to Kafka is quite slow
                .aggregate(constant(true)).completionSize(20).aggregationStrategy(AggregationStrategies.groupedBody())
                .threads(5)
                .toF("kafka:commits?brokers=%s:%d", bootstrapHost, bootstrapPort);
    }
}
