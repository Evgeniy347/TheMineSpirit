package com.theminespirit.gametest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.task.TaskRegistry;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;

final class RestFixture {
    // Тики сервера GameTest идут без пауз, а ответ моста приходит по HTTP за реальное время: срок ответа считается
    // в секундах (REPLY_DEADLINE), потолок тиков только запасной и заведомо больше любого честного ожидания.
    static final int TIMEOUT_TICKS = 20_000;
    static final Duration REPLY_DEADLINE = Duration.ofSeconds(30);
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

    private final MinecraftServer minecraft;
    private final SpiritIndex index;
    private final SpiritBotGateway gateway;
    private final RestServer server;
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final List<SpiritEntity> spirits = new ArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    private RestFixture(MinecraftServer minecraft, SpiritIndex index, SpiritBotGateway gateway, RestServer server) {
        this.minecraft = minecraft;
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
        SpiritBotGateway gateway = new SpiritBotGateway(minecraft, index, NeoForge.EVENT_BUS, new TaskRegistry());
        RestServer server = RestServer.start(loopback(), 0, RestRouter.create(gateway), gateway::stop);
        return new RestFixture(minecraft, index, gateway, server);
    }

    static void await(GameTestHelper helper, RestFixture rest, CompletableFuture<List<String>> scenario) {
        CompletableFuture<List<String>> outcome = scenario.exceptionally(
                error -> List.of("сценарий завершился исключением: " + error));
        long started = System.nanoTime();
        helper.onEachTick(() -> {
            if (outcome.isDone() && !outcome.join().isEmpty()) {
                rest.close();
                throw new IllegalStateException("REST-сценарий нарушил контракт: " + String.join("; ", outcome.join()));
            }
            if (!outcome.isDone() && System.nanoTime() - started > REPLY_DEADLINE.toNanos()) {
                rest.close();
                throw new IllegalStateException("REST-сценарий не завершился за " + REPLY_DEADLINE.toSeconds()
                        + " с реального времени: ответ моста не пришёл, серверный поток не отвечает или запрос завис");
            }
        });
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

    CompletableFuture<Reply> post(String path, String json) {
        return post(path, json.getBytes(StandardCharsets.UTF_8));
    }

    CompletableFuture<Reply> delete(String path) {
        return send(HttpRequest.newBuilder(uri(path)).DELETE().build());
    }

    <T> CompletableFuture<T> onServer(Supplier<T> read) {
        return CompletableFuture.supplyAsync(read, minecraft);
    }

    SpiritEntity spawn(GameTestHelper helper, BlockPos relative) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        spirits.add(spirit);
        return spirit;
    }

    void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        spirits.forEach(SpiritEntity::discard);
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
