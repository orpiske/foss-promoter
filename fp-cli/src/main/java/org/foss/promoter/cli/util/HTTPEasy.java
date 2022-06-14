package org.foss.promoter.cli.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public final class HTTPEasy {


    private HTTPEasy() {

    }

    public static HttpResponse<String> post(final String requestUrl, String body) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .setHeader("Accept", "application/json")
                .setHeader("Content-Type", "application/json")
                .uri(URI.create(requestUrl))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());


    }
}
