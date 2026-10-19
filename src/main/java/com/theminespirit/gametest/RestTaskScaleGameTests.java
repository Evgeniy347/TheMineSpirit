package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.task.TaskRegistry;
import com.theminespirit.core.task.TaskState;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.rest.TaskWatcher;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RestTaskScaleGameTests {
    private static final BlockPos SPIRIT_START = new BlockPos(1, 1, 1);
    private static final BlockPos FAR_CORNER = new BlockPos(7, 1, 7);
    private static final int BOTS = 30;
    private static final int STREAMS = 4;
    private static final int LEGS = 3;
    private static final int EXPECTED_LOG_WRITES = 5;
    private static final int SETTLE_TICKS = 3;

    private RestTaskScaleGameTests() {}

    @GameTest(batch = "rest_scale_watcher", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void watcher_ThirtyBotsWithActiveTasks_PollsExactlyThirtyPerTickAndNothingWhenEmpty(
            GameTestHelper helper) throws IOException {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        RestFixture rest = RestFixture.start(helper.getLevel().getServer());
        TaskWatcher watcher = rest.gateway().taskWatcher();
        TaskRegistry registry = rest.gateway().taskRegistry();
        List<SpiritEntity> spirits = new ArrayList<>();
        for (int i = 0; i < BOTS; i++) {
            spirits.add(rest.spawn(helper, SPIRIT_START));
        }
        List<String> taskIds = new ArrayList<>();
        AtomicLong startTick = new AtomicLong(-1);
        AtomicLong pollsAtRegistration = new AtomicLong();
        AtomicInteger emptyPolls = new AtomicInteger(-1);
        AtomicLong emptyTotalDelta = new AtomicLong(-1);
        AtomicInteger activePolls = new AtomicInteger(-1);
        AtomicLong activeTotalDelta = new AtomicLong(-1);
        AtomicInteger afterCancelPolls = new AtomicInteger(-1);
        AtomicLong registeredAt = new AtomicLong(-1);
        AtomicLong cancelledAt = new AtomicLong(-1);
        long pollsAtStart = watcher.totalPolls();
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (startTick.get() < 0) {
                startTick.set(now);
                return;
            }
            if (registeredAt.get() < 0 && now >= startTick.get() + SETTLE_TICKS) {
                emptyPolls.set(watcher.lastTickPolls());
                emptyTotalDelta.set(watcher.totalPolls() - pollsAtStart);
                for (SpiritEntity spirit : spirits) {
                    String taskId = registry.reserve(spirit.getStringUUID(), "масштаб");
                    registry.markStarted(taskId);
                    taskIds.add(taskId);
                }
                pollsAtRegistration.set(watcher.totalPolls());
                registeredAt.set(now);
                return;
            }
            if (registeredAt.get() >= 0 && cancelledAt.get() < 0 && now >= registeredAt.get() + SETTLE_TICKS) {
                activePolls.set(watcher.lastTickPolls());
                activeTotalDelta.set(watcher.totalPolls() - pollsAtRegistration.get());
                taskIds.forEach(taskId -> registry.observe(taskId, TaskState.CANCELLED, null, null));
                cancelledAt.set(now);
                return;
            }
            if (cancelledAt.get() >= 0 && afterCancelPolls.get() < 0 && now >= cancelledAt.get() + SETTLE_TICKS) {
                afterCancelPolls.set(watcher.lastTickPolls());
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(afterCancelPolls.get() >= 0, "Сценарий ещё не дошёл до конца: ждём тики");
            helper.assertTrue(
                    emptyPolls.get() == 0 && emptyTotalDelta.get() == 0,
                    "При пустом реестре watcher не должен опрашивать никого: за тик " + emptyPolls.get()
                            + ", всего за ожидание " + emptyTotalDelta.get());
            helper.assertTrue(
                    activePolls.get() == BOTS,
                    "При " + BOTS + " духах с активными задачами watcher обязан делать ровно " + BOTS
                            + " опросов за тик, а сделал " + activePolls.get()
                            + ": лишние опросы растут с числом задач быстрее, чем нужно");
            helper.assertTrue(
                    activeTotalDelta.get() % BOTS == 0 && activeTotalDelta.get() >= BOTS,
                    "Суммарно опросов за прогон обязано быть кратно " + BOTS + " (по " + BOTS + " на тик), получено "
                            + activeTotalDelta.get());
            helper.assertTrue(
                    afterCancelPolls.get() == 0,
                    "После завершения всех задач watcher не должен опрашивать никого: за тик "
                            + afterCancelPolls.get());
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(batch = "rest_scale_log", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void events_FourOpenStreams_LogWrittenOncePerChangeNotPerStream(GameTestHelper helper)
            throws IOException {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        RestFixture rest = RestFixture.start(helper.getLevel().getServer());
        SpiritEntity spirit = rest.spawnHeldUntilStreamsOpen(helper, SPIRIT_START, STREAMS);
        List<RestFixture.SseCapture> streams = new ArrayList<>();
        AtomicReference<String> taskId = new AtomicReference<>();
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest.post(
                        "/v1/bots/" + spirit.getStringUUID() + "/tasks", legsBody(helper))
                .thenApply(accepted -> {
                    problems.expect(accepted.status() == 202, "POST tasks обязан отвечать 202: " + accepted.body());
                    taskId.set(accepted.json().path("task_id").asText());
                    for (int i = 0; i < STREAMS; i++) {
                        streams.add(rest.stream("/v1/tasks/" + taskId.get() + "/events", Map.of()));
                    }
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario, () -> {
            for (RestFixture.SseCapture stream : streams) {
                helper.assertTrue(
                        stream.finished(), "Все " + STREAMS + " потока обязаны закрыться после завершения задачи");
            }
            long writes = rest.gateway().taskRegistry().log(taskId.get()).lastId();
            helper.assertTrue(
                    writes == EXPECTED_LOG_WRITES,
                    "Журнал задачи из " + LEGS + " шагов обязан получить ровно " + EXPECTED_LOG_WRITES
                            + " записей (progress+state запуска, два перехода шага, финальный state) независимо от числа "
                            + "потоков, получил " + writes + " при " + STREAMS + " открытых потоках");
            for (RestFixture.SseCapture stream : streams) {
                List<RestFixture.Frame> frames = stream.frames();
                RestFixture.Frame last = frames.get(frames.size() - 1);
                helper.assertTrue(
                        last.idNumber() == writes,
                        "Последний кадр каждого потока обязан иметь id " + writes + " (число записей журнала), получен "
                                + last);
            }
        });
    }

    @GameTest(batch = "rest_scale_hops", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void tasks_OnePlanPost_CostsExactlyOneServerHopAndReadsCostNone(GameTestHelper helper)
            throws IOException {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        RestFixture rest = RestFixture.start(helper.getLevel().getServer());
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        RestFixture.Problems problems = new RestFixture.Problems();
        AtomicInteger hopsForPost = new AtomicInteger(-1);
        AtomicInteger hopsForReads = new AtomicInteger(-1);
        int before = rest.gateway().serverCall().submittedCount();
        CompletableFuture<List<String>> scenario = rest.post(
                        "/v1/bots/" + spirit.getStringUUID() + "/tasks", legsBody(helper))
                .thenCompose(accepted -> {
                    problems.expect(accepted.status() == 202, "POST tasks обязан отвечать 202: " + accepted.body());
                    hopsForPost.set(rest.gateway().serverCall().submittedCount() - before);
                    String taskPath =
                            "/v1/tasks/" + accepted.json().path("task_id").asText();
                    int afterPost = rest.gateway().serverCall().submittedCount();
                    RestFixture.SseCapture stream = rest.stream(taskPath + "/events", Map.of());
                    return rest.get(taskPath)
                            .thenCompose(task -> stream.status().thenApply(status -> {
                                problems.expect(
                                        task.status() == 200 && status == 200,
                                        "GET задачи и открытие потока обязаны отвечать 200: " + task.status() + " и "
                                                + status);
                                hopsForReads.set(rest.gateway().serverCall().submittedCount() - afterPost);
                                stream.cancel();
                                return problems.found();
                            }));
                });
        RestFixture.await(helper, rest, scenario, () -> {
            helper.assertTrue(
                    hopsForPost.get() == 1,
                    "Один POST tasks с планом обязан стоить ровно один переход на серверный поток (старт у шлюза), "
                            + "стоил " + hopsForPost.get());
            helper.assertTrue(
                    hopsForReads.get() == 0,
                    "GET задачи и открытие SSE читают реестр в памяти и не должны ходить на серверный поток, ходили "
                            + hopsForReads.get() + " раз");
        });
    }

    private static String legsBody(GameTestHelper helper) {
        StringBuilder steps = new StringBuilder();
        for (int i = 0; i < LEGS; i++) {
            BlockPos target = helper.absolutePos(i % 2 == 0 ? FAR_CORNER : SPIRIT_START);
            if (i > 0) {
                steps.append(',');
            }
            steps.append("{\"type\":\"script\",\"script\":\"goto\",\"params\":{\"x\":")
                    .append(target.getX())
                    .append(",\"y\":")
                    .append(target.getY())
                    .append(",\"z\":")
                    .append(target.getZ())
                    .append("}}");
        }
        return "{\"request_id\":\"" + UUID.randomUUID() + "\",\"plan\":{\"type\":\"sequence\",\"steps\":[" + steps
                + "]}}";
    }
}
