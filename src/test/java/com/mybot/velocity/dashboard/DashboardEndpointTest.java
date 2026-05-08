package com.mybot.velocity.dashboard;

import com.mybot.velocity.config.GlobalConfig;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardEndpointTest {

    @Test
    void servesLocalStateAndStaticAssets() throws Exception {
        GlobalConfig.DashboardConfig config = new GlobalConfig.DashboardConfig(
                true, "127.0.0.1", 0, "", "", List.of());
        try (DashboardEndpoint endpoint = new DashboardEndpoint(() -> "{\"proxy\":{\"id\":\"local\"}}", config, NOPLogger.NOP_LOGGER)) {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + endpoint.port();

            HttpResponse<String> state = client.send(HttpRequest.newBuilder(URI.create(base + "/api/local/state")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> html = client.send(HttpRequest.newBuilder(URI.create(base + "/")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> js = client.send(HttpRequest.newBuilder(URI.create(base + "/app.js")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(state.statusCode()).isEqualTo(200);
            assertThat(state.body()).contains("\"proxy\"");
            assertThat(html.statusCode()).isEqualTo(200);
            assertThat(html.body()).contains("MyBot Dashboard");
            assertThat(js.statusCode()).isEqualTo(200);
            assertThat(js.body()).contains("fetchState");
        }
    }

    @Test
    void aggregatesOfflineSourcesWithoutFailingWholeResponse() {
        DashboardEndpoint.RemoteAggregator aggregator = new DashboardEndpoint.RemoteAggregator(
                () -> "{\"proxy\":{\"id\":\"local\"}}",
                List.of(new GlobalConfig.DashboardSource("bad", "Bad Proxy", "http://127.0.0.1:1", "")),
                NOPLogger.NOP_LOGGER);

        String json = aggregator.stateJson();

        assertThat(json).contains("\"id\":\"local\"");
        assertThat(json).contains("\"id\":\"bad\"");
        assertThat(json).contains("\"status\":\"offline\"");
    }
}
