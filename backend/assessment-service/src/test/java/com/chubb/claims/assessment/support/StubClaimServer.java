package com.chubb.claims.assessment.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * A tiny in-process stand-in for claim-service, so assessment-service's real HTTP client, token forwarding and error
 * translation can be tested over a real socket. Routes are matched on "METHOD /path" (query ignored); every request
 * is recorded for assertions. Unstubbed routes answer 404 with claim-service's error shape.
 */
public final class StubClaimServer implements AutoCloseable {

    private static final StubClaimServer SHARED = new StubClaimServer();

    private final HttpServer server;
    private final Map<String, Function<RecordedRequest, StubResponse>> routes = new ConcurrentHashMap<>();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();

    public StubClaimServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Could not start the stub claim server", e);
        }
        server.setExecutor(Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "stub-claim-server");
            thread.setDaemon(true);
            return thread;
        }));
        server.createContext("/", this::handle);
        server.start();
    }

    /** One server for the whole test JVM, so Spring contexts that point at it can be cached and shared. */
    public static StubClaimServer shared() {
        return SHARED;
    }

    public String baseUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    public void reset() {
        routes.clear();
        requests.clear();
    }

    public void on(String method, String path, int status, String jsonBody) {
        on(method, path, request -> new StubResponse(status, jsonBody, Duration.ZERO));
    }

    public void on(String method, String path, Function<RecordedRequest, StubResponse> handler) {
        routes.put(key(method, path), handler);
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    public RecordedRequest lastRequest() {
        return requests.get(requests.size() - 1);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> headers = new ConcurrentHashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT), values.get(0)));
        RecordedRequest request = new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getRawQuery(), headers, body);
        requests.add(request);

        StubResponse response = routes
                .getOrDefault(key(request.method(), request.path()),
                        r -> new StubResponse(404, "{\"message\":\"no stub for " + r.method() + " " + r.path() + "\"}", Duration.ZERO))
                .apply(request);

        if (!response.delay().isZero()) {
            try {
                Thread.sleep(response.delay());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        byte[] payload = response.body() == null ? new byte[0] : response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), payload.length == 0 ? -1 : payload.length);
        if (payload.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        }
        exchange.close();
    }

    private static String key(String method, String path) {
        return method.toUpperCase(Locale.ROOT) + " " + path;
    }

    public record RecordedRequest(String method, String path, String query, Map<String, String> headers, String body) {

        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    public record StubResponse(int status, String body, Duration delay) {
    }
}
