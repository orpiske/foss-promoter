package org.foss.promoter.commit.service.routes;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.imageio.ImageIO;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.component.micrometer.eventnotifier.MicrometerRouteEventNotifier;
import org.apache.camel.component.micrometer.routepolicy.MicrometerRoutePolicyFactory;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.foss.promoter.commit.service.common.ContributionsDao;
import org.foss.promoter.common.data.CommitInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommitRoute extends RouteBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(CommitRoute.class);
    private final String bootstrapHost;
    private final int bootstrapPort;
    private final String cassandraServer;
    private final int cassandraPort;
    private final int imageSize;
    private final int consumersCount;
    private final MeterRegistry registry;

    public CommitRoute(String bootstrapHost, int bootstrapPort, String cassandraServer, int cassandraPort, int imageSize, int consumersCount, MeterRegistry registry) {
        this.bootstrapHost = bootstrapHost;
        this.bootstrapPort = bootstrapPort;
        this.cassandraServer = cassandraServer;
        this.cassandraPort = cassandraPort;
        this.imageSize = imageSize;
        this.consumersCount = consumersCount;
        this.registry = registry;
    }

    private void process(Exchange exchange) {
        CommitInfo commitInfo = exchange.getMessage().getBody(CommitInfo.class);
        String message = commitInfo.getMessage();

        LOG.debug("Generating for: {}", message);

        if (message == null || message.isBlank()) {
            exchange.getMessage().setHeader("valid-message", false);

            return;
        }

        exchange.getMessage().setHeader("valid-message", true);

        QRCodeWriter barcodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = null;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            bitMatrix = barcodeWriter.encode(message, BarcodeFormat.QR_CODE, imageSize, imageSize);
            final BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

            LOG.trace("Writing data");
            ImageIO.write(bufferedImage, "png", bos);
            LOG.trace("Done!");

            exchange.getMessage().setBody(Arrays.asList(commitInfo.getProjectName(), commitInfo.getAuthorName(), commitInfo.getAuthorEmail(), commitInfo.getDate(), message, ByteBuffer.wrap(bos.toByteArray())));
        } catch (WriterException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void configure() {
        // Add the registry
        getContext().getRegistry().bind(MicrometerConstants.METRICS_REGISTRY_NAME, registry);
        // Expose route statistics
        getContext().addRoutePolicyFactory(new MicrometerRoutePolicyFactory());
        // Count added / running routes
        getContext().getManagementStrategy().addEventNotifier(new MicrometerRouteEventNotifier());

        fromF("kafka:commits?brokers=%s:%d&consumersCount=%d&groupId=fp-commit-service", bootstrapHost, bootstrapPort, consumersCount)
                .routeId("commit-qr")
                .threads(3)
                .unmarshal().json(JsonLibrary.Jackson, CommitInfo.class)
                .process(this::process)
                .choice()
                .when(header("valid-message").isEqualTo(true))
                    .toF("cql://%s:%d/%s?cql=%s", cassandraServer, cassandraPort, ContributionsDao.KEY_SPACE,
                        ContributionsDao.getInsertStatement())
                .endChoice();

    }
}
