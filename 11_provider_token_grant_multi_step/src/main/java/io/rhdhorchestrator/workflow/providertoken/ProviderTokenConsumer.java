package io.rhdhorchestrator.workflow.providertoken;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Experimental call-time adapter for read-only GitHub operations.
 *
 * The access token is deliberately kept in local variables only. It is not
 * returned from an operation, copied into workflow state, logged, or included
 * in an exception message. Each GitHub request obtains a token from the RHDH
 * secure-token-storage broker immediately before making the provider request.
 */
@ApplicationScoped
public class ProviderTokenConsumer {
    private static final String GITHUB_PROVIDER = "github";
    private static final String GITHUB_ACCEPT = "application/vnd.github+json";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ConfigProperty(name = "secure-token-storage.url")
    String tokenBrokerUrl;

    @ConfigProperty(name = "secure-token-storage.service-token")
    Optional<String> serviceToken;

    @ConfigProperty(name = "github.api-url")
    String githubApiUrl;

    @ConfigProperty(name = "github.api-version")
    String githubApiVersion;

    public Map<String, Object> getGithubProfile(String grantId, String provider) {
        JsonNode body = githubGet(grantId, provider, "/user");
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", body.path("id").asLong());
        profile.put("login", text(body, "login"));
        profile.put("name", text(body, "name"));
        profile.put("publicRepos", body.path("public_repos").asInt());
        profile.put("privateRepos", body.path("total_private_repos").asInt());
        return profile;
    }

    public List<Map<String, Object>> listGithubPrivateRepositories(
            String grantId, String provider, Integer page, Integer perPage) {
        JsonNode body = githubGet(grantId, provider, "/user/repos?visibility=private"
                + "&affiliation=owner%2Ccollaborator%2Corganization_member"
                + pageQuery(page, perPage, "&"));
        return repositorySummaries(body);
    }

    public List<Map<String, Object>> listGithubOrganizations(
            String grantId, String provider, Integer page, Integer perPage) {
        JsonNode body = githubGet(grantId, provider, "/user/orgs" + pageQuery(page, perPage, "?"));
        List<Map<String, Object>> organizations = new ArrayList<>();
        for (JsonNode organization : requireArray(body, "organizations")) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", organization.path("id").asLong());
            result.put("login", text(organization, "login"));
            result.put("url", text(organization, "url"));
            result.put("avatarUrl", text(organization, "avatar_url"));
            result.put("description", text(organization, "description"));
            organizations.add(result);
        }
        return organizations;
    }

    public List<Map<String, Object>> listGithubStarredRepositories(
            String grantId, String provider, Integer page, Integer perPage) {
        JsonNode body = githubGet(grantId, provider, "/user/starred" + pageQuery(page, perPage, "?"));
        return repositorySummaries(body);
    }

    private JsonNode githubGet(String grantId, String provider, String path) {
        requireInput(grantId, provider);
        URI endpoint = URI.create(apiUrl(path));
        try {
            // The broker call is intentionally inside this helper so every
            // GitHub request gets a current token immediately beforehand.
            String accessToken = requestAccessToken(grantId, provider);
            HttpResponse<String> response = sendGithubGet(endpoint, accessToken);
            if (response.statusCode() == 401) {
                // A grant can be accepted while the provider token is being
                // revoked. Re-acquire once, then fail without exposing token data.
                accessToken = requestAccessToken(grantId, provider);
                response = sendGithubGet(endpoint, accessToken);
            }
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("GitHub request failed with status " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub request failed", error);
        } catch (IOException error) {
            throw new IllegalStateException("GitHub request failed", error);
        }
    }

    private HttpResponse<String> sendGithubGet(URI endpoint, String accessToken)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Accept", GITHUB_ACCEPT)
                .header("X-GitHub-Api-Version", githubApiVersion)
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String requestAccessToken(String grantId, String provider) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "grantId", grantId,
                    "provider", provider));

            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(tokenBrokerUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));
            addServiceAuthorization(request);

            HttpResponse<String> response = httpClient.send(
                    request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Token broker rejected the grant request");
            }

            JsonNode body = objectMapper.readTree(response.body());
            JsonNode accessToken = body.get("accessToken");
            if (accessToken == null || !accessToken.isTextual() || accessToken.textValue().isBlank()) {
                throw new IllegalStateException("Token broker returned no access token");
            }
            return accessToken.textValue();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Token broker request failed", error);
        } catch (IOException error) {
            throw new IllegalStateException("Token broker request failed", error);
        }
    }

    private String apiUrl(String path) {
        String base = githubApiUrl.endsWith("/")
                ? githubApiUrl.substring(0, githubApiUrl.length() - 1)
                : githubApiUrl;
        return base + path;
    }

    private static String pageQuery(Integer page, Integer perPage, String separator) {
        int safePage = page == null ? 1 : page;
        int safePerPage = perPage == null ? 100 : perPage;
        if (safePage < 1 || safePerPage < 1 || safePerPage > 100) {
            throw new IllegalArgumentException("page must be positive and perPage must be between 1 and 100");
        }
        return separator + "page=" + safePage + "&per_page=" + safePerPage;
    }

    private static void addServiceAuthorization(HttpRequest.Builder request, Optional<String> serviceToken) {
        if (serviceToken.isEmpty() || serviceToken.get().isBlank()) {
            throw new IllegalStateException("SECURE_TOKEN_STORAGE_SERVICE_TOKEN is required");
        }
        request.header("Authorization", "Bearer " + serviceToken.get());
    }

    private void addServiceAuthorization(HttpRequest.Builder request) {
        addServiceAuthorization(request, serviceToken);
    }

    private static void requireInput(String grantId, String provider) {
        if (grantId == null || grantId.isBlank() || provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("grantId and provider are required");
        }
        if (!GITHUB_PROVIDER.equals(provider)) {
            throw new IllegalArgumentException("This prototype supports provider github only");
        }
    }

    private static JsonNode requireArray(JsonNode body, String description) {
        if (!body.isArray()) {
            throw new IllegalStateException("GitHub returned an invalid " + description + " response");
        }
        return body;
    }

    private static String text(JsonNode body, String field) {
        JsonNode value = body == null ? null : body.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static List<Map<String, Object>> repositorySummaries(JsonNode body) {
        List<Map<String, Object>> repositories = new ArrayList<>();
        for (JsonNode repository : requireArray(body, "repositories")) {
            repositories.add(repositorySummary(repository));
        }
        return repositories;
    }

    private static Map<String, Object> repositorySummary(JsonNode repository) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", repository.path("id").asLong());
        result.put("name", text(repository, "name"));
        result.put("fullName", text(repository, "full_name"));
        result.put("owner", text(repository.path("owner"), "login"));
        result.put("private", repository.path("private").asBoolean());
        result.put("visibility", text(repository, "visibility"));
        result.put("description", text(repository, "description"));
        result.put("defaultBranch", text(repository, "default_branch"));
        result.put("language", text(repository, "language"));
        result.put("archived", repository.path("archived").asBoolean());
        result.put("htmlUrl", text(repository, "html_url"));
        return result;
    }
}
