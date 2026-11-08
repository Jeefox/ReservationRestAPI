package grevcev;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class DevProfileIntegrationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");
    @Container static final KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");
    @LocalServerPort private int port;

    @DynamicPropertySource
    static void dependencies(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    void devStartsOnEmptyDatabaseWithSwaggerAndWorkingAdmin() throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        var swagger = client.send(request("/swagger-ui.html").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(swagger.statusCode()).isEqualTo(200);
        assertThat(swagger.body()).contains("Swagger UI");
        var login = client.send(request("/api/v1/auth/login").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"email":"admin@admin.com","password":"Admin123"}
                        """)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(200);
        String token = JsonPath.read(login.body(), "$.token");
        var rooms = client.send(request("/api/v1/rooms").header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(rooms.statusCode()).isEqualTo(200);
        assertThat(rooms.body()).contains("luxury", "casual");
    }

    @Test
    void publishedPostmanRequestsCompleteBookingLifecycle() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var collection = mapper.readTree(java.nio.file.Path.of("docs/postman-collection.json").toFile());
        var variables = new java.util.HashMap<String, String>();
        variables.put("baseUrl", "http://localhost:" + port);
        variables.put("startDate", java.time.LocalDate.now().plusDays(30).toString());
        variables.put("endDate", java.time.LocalDate.now().plusDays(33).toString());
        HttpClient client = HttpClient.newHttpClient();
        for (var item : collection.get("item")) {
            var spec = item.get("request");
            String url = expand(spec.get("url").asText(), variables);
            String body = spec.has("body") ? expand(spec.get("body").get("raw").asText(), variables) : "";
            var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json");
            if (variables.containsKey("token")) request.header("Authorization", "Bearer " + variables.get("token"));
            request.method(spec.get("method").asText(), body.isEmpty() ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
            var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            String tests = item.get("event").toString();
            var expected = java.util.regex.Pattern.compile("HTTP (\\d+)").matcher(tests);
            assertThat(expected.find()).isTrue();
            assertThat(response.statusCode()).as(item.get("name").asText()).isEqualTo(Integer.parseInt(expected.group(1)));
            if (!response.body().isEmpty()) {
                var json = mapper.readTree(response.body());
                if (json.has("token")) variables.put("token", json.get("token").asText());
                if (spec.get("method").asText().equals("POST") && json.has("id")) {
                    variables.put(url.endsWith("/rooms") ? "roomId" : "reservationId", json.get("id").asText());
                }
            }
        }
    }

    private String expand(String value, java.util.Map<String, String> variables) {
        for (var entry : variables.entrySet()) value = value.replace("{{" + entry.getKey() + "}}", entry.getValue());
        return value.replace("{{$guid}}", java.util.UUID.randomUUID().toString());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(20));
    }
}
