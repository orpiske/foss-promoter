package org.foss.promoter.commit.service.main;

import java.util.concurrent.Callable;

import org.apache.camel.main.Main;
import org.foss.promoter.commit.service.common.CassandraClient;
import org.foss.promoter.commit.service.common.ContributionsDao;
import org.foss.promoter.commit.service.routes.CommitRoute;
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

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "display a help message")
    private boolean helpRequested = false;

    @CommandLine.Option(names = {"-is", "--image-size"}, description = "The image size to generate", defaultValue = "1042")
    private int imageSize;

    private void createTable() {
        CassandraClient cassandraClient = new CassandraClient(cassandraServer, cassandraPort);
        final ContributionsDao contributionsDao = cassandraClient.newExampleDao();

        contributionsDao.createKeySpace();
        contributionsDao.useKeySpace();
        contributionsDao.createTable();
    }

    @Override
    public Integer call() throws Exception {
        Main main = new Main();

        main.configure().addRoutesBuilder(new CommitRoute(bootstrapServer, bootstrapPort, cassandraServer, cassandraPort, imageSize, consumersCount));

        createTable();

        main.run();
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new FPCommitServiceMain()).execute(args);

        System.exit(exitCode);
    }

}
