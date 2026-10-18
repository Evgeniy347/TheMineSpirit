package com.theminespirit.gametest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResultGameTests {
    private static final BlockPos SPIRIT_START = new BlockPos(1, 1, 1);

    private ResultGameTests() {
    }

    @GameTest(batch = "result_chop_text", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void chopTree_SucceededTask_RestResultHoldsSummaryText(GameTestHelper helper) throws IOException {
        RestFixture rest = arena(helper);
        buildOakTree(helper, 4, 4, 5);
        SpiritEntity spirit = spawnedChopper(helper, rest);
        ResultPoller poller = ResultPoller.start(helper, rest);
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest
                .post("/v1/bots/" + spirit.getStringUUID() + "/tasks",
                        taskBody("chop_tree",
                                Map.of("species", "oak", "count", 1, "radius", 8, "replant", false)))
                .thenApply(accepted -> {
                    problems.expect(accepted.status() == 202, "POST tasks обязан отвечать 202: " + accepted.body());
                    poller.follow(accepted.json().path("task_id").asText());
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario, () -> {
            helper.assertTrue("succeeded".equals(poller.latest()),
                    "Рубка одного дуба обязана завершить задачу успехом; последнее состояние " + poller.latest()
                            + ", тело " + poller.lastJson());
            JsonNode result = poller.lastJson().path("result");
            helper.assertTrue(result.isTextual() && result.asText().equals("срублено деревьев 1 из 1"),
                    "Итог chop_tree в GET задачи обязан быть строкой «срублено деревьев 1 из 1», получено " + result);
        });
    }

    @GameTest(batch = "result_wait", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void wait_SucceededTask_RestResultHoldsTicksObject(GameTestHelper helper) throws IOException {
        RestFixture rest = arena(helper);
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        ResultPoller poller = ResultPoller.start(helper, rest);
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest
                .post("/v1/bots/" + spirit.getStringUUID() + "/tasks",
                        taskBody("wait", Map.of("ticks", 40)))
                .thenApply(accepted -> {
                    problems.expect(accepted.status() == 202, "POST tasks обязан отвечать 202: " + accepted.body());
                    poller.follow(accepted.json().path("task_id").asText());
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario, () -> {
            helper.assertTrue("succeeded".equals(poller.latest()),
                    "wait 40 тиков обязана завершить задачу успехом; последнее состояние " + poller.latest()
                            + ", тело " + poller.lastJson());
            JsonNode result = poller.lastJson().path("result");
            helper.assertTrue(result.isObject() && result.path("ticks").asInt() == 40,
                    "итог wait в GET задачи обязан быть объектом с ticks=40, получено " + result);
        });
    }

    @GameTest(batch = "result_chop_sse", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void chopTree_SucceededTask_SseFinalStateCarriesResult(GameTestHelper helper) throws IOException {
        RestFixture rest = arena(helper);
        buildOakTree(helper, 4, 4, 5);
        SpiritEntity spirit = spawnedChopper(helper, rest);
        AtomicReference<RestFixture.SseCapture> stream = new AtomicReference<>();
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest
                .post("/v1/bots/" + spirit.getStringUUID() + "/tasks",
                        taskBody("chop_tree",
                                Map.of("species", "oak", "count", 1, "radius", 8, "replant", false)))
                .thenApply(accepted -> {
                    problems.expect(accepted.status() == 202, "POST tasks обязан отвечать 202: " + accepted.body());
                    stream.set(rest.stream(
                            "/v1/tasks/" + accepted.json().path("task_id").asText() + "/events", Map.of()));
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario, () -> {
            helper.assertTrue(stream.get() != null && stream.get().finished(),
                    "Поток событий обязан закрыться после финального state; кадры пока: "
                            + (stream.get() == null ? "нет потока" : stream.get().lines()));
            List<RestFixture.Frame> frames = stream.get().frames();
            helper.assertTrue(!frames.isEmpty(),
                    "Поток событий обязан принести хотя бы один кадр state; кадры: " + stream.get().lines()
                            + ": пустой поток означает, что задача завершилась раньше подписки, — проверять нечего");
            RestFixture.Frame last = frames.get(frames.size() - 1);
            helper.assertTrue(last.event().equals("state")
                    && last.json().path("state").asText().equals("succeeded"),
                    "Завершающим кадром обязан быть state succeeded, получено " + last);
            JsonNode result = last.json().path("result");
            helper.assertTrue(result.isTextual() && result.asText().equals("срублено деревьев 1 из 1"),
                    "Финальный state обязан нести итог «срублено деревьев 1 из 1», получено " + result);
        });
    }

    @GameTest(batch = "result_scan_blocks", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void scanBlocks_FilteredScan_RestResultHoldsBlocksObject(GameTestHelper helper) throws IOException {
        RestFixture rest = arena(helper);
        helper.setBlock(3, 1, 3, Blocks.OAK_LOG);
        helper.setBlock(4, 1, 3, Blocks.OAK_LOG);
        helper.setBlock(5, 1, 3, Blocks.OAK_LOG);
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        ResultPoller poller = ResultPoller.start(helper, rest);
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest
                .post("/v1/bots/" + spirit.getStringUUID() + "/tasks",
                        taskBody("scan_blocks", Map.of("radius", 8, "filter", List.of("*_log"))))
                .thenApply(accepted -> {
                    problems.expect(accepted.status() == 202, "POST tasks обязан отвечать 202: " + accepted.body());
                    poller.follow(accepted.json().path("task_id").asText());
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario, () -> {
            helper.assertTrue("succeeded".equals(poller.latest()),
                    "scan_blocks обязан завершить задачу успехом; последнее состояние " + poller.latest()
                            + ", тело " + poller.lastJson());
            JsonNode result = poller.lastJson().path("result");
            helper.assertTrue(result.isObject() && result.path("type").asText().equals("blocks"),
                    "Итог scan_blocks обязан быть объектом с type=blocks, получено " + result);
            boolean oakLogs = false;
            for (JsonNode entry : result.path("blocks")) {
                if (entry.path("item").asText().equals("minecraft:oak_log") && entry.path("count").asInt() == 3) {
                    oakLogs = true;
                }
            }
            helper.assertTrue(oakLogs,
                    "Итог обязан содержать minecraft:oak_log в числе 3, получено " + result.path("blocks"));
        });
    }

    @GameTest(batch = "result_scan_hazard", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void senseHazard_WaterNearby_RestResultHoldsHazardsObject(GameTestHelper helper) throws IOException {
        RestFixture rest = arena(helper);
        helper.setBlock(4, 1, 4, Blocks.WATER);
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        ResultPoller poller = ResultPoller.start(helper, rest);
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest
                .post("/v1/bots/" + spirit.getStringUUID() + "/tasks",
                        taskBody("sense_hazard", Map.of("radius", 8)))
                .thenApply(accepted -> {
                    problems.expect(accepted.status() == 202, "POST tasks обязан отвечать 202: " + accepted.body());
                    poller.follow(accepted.json().path("task_id").asText());
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario, () -> {
            helper.assertTrue("succeeded".equals(poller.latest()),
                    "sense_hazard обязан завершить задачу успехом; последнее состояние " + poller.latest()
                            + ", тело " + poller.lastJson());
            JsonNode result = poller.lastJson().path("result");
            helper.assertTrue(result.isObject() && result.path("type").asText().equals("hazards"),
                    "Итог sense_hazard обязан быть объектом с type=hazards, получено " + result);
            boolean water = false;
            for (JsonNode entry : result.path("hazards")) {
                if (entry.path("type").asText().equals("water")) {
                    water = true;
                }
            }
            helper.assertTrue(water, "Итог обязан содержать опасность water, получено " + result.path("hazards"));
        });
    }

    @GameTest(batch = "result_scan_locate", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void locateBlock_LogNearby_RestResultHoldsLocatedSightings(GameTestHelper helper) throws IOException {
        RestFixture rest = arena(helper);
        helper.setBlock(4, 1, 4, Blocks.OAK_LOG);
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        ResultPoller poller = ResultPoller.start(helper, rest);
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest
                .post("/v1/bots/" + spirit.getStringUUID() + "/tasks",
                        taskBody("locate_block", Map.of("radius", 8, "filter", List.of("minecraft:oak_log"))))
                .thenApply(accepted -> {
                    problems.expect(accepted.status() == 202, "POST tasks обязан отвечать 202: " + accepted.body());
                    poller.follow(accepted.json().path("task_id").asText());
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario, () -> {
            helper.assertTrue("succeeded".equals(poller.latest()),
                    "locate_block обязан завершить задачу успехом; последнее состояние " + poller.latest()
                            + ", тело " + poller.lastJson());
            JsonNode result = poller.lastJson().path("result");
            helper.assertTrue(result.isObject() && result.path("type").asText().equals("located"),
                    "Итог locate_block обязан быть объектом с type=located, получено " + result);
            JsonNode sightings = result.path("sightings");
            helper.assertTrue(sightings.isArray() && sightings.size() == 1
                    && sightings.get(0).path("label").asText().contains("oak_log"),
                    "Итог обязан содержать одну находку oak_log, получено " + sightings);
        });
    }

    @GameTest(batch = "result_scan_truncated", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void scanBlocks_TooManyBlockKinds_RestResultTruncatedToLimit(GameTestHelper helper)
            throws IOException {
        RestFixture rest = arena(helper);
        placeDistinctBlocks(helper);
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        ResultPoller poller = ResultPoller.start(helper, rest);
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest
                .post("/v1/bots/" + spirit.getStringUUID() + "/tasks",
                        taskBody("scan_blocks", Map.of("radius", 8, "filter", List.of("*"))))
                .thenApply(accepted -> {
                    problems.expect(accepted.status() == 202, "POST tasks обязан отвечать 202: " + accepted.body());
                    poller.follow(accepted.json().path("task_id").asText());
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario, () -> {
            helper.assertTrue("succeeded".equals(poller.latest()),
                    "scan_blocks обязан завершить задачу успехом; последнее состояние " + poller.latest()
                            + ", тело " + poller.lastJson());
            JsonNode result = poller.lastJson().path("result");
            helper.assertTrue(result.isObject() && result.path("type").asText().equals("blocks"),
                    "Итог обязан быть объектом с type=blocks, получено " + result);
            helper.assertTrue(result.path("truncated").asBoolean(false),
                    "Итог длиннее лимита обязан нести truncated=true, получено " + result);
            helper.assertTrue(result.path("blocks").size() == 32,
                    "Список блоков обязан обрезаться до 32 записей, получено " + result.path("blocks").size());
        });
    }

    private static void placeDistinctBlocks(GameTestHelper helper) {
        String[] ids = { "white_wool", "orange_wool", "magenta_wool", "light_blue_wool", "yellow_wool",
                "lime_wool", "pink_wool", "gray_wool", "light_gray_wool", "cyan_wool", "purple_wool", "blue_wool",
                "brown_wool", "green_wool", "red_wool", "black_wool", "oak_planks", "spruce_planks", "birch_planks",
                "jungle_planks", "acacia_planks", "dark_oak_planks", "mangrove_planks", "cherry_planks",
                "bamboo_planks", "crimson_planks", "warped_planks", "oak_log", "spruce_log", "birch_log",
                "jungle_log", "acacia_log", "dark_oak_log" };
        int i = 0;
        for (int z = 0; z < 4 && i < ids.length; z++) {
            for (int x = 0; x < 9 && i < ids.length; x++) {
                if (x == SPIRIT_START.getX() && z == SPIRIT_START.getZ()) {
                    continue;
                }
                helper.setBlock(x, 1, z, BuiltInRegistries.BLOCK
                        .get(ResourceLocation.parse("minecraft:" + ids[i])));
                i++;
            }
        }
    }

    @GameTest(batch = "result_chop_reload", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void chopTree_ReloadedAfterFirstTree_ResultKeepsCountersOfBoth(GameTestHelper helper)
            throws IOException {
        RestFixture rest = arena(helper);
        buildOakTree(helper, 4, 4, 8);
        buildOakTree(helper, 6, 6, 5);
        SpiritEntity started = spawnedChopper(helper, rest);
        AtomicReference<SpiritEntity> spirit = new AtomicReference<>(started);
        ResultPoller poller = ResultPoller.start(helper, rest);
        AtomicBoolean reloaded = new AtomicBoolean();
        AtomicReference<CompoundTag> saved = new AtomicReference<>();
        helper.onEachTick(() -> {
            if (!reloaded.get() && saved.get() == null
                    && spirit.get().taskProgress().map(progress -> progress.done() != null && progress.done() >= 1)
                            .orElse(false)) {
                CompoundTag tag = new CompoundTag();
                spirit.get().saveWithoutId(tag);
                spirit.get().discard();
                saved.set(tag);
            }
            if (saved.get() != null && !reloaded.get()) {
                reloaded.set(true);
                SpiritEntity back = ModEntities.SPIRIT.get().create(helper.getLevel());
                back.load(saved.get());
                helper.getLevel().addFreshEntity(back);
                spirit.set(back);
                rest.track(back);
            }
        });
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest
                .post("/v1/bots/" + started.getStringUUID() + "/tasks",
                        taskBody("chop_tree",
                                Map.of("species", "oak", "count", 2, "radius", 8, "replant", false)))
                .thenApply(accepted -> {
                    problems.expect(accepted.status() == 202, "POST tasks обязан отвечать 202: " + accepted.body());
                    poller.follow(accepted.json().path("task_id").asText());
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario, () -> {
            helper.assertTrue(reloaded.get(), "Дух обязан пройти перезагрузку до завершения рубки двух деревьев");
            helper.assertTrue("succeeded".equals(poller.latest()),
                    "Рубка двух деревьев с перезагрузкой обязана завершиться успехом; последнее состояние "
                            + poller.latest() + ", тело " + poller.lastJson());
            String result = poller.lastJson().path("result").asText("");
            helper.assertTrue(result.contains("срублено деревьев 2 из 2"),
                    "Итог обязан называть оба дерева «срублено деревьев 2 из 2» (счётчик пережил перезагрузку), "
                            + "получено " + result);
            helper.assertTrue(countLogs(helper) == 0,
                    "Дух рубит дерево целиком с одной стоянки: после двух деревьев в мире не остаётся брёвен, "
                            + "осталось " + countLogs(helper));
        });
    }

    private static int countLogs(GameTestHelper helper) {
        int count = 0;
        for (int x = -2; x < 11; x++) {
            for (int y = 0; y < 12; y++) {
                for (int z = -2; z < 11; z++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.OAK_LOG)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static RestFixture arena(GameTestHelper helper) throws IOException {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.DIRT);
        return RestFixture.start(helper.getLevel().getServer());
    }

    private static SpiritEntity spawnedChopper(GameTestHelper helper, RestFixture rest) {
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_AXE));
        return spirit;
    }

    static String taskBody(String script, Map<String, Object> params) {
        StringBuilder body = new StringBuilder("{\"request_id\":\"").append(UUID.randomUUID())
                .append("\",\"plan\":{\"type\":\"script\",\"script\":\"").append(script).append("\",\"params\":{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!first) {
                body.append(',');
            }
            first = false;
            body.append('"').append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String text) {
                body.append('"').append(text).append('"');
            } else if (value instanceof List<?> list) {
                body.append('[');
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        body.append(',');
                    }
                    body.append('"').append(list.get(i)).append('"');
                }
                body.append(']');
            } else {
                body.append(value);
            }
        }
        return body.append("}}}").toString();
    }

    private static void buildOakTree(GameTestHelper helper, int x, int z, int height) {
        for (int y = 1; y <= height; y++) {
            helper.setBlock(x, y, z, Blocks.OAK_LOG);
        }
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        helper.setBlock(x, height + 1, z, leaves);
        helper.setBlock(x + 1, height, z, leaves);
        helper.setBlock(x - 1, height, z, leaves);
        helper.setBlock(x, height, z + 1, leaves);
        helper.setBlock(x, height, z - 1, leaves);
        helper.setBlock(x + 1, height + 1, z, leaves);
        helper.setBlock(x - 1, height + 1, z, leaves);
        helper.setBlock(x, height + 1, z + 1, leaves);
        helper.setBlock(x, height + 1, z - 1, leaves);
        helper.setBlock(x + 1, height + 1, z + 1, leaves);
        helper.setBlock(x - 1, height + 1, z - 1, leaves);
    }

    static final class ResultPoller {
        private final AtomicReference<String> path = new AtomicReference<>();
        private final AtomicReference<String> latest = new AtomicReference<>();
        private final AtomicReference<JsonNode> lastJson = new AtomicReference<>(MissingNode.getInstance());
        private final AtomicBoolean inFlight = new AtomicBoolean();

        static ResultPoller start(GameTestHelper helper, RestFixture rest) {
            ResultPoller poller = new ResultPoller();
            helper.onEachTick(() -> poller.poll(rest));
            return poller;
        }

        void follow(String taskId) {
            path.set("/v1/tasks/" + taskId);
        }

        String latest() {
            return latest.get();
        }

        JsonNode lastJson() {
            return lastJson.get();
        }

        private void poll(RestFixture rest) {
            String target = path.get();
            if (target == null || !inFlight.compareAndSet(false, true)) {
                return;
            }
            rest.get(target).whenComplete((reply, error) -> {
                try {
                    if (reply != null && reply.status() == 200) {
                        String state = reply.json().path("state").asText();
                        latest.set(state);
                        lastJson.set(reply.json());
                    }
                } finally {
                    inFlight.set(false);
                }
            });
        }
    }
}
