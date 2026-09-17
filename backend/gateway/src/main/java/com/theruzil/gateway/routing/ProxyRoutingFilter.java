package com.theruzil.gateway.routing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

import com.theruzil.gateway.config.GatewayProperties;
import com.theruzil.gateway.config.GatewayProperties.Route;
import com.theruzil.gateway.security.AuthenticatedUser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ProxyRoutingFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-User-Id";

    private static final Set<String> RESERVED_PATH_PREFIXES = Set.of("/auth/");

    private static final Set<String> REQUEST_HEADER_BLOCKLIST = Set.of(
            "authorization", "host", "content-length", "connection", "keep-alive", "transfer-encoding", "te",
            "trailer", "upgrade", "proxy-authenticate", "proxy-authorization", "accept-encoding", "expect",
            USER_ID_HEADER.toLowerCase(Locale.ROOT));

    private static final Set<String> RESPONSE_HEADER_BLOCKLIST = Set.of(
            "connection", "keep-alive", "transfer-encoding", "content-length");

    private final RestClient restClient;
    private final List<Route> routes;

    public ProxyRoutingFilter(RestClient restClient, GatewayProperties properties) {
        this.restClient = restClient;
        this.routes = List.copyOf(properties.getRoutes());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Route route = match(request.getRequestURI());
        if (route == null) {
            writeNotFound(response);
            return;
        }
        proxy(request, response, route);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/error") || path.equals("/auth")
                || RESERVED_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private Route match(String path) {
        return routes.stream().filter(route -> matchesPrefix(path, route.getPrefix())).findFirst().orElse(null);
    }

    private boolean matchesPrefix(String path, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return false;
        }
        String normalizedPrefix = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        return path.equals(normalizedPrefix) || path.startsWith(normalizedPrefix + "/");
    }

    private void proxy(HttpServletRequest request, HttpServletResponse response, Route route) throws IOException {
        String query = request.getQueryString();
        String targetUrl = route.getTargetBaseUrl() + request.getRequestURI() + (query == null ? "" : "?" + query);
        byte[] requestBody = request.getInputStream().readAllBytes();

        RestClient.RequestBodySpec bodySpec = restClient.method(HttpMethod.valueOf(request.getMethod()))
                .uri(URI.create(targetUrl))
                .headers(headers -> copyRequestHeaders(request, headers));
        RestClient.RequestHeadersSpec<?> headersSpec = requestBody.length > 0 ? bodySpec.body(requestBody) : bodySpec;

        ResponseEntity<byte[]> upstream;
        try {
            upstream = headersSpec.exchange((clientRequest, clientResponse) -> {
                byte[] responseBody;
                try {
                    responseBody = clientResponse.getBody().readAllBytes();
                }
                catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
                HttpHeaders responseHeaders = new HttpHeaders();
                clientResponse.getHeaders().forEach((name, values) -> {
                    if (isForwardableResponseHeader(name)) {
                        responseHeaders.put(name, values);
                    }
                });
                return ResponseEntity.status(clientResponse.getStatusCode()).headers(responseHeaders).body(responseBody);
            });
        }
        catch (ResourceAccessException | UncheckedIOException ex) {
            writeServiceUnavailable(response);
            return;
        }

        response.setStatus(upstream.getStatusCode().value());
        upstream.getHeaders().forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
        byte[] responseBody = upstream.getBody();
        if (responseBody != null) {
            response.setContentLength(responseBody.length);
            response.getOutputStream().write(responseBody);
        }
        response.flushBuffer();
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpHeaders headers) {
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (!isForwardableRequestHeader(name)) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                headers.add(name, values.nextElement());
            }
        }
        String userId = currentUserId();
        if (userId != null) {
            headers.set(USER_ID_HEADER, userId);
        }
    }

    private boolean isForwardableRequestHeader(String name) {
        return !REQUEST_HEADER_BLOCKLIST.contains(name.toLowerCase(Locale.ROOT));
    }

    private boolean isForwardableResponseHeader(String name) {
        return !RESPONSE_HEADER_BLOCKLIST.contains(name.toLowerCase(Locale.ROOT));
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return String.valueOf(user.id());
        }
        return null;
    }

    private void writeNotFound(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"not found\"}");
    }

    private void writeServiceUnavailable(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.BAD_GATEWAY.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"backend service unavailable\"}");
    }
}
