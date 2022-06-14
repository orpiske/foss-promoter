package org.foss.promoter.cli.main;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Callable;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.foss.promoter.cli.util.AddRepo;
import org.foss.promoter.common.data.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

public class FPMain implements Callable<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(FPMain.class);

    @CommandLine.Option(names = {"-r", "--repository"}, description = "The address of the repository to load", required = true)
    private String repository;

    @CommandLine.Option(names = {"-s", "--api-server"}, description = "The API server to use", defaultValue = "http://localhost:8080", required = true)
    private String apiServer;

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "display a help message")
    private boolean helpRequested = false;

    @Override
    public Integer call() {
        LOG.debug("Using repository: {}", repository);
        LOG.debug("Using server: {}", apiServer);

        final Repository repositoryBean = new Repository();

        repositoryBean.setName(repository);
        try {
            AddRepo.addRepo(apiServer, repositoryBean);
            LOG.info("Repository {} added successfully", repository);
            return 0;
        } catch (IOException | InterruptedException e) {
            if (LOG.isDebugEnabled()) {
                LOG.error("Unable to add repository: {}", e.getMessage(), e);
            } else {
                LOG.error("Unable to add repository: {}", e.getMessage());
            }

        }

        return 1;
    }


    public static void main(String[] args) {
        int exitCode = new CommandLine(new FPMain()).execute(args);

        System.exit(exitCode);
    }
}
