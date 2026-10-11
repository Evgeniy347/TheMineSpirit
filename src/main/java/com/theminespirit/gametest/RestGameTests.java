package com.theminespirit.gametest;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.rest.SpiritIndex;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RestGameTests {
    private static final int FLOOR_SIZE = 3;
    private static final int OVERSIZED_BODY_BYTES = 2 * 1024 * 1024;
    private static final int FAR_TARGET_BLOCKS = 40;
    private static final String UNKNOWN_ID = "00000000-0000-0000-0000-000000000000";

    private RestGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void capabilities_ImplementedScripts_ListedWithSchemaWithoutUnimplemented(GameTestHelper helper)
            throws IOException {
        RestFixture rest = RestFixture.start(helper.getLevel().getServer());
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest.get("/v1/capabilities").thenApply(reply -> {
            problems.expect(reply.status() == 200,
                    "GET /v1/capabilities обязан отвечать 200, получен " + reply.status() + ": " + reply.body());
            JsonNode scripts = reply.json().path("scripts");
            List<String> ids = new ArrayList<>();
            scripts.forEach(script -> ids.add(script.path("id").asText()));
            problems.expect(ids.contains("goto") && ids.contains("follow"),
                    "Каталог обязан содержать реализованные скрипты goto и follow, получено " + ids);
            problems.expect(!ids.contains("patrol"),
                    "patrol не реализован в SpiritScripts и не должен обещаться ИИ в capabilities, получено " + ids);
            List<String> required = new ArrayList<>();
            for (JsonNode script : scripts) {
                if (script.path("id").asText().equals("goto")) {
                    script.path("params").path("required").forEach(name -> required.add(name.asText()));
                }
            }
            problems.expect(required.equals(List.of("x", "y", "z")),
                    "У goto обязательны x, y, z (required в JSON-схеме), получено " + required);
            return problems.found();
        });
        RestFixture.await(helper, rest, scenario);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void bots_SpawnedThenDiscardedSpirit_ListedThenAbsent(GameTestHelper helper) throws IOException {
        MinecraftServer minecraft = helper.getLevel().getServer();
        RestFixture rest = RestFixture.start(minecraft);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        String id = spirit.getStringUUID();
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest.get("/v1/bots")
                .thenCompose(first -> {
                    problems.expect(first.status() == 200 && listsId(first.json(), id),
                            "Заспавненный дух " + id + " обязан быть в GET /v1/bots: " + first.body());
                    return CompletableFuture.runAsync(spirit::discard, minecraft);
                })
                .thenCompose(discarded -> rest.get("/v1/bots"))
                .thenApply(second -> {
                    problems.expect(second.status() == 200 && !listsId(second.json(), id),
                            "Удалённый дух " + id + " не должен оставаться в GET /v1/bots: " + second.body());
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void botState_IdleThenGotoStarted_ReportsIdleThenBusy(GameTestHelper helper) throws IOException {
        MinecraftServer minecraft = helper.getLevel().getServer();
        buildFloor(helper);
        RestFixture rest = RestFixture.start(minecraft);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        BlockPos spawned = spirit.blockPosition();
        BlockPos far = helper.absolutePos(new BlockPos(FAR_TARGET_BLOCKS, 1, 1));
        String path = "/v1/bots/" + spirit.getStringUUID();
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest.get(path)
                .thenCompose(idle -> {
                    JsonNode bot = idle.json();
                    problems.expect(idle.status() == 200 && bot.path("state").asText().equals("idle"),
                            "Дух без задачи обязан иметь state=idle: " + idle.body());
                    problems.expect(bot.path("position").toString()
                                    .equals("[" + spawned.getX() + "," + spawned.getY() + "," + spawned.getZ() + "]"),
                            "position обязана совпадать с блоком духа " + spawned + ": " + idle.body());
                    problems.expect(bot.path("dimension").asText().equals("minecraft:overworld"),
                            "dimension обязано называть измерение духа: " + idle.body());
                    problems.expect(bot.path("current_task").isNull(),
                            "current_task до чеклиста 15 обязан быть null: " + idle.body());
                    return CompletableFuture.runAsync(() -> spirit.startTask(new ScriptCall("goto",
                            Map.of("x", far.getX(), "y", far.getY(), "z", far.getZ()))), minecraft);
                })
                .thenCompose(started -> rest.get(path))
                .thenApply(busy -> {
                    problems.expect(busy.json().path("state").asText().equals("busy"),
                            "Дух с идущей задачей goto обязан иметь state=busy: " + busy.body());
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void bot_UnknownId_Returns404NotFoundObject(GameTestHelper helper) throws IOException {
        RestFixture rest = RestFixture.start(helper.getLevel().getServer());
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest.get("/v1/bots/" + UNKNOWN_ID)
                .thenCombine(rest.get("/v1/bots/not-a-uuid"), (unknown, notUuid) -> {
                    JsonNode error = unknown.json().path("error");
                    problems.expect(unknown.status() == 404 && error.path("code").asText().equals("not_found"),
                            "Неизвестный UUID обязан давать 404 not_found: " + unknown.body());
                    problems.expect(error.path("message").asText().contains(UNKNOWN_ID),
                            "Сообщение not_found обязано называть искомый id: " + unknown.body());
                    problems.expect(error.path("details").isObject(), "details обязан быть объектом: " + unknown.body());
                    problems.expect(notUuid.status() == 404 && notUuid.json().path("error").path("code").asText()
                                    .equals("not_found"),
                            "id не в формате UUID — тоже 404 not_found, а не 500: " + notUuid.body());
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void observe_RadiusEightAndInvalidRadius_ReturnsObservationAnd400(GameTestHelper helper)
            throws IOException {
        buildFloor(helper);
        RestFixture rest = RestFixture.start(helper.getLevel().getServer());
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        String id = spirit.getStringUUID();
        String path = "/v1/bots/" + id + "/observe";
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest.post(path, "{\"radius\":8}".getBytes(StandardCharsets.UTF_8))
                .thenCombine(rest.post(path, "{\"radius\":99}".getBytes(StandardCharsets.UTF_8)),
                        (ok, tooBig) -> {
                            JsonNode observation = ok.json();
                            problems.expect(ok.status() == 200, "observe радиуса 8 обязан отвечать 200: " + ok.body());
                            problems.expect(observation.path("bot").asText().equals(id),
                                    "bot в наблюдении обязан быть id духа " + id + ": " + ok.body());
                            problems.expect(observation.path("hash").asText().length() == 64,
                                    "hash наблюдения — SHA-256 из 64 символов: " + observation.path("hash"));
                            problems.expect(hasBlock(observation, "minecraft:stone"),
                                    "Блоки пола (minecraft:stone) обязаны быть в blocks наблюдения");
                            problems.expect(tooBig.status() == 400
                                            && tooBig.json().path("error").path("message").asText().contains("radius"),
                                    "radius 99 обязан давать 400 с названием поля radius: " + tooBig.body());
                            return ok;
                        })
                .thenCompose(ok -> rest.post(path,
                        "{\"include_entities\":false}".getBytes(StandardCharsets.UTF_8)))
                .thenApply(noEntities -> {
                    problems.expect(noEntities.status() == 200 && noEntities.json().path("entities").isEmpty(),
                            "include_entities=false обязан давать пустой список сущностей: " + noEntities.body());
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void threads_ForeignThreadAccess_IndexRejectsWhileGatewayAndHttpWork(GameTestHelper helper)
            throws IOException {
        RestFixture rest = RestFixture.start(helper.getLevel().getServer());
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        String id = spirit.getStringUUID();
        SpiritIndex index = rest.index();
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<Throwable> directAccess = CompletableFuture.supplyAsync(index::snapshot)
                .handle((result, error) -> error);
        CompletableFuture<List<String>> scenario = directAccess.thenCompose(error -> {
            problems.expect(error != null && error.getCause() instanceof IllegalStateException,
                    "Чтение SpiritIndex с постороннего потока обязано давать IllegalStateException, получено " + error);
            return CompletableFuture.supplyAsync(() -> rest.gateway().bots());
        }).thenCompose(viaGateway -> {
            problems.expect(viaGateway.stream().anyMatch(bot -> bot.id().equals(id)),
                    "Шлюз с постороннего потока обязан переходить на серверный и видеть духа " + id);
            return rest.get("/v1/bots");
        }).thenApply(http -> {
            problems.expect(http.status() == 200 && listsId(http.json(), id),
                    "HTTP-запрос при этом обязан работать и видеть духа: " + http.body());
            return problems.found();
        });
        RestFixture.await(helper, rest, scenario);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void stop_RunningServer_RefusesConnectionsAndFreesPort(GameTestHelper helper) throws IOException {
        RestFixture rest = RestFixture.start(helper.getLevel().getServer());
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest.get("/v1/bots").thenApplyAsync(running -> {
            problems.expect(running.status() == 200, "До остановки сервер обязан отвечать 200: " + running.body());
            int port = rest.server().port();
            rest.server().stop();
            rest.server().stop();
            problems.expect(connectionRefused(port),
                    "После stop() новое соединение на порт " + port + " обязано отвергаться");
            problems.expect(canBind(port),
                    "После stop() порт " + port + " обязан освобождаться: его можно занять снова");
            return problems.found();
        });
        RestFixture.await(helper, rest, scenario);
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void badRequests_BrokenJsonOversizedBodyUnknownPathWrongMethod_ReturnDocumentedErrors(
            GameTestHelper helper) throws IOException {
        RestFixture rest = RestFixture.start(helper.getLevel().getServer());
        String observe = "/v1/bots/" + UNKNOWN_ID + "/observe";
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<RestFixture.Reply> brokenJson = rest.post(observe,
                "{\n  \"radius\": }".getBytes(StandardCharsets.UTF_8));
        CompletableFuture<RestFixture.Reply> oversized = rest.post(observe, new byte[OVERSIZED_BODY_BYTES]);
        CompletableFuture<RestFixture.Reply> unknownPath = rest.get("/v1/nope");
        CompletableFuture<RestFixture.Reply> wrongMethod = rest.post("/v1/bots", new byte[0]);
        CompletableFuture<List<String>> scenario = CompletableFuture.allOf(brokenJson, oversized, unknownPath, wrongMethod)
                .thenApply(done -> {
                    JsonNode broken = brokenJson.join().json().path("error");
                    problems.expect(brokenJson.join().status() == 400 && broken.path("code").asText().equals("invalid_request")
                                    && broken.path("message").asText().contains("строка 2"),
                            "Неверный JSON обязан давать 400 invalid_request с позицией ошибки: " + brokenJson.join().body());
                    problems.expect(oversized.join().status() == 400
                                    && oversized.join().json().path("error").path("code").asText().equals("invalid_request"),
                            "Тело 2 МиБ обязано давать 400 invalid_request: " + oversized.join().body());
                    problems.expect(unknownPath.join().status() == 404
                                    && unknownPath.join().json().path("error").path("code").asText().equals("not_found"),
                            "Неизвестный путь обязан давать 404 not_found: " + unknownPath.join().body());
                    problems.expect(wrongMethod.join().status() == 405
                                    && wrongMethod.join().headers().firstValue("Allow").orElse("").equals("GET"),
                            "POST на /v1/bots обязан давать 405 с Allow: GET: " + wrongMethod.join().body()
                                    + " " + wrongMethod.join().headers());
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario);
    }

    private static boolean listsId(JsonNode bots, String id) {
        for (JsonNode bot : bots.path("bots")) {
            if (bot.path("id").asText().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBlock(JsonNode observation, String item) {
        for (JsonNode block : observation.path("blocks")) {
            if (block.path("item").asText().equals(item)) {
                return true;
            }
        }
        return false;
    }

    private static boolean connectionRefused(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(RestFixture.loopback(), port), 1000);
            return false;
        } catch (ConnectException refused) {
            return true;
        } catch (IOException other) {
            return false;
        }
    }

    private static boolean canBind(int port) {
        try (ServerSocket socket = new ServerSocket(port, 1, RestFixture.loopback())) {
            return socket.isBound();
        } catch (IOException busy) {
            return false;
        }
    }

    private static void buildFloor(GameTestHelper helper) {
        for (int x = 0; x < FLOOR_SIZE; x++) {
            for (int z = 0; z < FLOOR_SIZE; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
    }
}
