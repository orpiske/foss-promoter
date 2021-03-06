package org.foss.promoter.repo.service.main;

import java.util.concurrent.Callable;

import org.apache.camel.main.Main;
import org.foss.promoter.repo.service.routes.RepositoryRoute;
import picocli.CommandLine;

public class FPRepoServiceMain implements Callable<Integer> {
    @CommandLine.Option(names = {"-s", "--bootstrap-server"}, description = "The Kafka bootstrap server to use", required = true)
    private String bootstrapServer;

    @CommandLine.Option(names = {"-p", "--bootstrap-server-port"}, description = "The Kafka bootstrap server port to use", defaultValue = "9092")
    private int bootstrapPort;

    @CommandLine.Option(names = {"-d", "--data-directory"}, description = "The path where to store the downloaded repository", defaultValue = "/data")
    private String dataDir;

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "display a help message")
    private boolean helpRequested = false;

    @Override
    public Integer call() throws Exception {
        Main main = new Main();

        main.configure().addRoutesBuilder(new RepositoryRoute(bootstrapServer, bootstrapPort, dataDir));

        main.run();
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new FPRepoServiceMain()).execute(args);

        System.exit(exitCode);
    }

}
