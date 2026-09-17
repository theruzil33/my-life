package com.theruzil.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import com.theruzil.gateway.auth.JwtService;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProxyRoutingTest {

    private static final HttpServer STUB_SERVER;

    private static final String STUB_BASE_URL;

    private static final AtomicInteger STUB_REQUEST_COUNT = new AtomicInteger();

    static {
        try {
            STUB_SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            STUB_SERVER.createContext("/", ProxyRoutingTest::handleStubRequest);
            STUB_SERVER.start();
            STUB_BASE_URL = "http://127.0.0.1:" + STUB_SERVER.getAddress().getPort();
        }
        catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @LocalServerPort
    private int gatewayPort;

    @Autowired
    private JwtService jwtService;

    @DynamicPropertySource
    static void gatewayRoutes(DynamicPropertyRegistry registry) {
        registry.add("gateway.routes[0].prefix", () -> "/tracker");
        registry.add("gateway.routes[0].target-base-url", () -> STUB_BASE_URL);
        registry.add("gateway.routes[1].prefix", () -> "/unavailable");
        registry.add("gateway.routes[1].target-base-url", () -> "http://127.0.0.1:1");
    }

    @AfterAll
    static void stopStubServer() {
        STUB_SERVER.stop(0);
    }

    @Test
    void routesRequestToConfiguredServicePreservingMethodBodyAndQuery() {
        String token = jwtService.issueAccessToken(42L);

        ResponseEntity<byte[]> response = call(HttpMethod.POST, "/tracker/items?filter=open", token,
                "{\"name\":\"shopping\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(response)).contains("\"method\":\"POST\"")
                .contains("\"path\":\"/tracker/items\"")
                .contains("\"query\":\"filter=open\"")
                .contains("\"body\":\"{\\\"name\\\":\\\"shopping\\\"}\"");
    }

    @Test
    void returnsUpstreamResponseWithoutDistortion() {
        String token = jwtService.issueAccessToken(42L);

        ResponseEntity<byte[]> response = call(HttpMethod.GET, "/tracker/teapot", token, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.I_AM_A_TEAPOT);
        assertThat(body(response)).isEqualTo("upstream-teapot");
        assertThat(response.getHeaders().getFirst("X-Upstream")).isEqualTo("yes");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
    }

    @Test
    void forwardsAuthenticatedUserIdToService() {
        String token = jwtService.issueAccessToken(42L);

        ResponseEntity<byte[]> response = call(HttpMethod.GET, "/tracker/whoami", token, null, "999");

        assertThat(body(response)).contains("\"userId\":\"42\"");
        assertThat(body(response)).contains("\"authorization\":\"null\"");
    }

    @Test
    void unknownPrefixReturnsNotFoundWithoutCallingService() {
        String token = jwtService.issueAccessToken(42L);
        int requestsBefore = STUB_REQUEST_COUNT.get();

        ResponseEntity<byte[]> response = call(HttpMethod.GET, "/unknown/path", token, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(STUB_REQUEST_COUNT.get()).isEqualTo(requestsBefore);
    }

    @Test
    void authenticationRouteIsHandledLocallyWithoutProxying() {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        int requestsBefore = STUB_REQUEST_COUNT.get();

        ResponseEntity<byte[]> response = call(HttpMethod.POST, "/auth/register", null,
                "{\"email\":\"" + email + "\",\"password\":\"password123\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(STUB_REQUEST_COUNT.get()).isEqualTo(requestsBefore);
    }

    @Test
    void unreachableServiceReturnsBadGatewayInsteadOfUnauthorized() {
        String token = jwtService.issueAccessToken(42L);

        ResponseEntity<byte[]> response = call(HttpMethod.GET, "/unavailable/items", token, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(body(response)).contains("backend service unavailable");
    }

    @Test
    void protectedRouteWithoutTokenIsRejectedBeforeProxying() {
        int requestsBefore = STUB_REQUEST_COUNT.get();

        ResponseEntity<byte[]> response = call(HttpMethod.GET, "/tracker/items", null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(STUB_REQUEST_COUNT.get()).isEqualTo(requestsBefore);
    }

    private ResponseEntity<byte[]> call(HttpMethod method, String path, String token, String requestBody) {
        return call(method, path, token, requestBody, null);
    }

    private ResponseEntity<byte[]> call(HttpMethod method, String path, String token, String requestBody,
            String spoofedUserId) {
        RestClient client = RestClient.create();
        RestClient.RequestBodySpec spec = client.method(method)
                .uri("http://localhost:" + gatewayPort + path)
                .headers(headers -> {
                    if (token != null) {
                        headers.setBearerAuth(token);
                    }
                    if (requestBody != null) {
                        headers.setContentType(MediaType.APPLICATION_JSON);
                    }
                    if (spoofedUserId != null) {
                        headers.set(ProxyRoutingFilter.USER_ID_HEADER, spoofedUserId);
                    }
                });
        RestClient.RequestHeadersSpec<?> headersSpec = requestBody == null ? spec : spec.body(requestBody);
        return headersSpec.exchange((clientRequest, clientResponse) -> {
            try {
                return ResponseEntity.status(clientResponse.getStatusCode())
                        .headers(clientResponse.getHeaders())
                        .body(clientResponse.getBody().readAllBytes());
            }
            catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
    }

    private String body(ResponseEntity<byte[]> response) {
        return new String(response.getBody(), StandardCharsets.UTF_8);
    }

    private static void handleStubRequest(HttpExchange exchange) throws IOException {
        STUB_REQUEST_COUNT.incrementAndGet();
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        String path = exchange.getRequestURI().getPath();

        if (path.equals("/tracker/teapot")) {
            byte[] body = "upstream-teapot".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.getResponseHeaders().add("X-Upstream", "yes");
            exchange.sendResponseHeaders(418, body.length);
            writeBody(exchange, body);
            return;
        }

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("method", exchange.getRequestMethod());
        payload.put("path", path);
        payload.put("query", String.valueOf(exchange.getRequestURI().getRawQuery()));
        payload.put("userId", String.valueOf(exchange.getRequestHeaders().getFirst("X-User-Id")));
        payload.put("authorization", String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
        payload.put("body", new String(requestBody, StandardCharsets.UTF_8));

        byte[] body = toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        writeBody(exchange, body);
    }

    private static void writeBody(HttpExchange exchange, byte[] body) throws IOException {
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static String toJson(Map<String, String> payload) {
        StringBuilder json = new StringBuilder("{");
        payload.forEach((key, value) -> {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append('"').append(key).append("\":\"").append(escape(value)).append('"');
        });
        return json.append('}').toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
