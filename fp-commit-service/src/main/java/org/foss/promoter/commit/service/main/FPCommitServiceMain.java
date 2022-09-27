package org.foss.promoter.commit.service.main;

import java.util.concurrent.Callable;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.camel.main.Main;
import org.foss.promoter.commit.service.common.CassandraClient;
import org.foss.promoter.commit.service.common.ContributionsDao;
import org.foss.promoter.commit.service.routes.CommitRoute;
import org.foss.promoter.commit.service.routes.QueryRoute;
import org.foss.promoter.common.PrometheusRegistryUtil;
import picocli.CommandLine;

public class FPCommitServiceMain implements Callable<Integer> {
    @CommandLine.Option(names = {"-s", "--bootstrap-server"}, description = "The Kafka bootstrap server to use", required = true)
    private String bootstrapServer;

    @CommandLine.Option(names = {"-p", "--bootstrap-server-port"}, description = "The Kafka bootstrap server port to use", defaultValue = "9092")
    private int bootstrapPort;

    @CommandLine.Option(names = {"-cs", "--cassandra-server"}, description = "The Cassandra server to use", required = true)
    private String cassandraServer;

    @CommandLine.Option(names = {"-cp", "--cassandra-port"}, description = "The Cassandra server port (CQL 3) to use", defaultValue = "9042")
    private int cassandraPort;

    @CommandLine.Option(names = {"-kc", "--kafka-consumers-count"}, description = "The count of consumers to use when fetching from Kafka", defaultValue = "3")
    private int consumersCount;

    @CommandLine.Option(names = {"-sp", "--service-port"}, description = "The port to use for the web service", defaultValue = "8080", required = true)
    private int servicePort;

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "display a help message")
    private boolean helpRequested = false;

    @CommandLine.Option(names = {"-is", "--image-size"}, description = "The image size to generate", defaultValue = "1042")
    private int imageSize;
    private CassandraClient cassandraClient;

    private void createTable() {
        final ContributionsDao contributionsDao = cassandraClient.newContributionDao();

        contributionsDao.createKeySpace();
        contributionsDao.useKeySpace();
        contributionsDao.createTable();
    }

    @Override
    public Integer call() throws Exception {
        Main main = new Main();

        cassandraClient = new CassandraClient(cassandraServer, cassandraPort);
        createTable();

        MeterRegistry registry = PrometheusRegistryUtil.getMetricRegistry();

        main.configure().addRoutesBuilder(new CommitRoute(bootstrapServer, bootstrapPort, cassandraServer, cassandraPort, imageSize, consumersCount, registry));
        main.configure().addRoutesBuilder(new QueryRoute(cassandraClient, servicePort, registry));

        main.run();
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new FPCommitServiceMain()).execute(args);

        System.exit(exitCode);
    }

}
