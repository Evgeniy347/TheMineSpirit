package com.theminespirit.gametest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.rest.SseSlots;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

final class RestFixture {
    // [флак] Тики сервера GameTest идут без пауз, а ответ моста приходит по HTTP за реальное время: срок ответа
    // считается в секундах (REPLY_DEADLINE = 30 с), потолок тиков только запасной и заведомо больше любого
    // честного ожидания. Потолок не тиковый, поэтому правило «timeoutTicks ≈ 2× тиков духа» к нему не применимо.
    static final int TIMEOUT_TICKS = 20_000;
    static final int FOLLOW_RADIUS = 2;
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

    record Frame(String id, String event, String data) {
        long idNumber() {
            return Long.parseLong(id);
        }

        JsonNode json() {
            try {
                return MAPPER.readTree(data);
            } catch (IOException e) {
                throw new IllegalStateException("Данные SSE-кадра не разобраны как JSON: " + data, e);
            }
        }
    }

    static final class SseCapture {
        private final List<String> lines = new CopyOnWriteArrayList<>();
        private final AtomicReference<Stream<String>> body = new AtomicReference<>();
        private final CompletableFuture<Integer> status = new CompletableFuture<>();
        private final CompletableFuture<Void> finished = new CompletableFuture<>();
        private volatile String contentType;

        CompletableFuture<Integer> status() {
            return status;
        }

        boolean finished() {
            return finished.isDone();
        }

        CompletableFuture<Void> whenFinished() {
            return finished;
        }

        String contentType() {
            return contentType;
        }

        List<String> lines() {
            return List.copyOf(lines);
        }

        List<Frame> frames() {
            List<Frame> frames = new ArrayList<>();
            String id = null;
            String event = null;
            String data = null;
            for (String line : lines()) {
                if (line.isEmpty()) {
                    if (event != null) {
                        frames.add(new Frame(id, event, data));
                    }
                    id = null;
                    event = null;
                    data = null;
                } else if (line.startsWith("id: ")) {
                    id = line.substring("id: ".length());
                } else if (line.startsWith("event: ")) {
                    event = line.substring("event: ".length());
                } else if (line.startsWith("data: ")) {
                    data = line.substring("data: ".length());
                }
            }
            return frames;
        }

        void cancel() {
            Stream<String> open = body.get();
            if (open != null) {
                open.close();
            }
        }

        private void read(HttpResponse<Stream<String>> response) {
            contentType = response.headers().firstValue("Content-Type").orElse("");
            status.complete(response.statusCode());
            try (Stream<String> stream = response.body()) {
                body.set(stream);
                stream.forEach(lines::add);
                finished.complete(null);
            } catch (RuntimeException e) {
                finished.completeExceptionally(e);
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
    private final List<ServerPlayer> players = new ArrayList<>();
    private int playerCounter;
    private final SseSlots slots;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ExecutorService readers = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "rest-gametest-sse-reader");
        thread.setDaemon(true);
        return thread;
    });

    private RestFixture(MinecraftServer minecraft, SpiritIndex index, SpiritBotGateway gateway, RestServer server,
            SseSlots slots) {
        this.minecraft = minecraft;
        this.slots = slots;
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
        SseSlots slots = new SseSlots();
        RestServer server = RestServer.start(loopback(), 0, RestRouter.create(gateway, slots), gateway::stop);
        return new RestFixture(minecraft, index, gateway, server, slots);
    }

    static void await(GameTestHelper helper, RestFixture rest, CompletableFuture<List<String>> scenario) {
        await(helper, rest, scenario, () -> { });
    }

    static void await(GameTestHelper helper, RestFixture rest, CompletableFuture<List<String>> scenario,
            Runnable worldChecks) {
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
            worldChecks.run();
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

    SseCapture stream(String path, Map<String, String> headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
        headers.forEach(request::header);
        SseCapture capture = new SseCapture();
        client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofLines()).whenComplete((response, error) -> {
            if (error != null) {
                capture.status.completeExceptionally(error);
                capture.finished.completeExceptionally(error);
                return;
            }
            readers.execute(() -> capture.read(response));
        });
        return capture;
    }

    SpiritEntity spawn(GameTestHelper helper, BlockPos relative) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        spirits.add(spirit);
        return spirit;
    }

    SpiritEntity spawnHeldUntilStreamsOpen(GameTestHelper helper, BlockPos relative, int streams) {
        SpiritEntity spirit = spawn(helper, relative);
        spirit.setNoAi(true);
        AtomicBoolean released = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (slots.open() >= streams && released.compareAndSet(false, true)) {
                spirit.setNoAi(false);
            }
        });
        return spirit;
    }

    // helper.makeMockServerPlayerInLevel() называет всех игроков test-mock-player, а follow берёт первого игрока
    // с таким именем: протёкший из упавшего соседнего теста игрок уводит духа к чужой цели. GameTestFixtures.mockPlayer
    // даёт уникальное имя теста и снимает игрока при любом исходе через слушателя (образец — 65406d0, 3130b96).
    ServerPlayer mockPlayer(GameTestHelper helper, BlockPos relative) {
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, relative, "gt_rest_player_" + playerCounter++);
        players.add(player);
        return player;
    }

    static void moveTo(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
    }

    static String followGoalBody(String requestId, ServerPlayer player) {
        return "{\"request_id\":\"" + requestId + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\""
                + player.getName().getString() + "\"}}";
    }

    static TaskNode followPlan(ServerPlayer player) {
        return new ScriptCall("follow", Map.of("target", player.getName().getString(), "radius", FOLLOW_RADIUS));
    }

    SpiritEntity track(SpiritEntity spirit) {
        spirits.add(spirit);
        return spirit;
    }

    void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        spirits.forEach(SpiritEntity::discard);
        players.forEach(player -> minecraft.getPlayerList().remove(player));
        index.unregister(NeoForge.EVENT_BUS);
        server.stop();
        client.shutdownNow();
        readers.shutdownNow();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.port() + path);
    }

    private CompletableFuture<Reply> send(HttpRequest request) {
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> new Reply(response.statusCode(), response.body(), response.headers()));
    }
}
