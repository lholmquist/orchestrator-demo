package io.rhdhorchestrator.workflow.providertoken;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProviderTokenConsumerTest {
    private HttpServer server;
    private ProviderTokenConsumer consumer;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/secure-token-storage/token", exchange -> respond(
                exchange, 200, "{\"accessToken\":\"github-token\"}"));
        server.createContext("/", exchange -> {
            if ("/user/orgs?page=2&per_page=50".equals(exchange.getRequestURI().toString())) {
                respond(exchange, 200, "[{\"id\":7,\"login\":\"example-org\"}]");
            } else {
                respond(exchange, 404, "{\"message\":\"not found\"}");
            }
        });
        server.start();

        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        consumer = new ProviderTokenConsumer();
        consumer.tokenBrokerUrl = baseUrl + "/api/secure-token-storage/token";
        consumer.serviceToken = Optional.of("service-token");
        consumer.githubApiUrl = baseUrl;
        consumer.githubApiVersion = "2022-11-28";
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void listOrganizationsUsesQuestionMarkBeforePaginationParameters() {
        List<Map<String, Object>> organizations = consumer.listGithubOrganizations(
                "grant-id", "github", 2, 50);

        assertFalse(organizations.isEmpty());
        assertEquals("example-org", organizations.get(0).get("login"));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, response.length);
        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }
}
