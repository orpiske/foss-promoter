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
import org.apache.camel.component.micrometer.eventnotifier.MicrometerRouteEventNotifier;
import org.apache.camel.component.micrometer.routepolicy.MicrometerRoutePolicyFactory;
import org.apache.camel.opentelemetry.OpenTelemetryTracer;
import org.foss.promoter.commit.service.common.ContributionsDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.micrometer.MicrometerConstants.METRICS_REGISTRY_NAME;
import static org.foss.promoter.common.PrometheusRegistryUtil.getMetricRegistry;

public class CommitRoute extends RouteBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(CommitRoute.class);
    private final String bootstrapHost;
    private final int bootstrapPort;
    private final String cassandraServer;
    private final int cassandraPort;
    private final int imageSize;
    private final int consumersCount;

    public CommitRoute(String bootstrapHost, int bootstrapPort, String cassandraServer, int cassandraPort, int imageSize, int consumersCount) {
        this.bootstrapHost = bootstrapHost;
        this.bootstrapPort = bootstrapPort;
        this.cassandraServer = cassandraServer;
        this.cassandraPort = cassandraPort;
        this.imageSize = imageSize;
        this.consumersCount = consumersCount;
    }

    private void process(Exchange exchange) {
        String message = exchange.getMessage().getBody(String.class);
        LOG.debug("Generating for: {}", message);

        QRCodeWriter barcodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = null;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            bitMatrix = barcodeWriter.encode(message, BarcodeFormat.QR_CODE, imageSize, imageSize);
            final BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

            LOG.trace("Writing data");
            ImageIO.write(bufferedImage, "png", bos);
            LOG.trace("Done!");

            exchange.getMessage().setBody(Arrays.asList(message, ByteBuffer.wrap(bos.toByteArray())));
        } catch (WriterException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void enableTelemetry() {
        OpenTelemetryTracer tracer = new OpenTelemetryTracer();
        tracer.init(getCamelContext());
    }

    private void enableMetrics() {
        MeterRegistry registry = getMetricRegistry();

        // Add the registry
        getContext().getRegistry().bind(METRICS_REGISTRY_NAME, registry);

        // Expose route statistics
        getContext().addRoutePolicyFactory(new MicrometerRoutePolicyFactory());
        // Count added / running routes
        getContext().getManagementStrategy().addEventNotifier(new MicrometerRouteEventNotifier());
    }

    @Override
    public void configure() {
        enableMetrics();

        enableTelemetry();

        fromF("kafka:commits?brokers=%s:%d&consumersCount=%d&groupId=fp-commit-service", bootstrapHost, bootstrapPort, consumersCount)
                .routeId("commit-qr")
                .threads(3)
                .process(this::process)
                .toF("cql://%s:%d/%s?cql=%s", cassandraServer, cassandraPort, ContributionsDao.KEY_SPACE,
                        ContributionsDao.getInsertStatement());

    }
}
