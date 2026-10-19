package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.task.TaskRegistry;
import com.theminespirit.core.task.TaskState;
import com.theminespirit.neoforge.SpiritEntity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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

    private RestTaskScaleGameTests() {
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
        CompletableFuture<List<String>> scenario = rest
                .post("/v1/bots/" + spirit.getStringUUID() + "/tasks", legsBody(helper))
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
                helper.assertTrue(stream.finished(), "Все " + STREAMS + " потока обязаны закрыться после завершения задачи");
            }
            long writes = rest.gateway().taskRegistry().log(taskId.get()).lastId();
            helper.assertTrue(writes == EXPECTED_LOG_WRITES,
                    "Журнал задачи из " + LEGS + " шагов обязан получить ровно " + EXPECTED_LOG_WRITES
                            + " записей (progress+state запуска, два перехода шага, финальный state) независимо от числа "
                            + "потоков, получил " + writes + " при " + STREAMS + " открытых потоках");
            for (RestFixture.SseCapture stream : streams) {
                List<RestFixture.Frame> frames = stream.frames();
                RestFixture.Frame last = frames.get(frames.size() - 1);
                helper.assertTrue(last.idNumber() == writes,
                        "Последний кадр каждого потока обязан иметь id " + writes + " (число записей журнала), получен "
                                + last);
            }
        });
    }

    private static String legsBody(GameTestHelper helper) {
        StringBuilder steps = new StringBuilder();
        for (int i = 0; i < LEGS; i++) {
            BlockPos target = helper.absolutePos(i % 2 == 0 ? FAR_CORNER : SPIRIT_START);
            if (i > 0) {
                steps.append(',');
            }
            steps.append("{\"type\":\"script\",\"script\":\"goto\",\"params\":{\"x\":").append(target.getX())
                    .append(",\"y\":").append(target.getY()).append(",\"z\":").append(target.getZ()).append("}}");
        }
        return "{\"request_id\":\"" + UUID.randomUUID() + "\",\"plan\":{\"type\":\"sequence\",\"steps\":[" + steps
                + "]}}";
    }
}
