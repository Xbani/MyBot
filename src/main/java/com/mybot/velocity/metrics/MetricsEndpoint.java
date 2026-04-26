package com.mybot.velocity.metrics;

import com.google.common.net.MediaType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Very small HTTP endpoint exposing metrics as JSON. Intended for on-box diagnostics.
 */
public final class MetricsEndpoint implements AutoCloseable {

    private final HttpServer server;
    private final BotMetrics metrics;

    public MetricsEndpoint(BotMetrics metrics, InetSocketAddress bindAddress, Logger logger) throws IOException {
        this.metrics = metrics;
        this.server = HttpServer.create(bindAddress, 0);
        this.server.createContext("/metrics", new Handler(metrics));
        this.server.setExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mybot-metrics");
            t.setDaemon(true);
            return t;
        }));
        logger.info("Metrics endpoint listening on {}", bindAddress);
        this.server.start();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static final class Handler implements HttpHandler {
        private final BotMetrics metrics;

        private Handler(BotMetrics metrics) {
            this.metrics = metrics;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String json = toJson(metrics.snapshot());
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", MediaType.JSON_UTF_8.toString());
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }

        private String toJson(Map<String, Object> map) {
            StringBuilder builder = new StringBuilder();
            builder.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append('"').append(entry.getKey()).append('"').append(':');
                builder.append('"').append(entry.getValue()).append('"');
            }
            builder.append('}');
            return builder.toString();
        }
    }
}
