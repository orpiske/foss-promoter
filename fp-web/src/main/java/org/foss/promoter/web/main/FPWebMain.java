package org.foss.promoter.web.main;

import java.util.concurrent.Callable;

import org.apache.camel.main.Main;
import org.foss.promoter.web.routes.RepositoryRoute;
import picocli.CommandLine;

@SuppressWarnings("unused")
public class FPWebMain implements Callable<Integer> {
    @CommandLine.Option(names = {"-sp", "--service-port"}, description = "The port to use for the web service", defaultValue = "8080", required = true)
    private int servicePort;

    @CommandLine.Option(names = {"-s", "--bootstrap-server"}, description = "The Kafka bootstrap server to use", required = true)
    private String bootstrapServer;

    @CommandLine.Option(names = {"-p", "--bootstrap-server-port"}, description = "The Kafka bootstrap server port to use", defaultValue = "9092")
    private int bootstrapPort;

    @CommandLine.Option(names = {"--commit-service-host"}, description = "The commit service host to use", defaultValue = "fp-commit-service")
    private String commitServiceHost;

    @CommandLine.Option(names = {"--commit-service-port"}, description = "The commit service port to use", defaultValue = "8080")
    private int commitServicePort;

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "display a help message")
    private boolean helpRequested = false;

    @Override
    public Integer call() throws Exception {
        Main main = new Main();

        main.configure().addRoutesBuilder(new RepositoryRoute(bootstrapServer, bootstrapPort, servicePort, commitServiceHost, commitServicePort));

        main.run();
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new FPWebMain()).execute(args);

        System.exit(exitCode);
    }
}
