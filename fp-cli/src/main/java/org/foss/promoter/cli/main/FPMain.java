package org.foss.promoter.cli.main;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import picocli.CommandLine;

public class FPMain implements Callable<Integer> {

    @CommandLine.Option(names = {"-r", "--repository"}, description = "The address of the repository to load")
    private String repository;

    @CommandLine.Option(names = {"-s", "--bootstrap-server"}, description = "The Kafka bootstrap server to use")
    private String bootstrapServer;

    @CommandLine.Option(names = {"-p", "--bootstrap-server-port"}, description = "The Kafka bootstrap server port to use", defaultValue = "9092")
    private int bootstrapPort;

    @Override
    public Integer call() {
        System.out.println("Using repository: " + repository);
        System.out.println("Using server: " + bootstrapServer);
        System.out.println("Using port: " + bootstrapPort);

        Properties producerProperties =  createProducer(bootstrapServer + ":" + bootstrapPort);
        KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties);

        try {
            List<Exception> exceptions = new ArrayList<>();
            final Future<RecordMetadata> sendStatus = producer.send(new ProducerRecord<>("repositories", repository),
                    (recordMetadata, e) -> {
                        if (e != null) {
                            exceptions.add(e);
                        }
                    });

            sendStatus.get();
            exceptions.forEach(e -> System.err.println("Failed to send record: " + e.getMessage()));
            return exceptions.size();
        } catch (Exception e) {
            System.err.println("Failed to send record: " + e.getMessage());
            return 1;
        }
    }

    /**
     * Creates a basic string-based producer
     *
     * @param  bootstrapServers the Kafka host
     * @return                  A set of default properties for producing string-based key/pair records from Kafka
     */
    public Properties createProducer(String bootstrapServers) {
        Properties config = new Properties();

        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        return config;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new FPMain()).execute(args);

        System.exit(exitCode);
    }
}
