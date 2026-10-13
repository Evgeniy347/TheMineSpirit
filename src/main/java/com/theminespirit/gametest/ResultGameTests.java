package com.theminespirit.gametest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.SpiritEntity;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
            helper.assertTrue(result.isTextual() && result.asText().equals("срублено 1 из 1"),
                    "Итог chop_tree в GET задачи обязан быть строкой «срублено 1 из 1», получено " + result);
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
            RestFixture.Frame last = frames.get(frames.size() - 1);
            helper.assertTrue(last.event().equals("state")
                    && last.json().path("state").asText().equals("succeeded"),
                    "Завершающим кадром обязан быть state succeeded, получено " + last);
            JsonNode result = last.json().path("result");
            helper.assertTrue(result.isTextual() && result.asText().equals("срублено 1 из 1"),
                    "Финальный state обязан нести итог «срублено 1 из 1», получено " + result);
        });
    }

    private static RestFixture arena(GameTestHelper helper) throws IOException {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        return RestFixture.start(helper.getLevel().getServer());
    }

    private static SpiritEntity spawnedChopper(GameTestHelper helper, RestFixture rest) {
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_AXE));
        return spirit;
    }

    private static String taskBody(String script, Map<String, Object> params) {
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
    }

    private static final class ResultPoller {
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
