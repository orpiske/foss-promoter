package org.foss.promoter.cli.util;

import java.io.IOException;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.foss.promoter.common.data.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddRepo {
    private static final Logger LOG = LoggerFactory.getLogger(AddRepo.class);

    public static void addRepo(String apiServer, Repository repository) throws IOException, InterruptedException {
        ObjectMapper mapper = new ObjectMapper();
        final String body = mapper.writeValueAsString(repository);
        LOG.trace("Sending record: {}", body);

        final HttpResponse<String> response = HTTPEasy.post(apiServer + "/api/repository", body);

        if (response.statusCode() != 200) {
            throw new RuntimeException("Unable to add repository: " + response.statusCode());
        }
    }
}
