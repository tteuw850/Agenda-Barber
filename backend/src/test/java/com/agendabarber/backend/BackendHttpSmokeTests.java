package com.agendabarber.backend;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendHttpSmokeTests {

    @Value("${local.server.port}")
    private int port;

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        URI uri = URI.create("http://127.0.0.1:" + port + path);
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void localHealthReturnsUp() throws Exception {
        HttpResponse<String> response = get("/actuator/health");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("UP"));
    }

    @Test
    void localOpenApiDocumentsService() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Agenda Barber API"));
        assertTrue(response.body().contains("openapi"));
    }

    @Test
    void localSwaggerUiIsAvailable() throws Exception {
        HttpResponse<String> response = get("/swagger-ui.html");
        assertTrue(response.statusCode() == 200 || response.statusCode() == 302
                || response.statusCode() == 303);
    }

    @Test
    void unknownBusinessRoutesAreDenied() throws Exception {
        HttpResponse<String> response = get("/api/appointments");
        assertEquals(403, response.statusCode());
    }
}
