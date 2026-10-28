package com.theminespirit.gametest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.SpiritEntity;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RestTaskGameTests {
    private static final BlockPos SPIRIT_START = new BlockPos(1, 1, 1);
    private static final BlockPos FAR_CORNER = new BlockPos(7, 1, 7);

    private RestTaskGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void tasks_PlanGoto_WatcherMovesAcceptedToRunningThenSucceededKeepingProgress(GameTestHelper helper)
            throws IOException {
        RestFixture rest = arena(helper);
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        TaskPoller poller = TaskPoller.start(helper, rest);
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest.post(
                        "/v1/bots/" + spirit.getStringUUID() + "/tasks",
                        gotoBody(helper, UUID.randomUUID().toString(), FAR_CORNER))
                .thenApply(accepted -> {
                    problems.expect(accepted.status() == 202, "POST tasks обязан отвечать 202: " + accepted.body());
                    poller.follow(accepted.json().path("task_id").asText());
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario, () -> {
            List<String> states = rest.gateway().taskRegistry().log(poller.taskId()).since(0).events().stream()
                    .filter(event -> "state".equals(event.type()))
                    .map(event -> event.data().get("state").toString())
                    .toList();
            helper.assertTrue(
                    states.equals(List.of("running", "succeeded")),
                    "Журнал событий обязан показать state running, затем succeeded (TaskWatcher переводит accepted в "
                            + "running и закрывает задачу), получено " + states);
            helper.assertTrue(
                    "succeeded".equals(poller.latest()),
                    "Дойдя до цели, дух обязан закрыть задачу как succeeded; последнее состояние " + poller.latest()
                            + ", виденные " + poller.seen());
            JsonNode progress = poller.lastJson().path("progress");
            helper.assertTrue(
                    "goto".equals(progress.path("current_script").asText())
                            && progress.path("step").asInt() == 1
                            && progress.path("total_steps").asInt() == 1,
                    "У завершённой задачи обязан остаться прогресс последнего шага 1 из 1 (goto): "
                            + poller.lastJson());
            helper.assertTrue(
                    rest.gateway().taskRegistry().watchableRecords().isEmpty(),
                    "После succeeded активных задач в реестре быть не должно");
        });
    }

    private static final class TaskPoller {
        private final Set<String> seen = ConcurrentHashMap.newKeySet();
        private final AtomicReference<String> path = new AtomicReference<>();
        private final AtomicReference<String> latest = new AtomicReference<>();
        private final AtomicReference<JsonNode> lastJson = new AtomicReference<>(MissingNode.getInstance());
        private final AtomicReference<CompletableFuture<Void>> inFlight =
                new AtomicReference<>(CompletableFuture.completedFuture(null));
        private final AtomicLong terminalSeenAt = new AtomicLong(-1);
        private final AtomicReference<String> taskId = new AtomicReference<>();

        static TaskPoller start(GameTestHelper helper, RestFixture rest) {
            TaskPoller poller = new TaskPoller();
            helper.onEachTick(() -> poller.poll(helper, rest));
            return poller;
        }

        String taskId() {
            return taskId.get();
        }

        void follow(String id) {
            taskId.set(id);
            path.set("/v1/tasks/" + id);
        }

        Set<String> seen() {
            return seen;
        }

        String latest() {
            return latest.get();
        }

        JsonNode lastJson() {
            return lastJson.get();
        }

        private void poll(GameTestHelper helper, RestFixture rest) {
            String id = taskId.get();
            if (id != null && terminalSeenAt.get() < 0) {
                rest.gateway()
                        .taskRegistry()
                        .get(id)
                        .filter(record -> !record.active())
                        .ifPresent(
                                record -> terminalSeenAt.set(helper.getLevel().getGameTime()));
            }
            String target = path.get();
            CompletableFuture<Void> previous = inFlight.get();
            if (target == null || !previous.isDone()) {
                return;
            }
            previous.join();
            inFlight.set(rest.get(target).handle((reply, error) -> {
                if (reply != null && reply.status() == 200) {
                    String state = reply.json().path("state").asText();
                    seen.add(state);
                    latest.set(state);
                    lastJson.set(reply.json());
                }
                return null;
            }));
        }
    }

    private static RestFixture arena(GameTestHelper helper) throws IOException {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        return RestFixture.start(helper.getLevel().getServer());
    }

    private static String gotoBody(GameTestHelper helper, String requestId, BlockPos relativeTarget) {
        BlockPos target = helper.absolutePos(relativeTarget);
        return "{\"request_id\":\"" + requestId + "\",\"plan\":{\"type\":\"script\",\"script\":\"goto\",\"params\":"
                + "{\"x\":" + target.getX() + ",\"y\":" + target.getY() + ",\"z\":" + target.getZ() + "}}}";
    }
}
