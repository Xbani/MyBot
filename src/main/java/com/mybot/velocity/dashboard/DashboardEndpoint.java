package com.mybot.velocity.dashboard;

import com.google.common.net.MediaType;
import com.mybot.velocity.config.GlobalConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;

public final class DashboardEndpoint implements AutoCloseable {
    private final HttpServer server;

    public DashboardEndpoint(DashboardStateProvider stateProvider,
                             GlobalConfig.DashboardConfig config,
                             Logger logger) throws IOException {
        Objects.requireNonNull(stateProvider, "stateProvider");
        Objects.requireNonNull(config, "config");
        this.server = HttpServer.create(new InetSocketAddress(config.host(), config.port()), 0);
        RemoteAggregator aggregator = new RemoteAggregator(stateProvider, config.sources(), logger);
        this.server.createContext("/api/local/state", new LocalStateHandler(stateProvider, config.authToken()));
        this.server.createContext("/api/state", new AggregatedStateHandler(aggregator, config.authToken()));
        this.server.createContext("/metrics", new LocalStateHandler(stateProvider, config.authToken()));
        this.server.createContext("/", new StaticHandler());
        this.server.setExecutor(Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "mybot-dashboard");
            t.setDaemon(true);
            return t;
        }));
        logger.info("Dashboard endpoint listening on {}:{}", config.host(), config.port());
        this.server.start();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private static boolean authorized(HttpExchange exchange, String token) {
        if (token == null || token.isBlank()) {
            return true;
        }
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        return ("Bearer " + token).equals(auth);
    }

    private static void write(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private static void methodAllowed(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
    }

    private static final class LocalStateHandler implements HttpHandler {
        private final DashboardStateProvider stateProvider;
        private final String token;

        private LocalStateHandler(DashboardStateProvider stateProvider, String token) {
            this.stateProvider = stateProvider;
            this.token = token;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            methodAllowed(exchange);
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                return;
            }
            if (!authorized(exchange, token)) {
                exchange.sendResponseHeaders(401, -1);
                return;
            }
            byte[] body = stateProvider.localStateJson().getBytes(StandardCharsets.UTF_8);
            write(exchange, 200, MediaType.JSON_UTF_8.toString(), body);
        }
    }

    private static final class AggregatedStateHandler implements HttpHandler {
        private final RemoteAggregator aggregator;
        private final String token;

        private AggregatedStateHandler(RemoteAggregator aggregator, String token) {
            this.aggregator = aggregator;
            this.token = token;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            methodAllowed(exchange);
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                return;
            }
            if (!authorized(exchange, token)) {
                exchange.sendResponseHeaders(401, -1);
                return;
            }
            byte[] body = aggregator.stateJson().getBytes(StandardCharsets.UTF_8);
            write(exchange, 200, MediaType.JSON_UTF_8.toString(), body);
        }
    }

    static final class RemoteAggregator {
        private final DashboardStateProvider stateProvider;
        private final List<GlobalConfig.DashboardSource> sources;
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(750))
                .build();
        private final Logger logger;

        RemoteAggregator(DashboardStateProvider stateProvider, List<GlobalConfig.DashboardSource> sources, Logger logger) {
            this.stateProvider = stateProvider;
            this.sources = List.copyOf(sources);
            this.logger = logger;
        }

        String stateJson() {
            JsonWriter json = new JsonWriter();
            json.beginObject()
                    .name("generatedAt").string(Instant.now().toString()).comma()
                    .name("sources").beginArray();
            json.beginObject()
                    .name("id").string("local").comma()
                    .name("name").string("Local proxy").comma()
                    .name("status").string("online").comma()
                    .name("state").raw(stateProvider.localStateJson())
                    .endObject();
            for (GlobalConfig.DashboardSource source : sources) {
                json.comma();
                appendRemote(json, source);
            }
            json.endArray().endObject();
            return json.json();
        }

        private void appendRemote(JsonWriter json, GlobalConfig.DashboardSource source) {
            String url = source.baseUrl().replaceAll("/+$", "") + "/api/local/state";
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMillis(1200))
                        .GET();
                if (!source.token().isBlank()) {
                    builder.header("Authorization", "Bearer " + source.token());
                }
                HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() >= 200 && response.statusCode() < 300 && looksLikeJsonObject(response.body())) {
                    json.beginObject()
                            .name("id").string(source.id()).comma()
                            .name("name").string(source.name()).comma()
                            .name("status").string("online").comma()
                            .name("state").raw(response.body())
                            .endObject();
                } else {
                    appendOffline(json, source, "HTTP " + response.statusCode());
                }
            } catch (Exception ex) {
                logger.debug("Dashboard source {} unavailable", source.id(), ex);
                appendOffline(json, source, ex.getClass().getSimpleName());
            }
        }

        private boolean looksLikeJsonObject(String body) {
            String trimmed = body == null ? "" : body.trim();
            return trimmed.startsWith("{") && trimmed.endsWith("}");
        }

        private void appendOffline(JsonWriter json, GlobalConfig.DashboardSource source, String error) {
            json.beginObject()
                    .name("id").string(source.id()).comma()
                    .name("name").string(source.name()).comma()
                    .name("status").string("offline").comma()
                    .name("error").string(error)
                    .endObject();
        }
    }

    private static final class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            methodAllowed(exchange);
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String resource = switch (path) {
                case "/", "/index.html" -> "dashboard/index.html";
                case "/app.css" -> "dashboard/app.css";
                case "/app.js" -> "dashboard/app.js";
                default -> "";
            };
            if (resource.isBlank()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            try (InputStream stream = DashboardEndpoint.class.getClassLoader().getResourceAsStream(resource)) {
                if (stream == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                byte[] body = stream.readAllBytes();
                write(exchange, 200, contentType(resource), body);
            }
        }

        private String contentType(String resource) {
            String lower = resource.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".css")) {
                return "text/css; charset=utf-8";
            }
            if (lower.endsWith(".js")) {
                return "application/javascript; charset=utf-8";
            }
            return "text/html; charset=utf-8";
        }
    }
}
