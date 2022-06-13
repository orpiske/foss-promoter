package org.foss.promoter.repo.service.main;

import java.util.concurrent.Callable;

import org.apache.camel.main.Main;
import org.foss.promoter.repo.service.routes.RepositoryRoute;
import picocli.CommandLine;

public class FPRepoServiceMain implements Callable<Integer> {
    @CommandLine.Option(names = {"-r", "--repository"}, description = "The address of the repository to load")
    private String repository;

    @CommandLine.Option(names = {"-s", "--bootstrap-server"}, description = "The Kafka bootstrap server to use")
    private String bootstrapServer;

    @CommandLine.Option(names = {"-p", "--bootstrap-server-port"}, description = "The Kafka bootstrap server port to use", defaultValue = "9092")
    private int bootstrapPort;

    @Override
    public Integer call() throws Exception {
        Main main = new Main();

        main.configure().addRoutesBuilder(new RepositoryRoute(bootstrapServer, bootstrapPort));

        main.run();
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new FPRepoServiceMain()).execute(args);

        System.exit(exitCode);
    }

}
