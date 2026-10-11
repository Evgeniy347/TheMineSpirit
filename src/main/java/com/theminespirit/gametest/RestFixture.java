package com.theminespirit.gametest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.neoforge.rest.RestRouter;
import com.theminespirit.neoforge.rest.RestServer;
import com.theminespirit.neoforge.rest.SpiritBotGateway;
import com.theminespirit.neoforge.rest.SpiritIndex;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;

final class RestFixture {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    record Reply(int status, String body, HttpHeaders headers) {
        JsonNode json() {
            try {
                return MAPPER.readTree(body);
            } catch (IOException e) {
                throw new IllegalStateException("Ответ REST-моста не разобран как JSON: " + body, e);
            }
        }
    }

    static final class Problems {
        private final List<String> found = new ArrayList<>();

        void expect(boolean condition, String message) {
            if (!condition) {
                found.add(message);
            }
        }

        List<String> found() {
            return found;
        }
    }

    private final SpiritIndex index;
    private final SpiritBotGateway gateway;
    private final RestServer server;
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    private RestFixture(SpiritIndex index, SpiritBotGateway gateway, RestServer server) {
        this.index = index;
        this.gateway = gateway;
        this.server = server;
    }

    static InetAddress loopback() throws UnknownHostException {
        return InetAddress.getByName("127.0.0.1");
    }

    static RestFixture start(MinecraftServer minecraft) throws IOException {
        SpiritIndex index = new SpiritIndex(minecraft);
        index.register(NeoForge.EVENT_BUS);
        SpiritBotGateway gateway = new SpiritBotGateway(minecraft, index);
        RestServer server = RestServer.start(loopback(), 0, RestRouter.create(gateway));
        return new RestFixture(index, gateway, server);
    }

    static void await(GameTestHelper helper, RestFixture rest, CompletableFuture<List<String>> scenario) {
        CompletableFuture<List<String>> outcome = scenario.exceptionally(
                error -> List.of("сценарий завершился исключением: " + error));
        helper.succeedWhen(() -> {
            helper.assertTrue(outcome.isDone(),
                    "REST-сценарий не завершился: ответ моста не пришёл, серверный поток не отвечает или запрос завис");
            rest.close();
            helper.assertTrue(outcome.join().isEmpty(),
                    "REST-сценарий нарушил контракт: " + String.join("; ", outcome.join()));
        });
    }

    SpiritIndex index() {
        return index;
    }

    SpiritBotGateway gateway() {
        return gateway;
    }

    RestServer server() {
        return server;
    }

    CompletableFuture<Reply> get(String path) {
        return send(HttpRequest.newBuilder(uri(path)).GET().build());
    }

    CompletableFuture<Reply> post(String path, byte[] body) {
        return send(HttpRequest.newBuilder(uri(path)).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build());
    }

    void close() {
        index.unregister(NeoForge.EVENT_BUS);
        server.stop();
        client.shutdown();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.port() + path);
    }

    private CompletableFuture<Reply> send(HttpRequest request) {
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> new Reply(response.statusCode(), response.body(), response.headers()));
    }
}
