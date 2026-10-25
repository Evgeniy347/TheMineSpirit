package com.theminespirit.gametest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.TaskSnapshot;
import com.theminespirit.core.engine.TaskSnapshotCodec;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BuildRestartGameTests {
    private BuildRestartGameTests() {}

    private static void prepare(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    private static int countCobble(GameTestHelper helper, BlockPos first, BlockPos second) {
        int found = 0;
        for (int x = first.getX(); x <= second.getX(); x++) {
            for (int y = first.getY(); y <= second.getY(); y++) {
                for (int z = first.getZ(); z <= second.getZ(); z++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.COBBLESTONE)) {
                        found++;
                    }
                }
            }
        }
        return found;
    }

    private static SpiritEntity reload(GameTestHelper helper, SpiritEntity original, CompoundTag tag) {
        original.discard();
        SpiritEntity reloaded = ModEntities.SPIRIT.get().create(helper.getLevel());
        reloaded.load(tag);
        helper.getLevel().addFreshEntity(reloaded);
        return reloaded;
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void fill_SavedMidway_ResumesWithoutDoubleSpending(GameTestHelper helper) {
        prepare(helper);
        BlockPos first = new BlockPos(4, 1, 4);
        BlockPos second = new BlockPos(5, 1, 5);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 4));
        BlockPos from = helper.absolutePos(first);
        BlockPos to = helper.absolutePos(second);
        AtomicReference<SpiritEntity> holder = new AtomicReference<>(spirit);
        AtomicBoolean saved = new AtomicBoolean(false);
        AtomicBoolean snapshotChecked = new AtomicBoolean(false);
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "fill",
                        Map.of(
                                "block",
                                "cobblestone",
                                "x1",
                                from.getX(),
                                "y1",
                                from.getY(),
                                "z1",
                                from.getZ(),
                                "x2",
                                to.getX(),
                                "y2",
                                to.getY(),
                                "z2",
                                to.getZ())),
                5);
        helper.onEachTick(() -> {
            SpiritEntity current = holder.get();
            if (!saved.get()
                    && current.taskStatus().orElse(null) == RunStatus.RUNNING
                    && countCobble(helper, first, second) >= 1) {
                saved.set(true);
                run.assertStarted();
                CompoundTag tag = new CompoundTag();
                current.saveWithoutId(tag);
                holder.set(reload(helper, current, tag));
            } else if (saved.get() && !snapshotChecked.get() && holder.get().tickCount >= 3) {
                snapshotChecked.set(true);
                Optional<String> json = GameTestFixtures.taskSnapshot(holder.get());
                helper.assertTrue(json.isPresent(), "у восстановленного духа обязан быть снимок задачи");
                TaskSnapshot snapshot = TaskSnapshotCodec.decode(json.get());
                helper.assertTrue(
                        snapshot.status() == RunStatus.RUNNING,
                        "снимок обязан хранить RUNNING-задачу: иначе продолжение нечем кормить, статус "
                                + snapshot.status());
            }
        });

        helper.succeedWhen(() -> {
            SpiritEntity current = holder.get();
            run.assertStarted();
            helper.assertTrue(
                    current.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "заливка после перезагрузки обязана дойти до успеха, статус "
                            + current.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + current.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    countCobble(helper, first, second) == 4,
                    "все 4 клетки обязаны стоять, фактически " + countCobble(helper, first, second));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(current.getInventory(), Items.COBBLESTONE) == 0,
                    "материалы не тратятся дважды: 4 блока — 4 булыжника, в инвентаре "
                            + GameTestFixtures.countInInventory(current.getInventory(), Items.COBBLESTONE));
            helper.assertTrue(
                    current.taskResult().orElse(null) instanceof ScriptResult.Data data
                            && Integer.valueOf(4).equals(data.fields().get("placed")),
                    "счётчики переживают перезапуск: итог обязан назвать placed=4, фактически "
                            + current.taskResult()
                                    .map(result -> result.toString())
                                    .orElse("пусто"));
            GameTestFixtures.finish(helper);
        });
    }

    private static RestFixture arena(GameTestHelper helper) throws IOException {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        return RestFixture.start(helper.getLevel().getServer());
    }

    private static String taskBody(String script, Map<String, Object> params) {
        StringBuilder body = new StringBuilder("{\"request_id\":\"")
                .append(UUID.randomUUID())
                .append("\",\"plan\":{\"type\":\"script\",\"script\":\"")
                .append(script)
                .append("\",\"params\":{");
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
            } else {
                body.append(value);
            }
        }
        return body.append("}}}").toString();
    }

    @SceneStep(template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void fill_SucceededTask_RestProgressAndResultHoldCounts(GameTestHelper helper) throws IOException {
        RestFixture rest = arena(helper);
        SpiritEntity spirit = rest.spawn(helper, new BlockPos(1, 1, 1));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 2));
        BlockPos from = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos to = helper.absolutePos(new BlockPos(5, 1, 4));
        TaskPoller poller = TaskPoller.start(helper, rest);
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest.post(
                        "/v1/bots/" + spirit.getStringUUID() + "/tasks",
                        taskBody(
                                "fill",
                                Map.of(
                                        "block",
                                        "cobblestone",
                                        "x1",
                                        from.getX(),
                                        "y1",
                                        from.getY(),
                                        "z1",
                                        from.getZ(),
                                        "x2",
                                        to.getX(),
                                        "y2",
                                        to.getY(),
                                        "z2",
                                        to.getZ())))
                .thenApply(accepted -> {
                    problems.expect(accepted.status() == 202, "POST tasks обязан отвечать 202: " + accepted.body());
                    poller.follow(accepted.json().path("task_id").asText());
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario, () -> {
            helper.assertTrue(
                    "succeeded".equals(poller.latest()),
                    "заливка двух клеток обязана завершить задачу успехом; последнее состояние " + poller.latest()
                            + ", тело " + poller.lastJson());
            JsonNode progress = poller.lastJson().path("progress");
            helper.assertTrue(
                    progress.path("done").asInt() == 2
                            && progress.path("target").asInt() == 2,
                    "счётная задача обязана отдать прогресс done=2 target=2, фактически " + progress);
            JsonNode result = poller.lastJson().path("result");
            helper.assertTrue(
                    result.isObject()
                            && result.path("placed").asInt() == 2
                            && result.path("missing").asInt() == 0,
                    "итог fill в GET задачи обязан нести placed=2 missing=0, получено " + result);
        });
    }

    private static final class TaskPoller {
        private final AtomicReference<String> path = new AtomicReference<>();
        private final AtomicReference<String> latest = new AtomicReference<>();
        private final AtomicReference<JsonNode> lastJson = new AtomicReference<>(MissingNode.getInstance());
        private final AtomicReference<CompletableFuture<Void>> inFlight =
                new AtomicReference<>(CompletableFuture.completedFuture(null));

        static TaskPoller start(GameTestHelper helper, RestFixture rest) {
            TaskPoller poller = new TaskPoller();
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
            CompletableFuture<Void> previous = inFlight.get();
            if (target == null || !previous.isDone()) {
                return;
            }
            previous.join();
            inFlight.set(rest.get(target).handle((reply, error) -> {
                if (reply != null && reply.status() == 200) {
                    latest.set(reply.json().path("state").asText());
                    lastJson.set(reply.json());
                }
                return null;
            }));
        }
    }
}
