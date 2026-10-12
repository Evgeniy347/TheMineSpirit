package com.theminespirit.gametest;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.SpiritEntity;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RestTaskGameTests {
    private static final BlockPos SPIRIT_START = new BlockPos(1, 1, 1);
    private static final BlockPos FAR_CORNER = new BlockPos(7, 1, 7);
    private static final BlockPos NEAR_CORNER = new BlockPos(2, 1, 1);

    private RestTaskGameTests() {
    }

    @GameTest(batch = "rest_tasks_accept", template = "arena", timeoutTicks = 200)
    public static void tasks_PlanGoto_Accepts202AndBotReportsCurrentTaskAndRefusesSecondTask(GameTestHelper helper)
            throws IOException {
        RestFixture rest = arena(helper);
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        String botPath = "/v1/bots/" + spirit.getStringUUID();
        String requestId = UUID.randomUUID().toString();
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest.post(botPath + "/tasks", gotoBody(helper, requestId, FAR_CORNER))
                .thenCompose(accepted -> {
                    JsonNode json = accepted.json();
                    problems.expect(accepted.status() == 202,
                            "POST tasks с планом goto обязан отвечать 202, получен " + accepted.status() + ": "
                                    + accepted.body());
                    problems.expect(json.path("state").asText().equals("accepted")
                            && json.path("bot").asText().equals(spirit.getStringUUID())
                            && json.path("request_id").asText().equals(requestId),
                            "202 обязан нести state=accepted, bot и request_id запроса: " + accepted.body());
                    String taskId = json.path("task_id").asText();
                    return rest.get(botPath).thenCompose(bot -> {
                        problems.expect(bot.json().path("current_task").asText().equals(taskId),
                                "GET bots/{id} обязан показывать current_task=" + taskId + " из реестра, получено: "
                                        + bot.body());
                        problems.expect(bot.json().path("state").asText().equals("busy"),
                                "Дух с запущенной задачей обязан быть busy: " + bot.body());
                        return rest.post(botPath + "/tasks", gotoBody(helper, UUID.randomUUID().toString(), NEAR_CORNER))
                                .thenApply(second -> {
                                    problems.expect(second.status() == 409,
                                            "Второй POST с новым request_id к занятому духу обязан давать 409, получен "
                                                    + second.status() + ": " + second.body());
                                    problems.expect(second.body().contains(taskId),
                                            "409 обязан называть текущую задачу " + taskId + ": " + second.body());
                                    return problems.found();
                                });
                    });
                });
        RestFixture.await(helper, rest, scenario);
    }

    @GameTest(batch = "rest_tasks_replay", template = "arena", timeoutTicks = 200)
    public static void tasks_ReplayAndChangedBody_SameTaskWithoutRestartThenConflict(GameTestHelper helper)
            throws IOException {
        RestFixture rest = arena(helper);
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        String botPath = "/v1/bots/" + spirit.getStringUUID();
        String requestId = UUID.randomUUID().toString();
        String body = gotoBody(helper, requestId, FAR_CORNER);
        String changedBody = gotoBody(helper, requestId, NEAR_CORNER);
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest.post(botPath + "/tasks", body).thenCompose(first ->
                rest.onServer(spirit::pathBuilds).thenCompose(buildsBefore ->
                        rest.post(botPath + "/tasks", body).thenCompose(replay ->
                                rest.post(botPath + "/tasks", changedBody).thenCompose(changed ->
                                        rest.onServer(spirit::pathBuilds).thenApply(buildsAfter -> {
                                            problems.expect(replay.status() == 202,
                                                    "Повтор того же запроса к духу, занятому этой же задачей, обязан "
                                                            + "давать 202, получен " + replay.status() + ": "
                                                            + replay.body());
                                            problems.expect(replay.json().path("task_id").asText()
                                                    .equals(first.json().path("task_id").asText()),
                                                    "Повтор обязан вернуть тот же task_id: " + first.body() + " и "
                                                            + replay.body());
                                            problems.expect(changed.status() == 409 && changed.body().contains(requestId),
                                                    "Тот же request_id с другим телом обязан давать 409, называя "
                                                            + "request_id: " + changed.status() + " " + changed.body());
                                            problems.expect(buildsAfter.equals(buildsBefore),
                                                    "Повтор не должен перезапускать духа: путей построено "
                                                            + buildsBefore + " до повтора и " + buildsAfter + " после");
                                            problems.expect(rest.gateway().taskRegistry().size() == 1,
                                                    "В реестре обязана остаться одна запись, а не "
                                                            + rest.gateway().taskRegistry().size());
                                            return problems.found();
                                        })))));
        RestFixture.await(helper, rest, scenario);
    }

    @GameTest(batch = "rest_tasks_command", template = "arena", timeoutTicks = 200)
    public static void tasks_SpiritBusyWithCommandTask_Returns409OutsideRestWithoutReservation(GameTestHelper helper)
            throws IOException {
        RestFixture rest = arena(helper);
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        BlockPos far = helper.absolutePos(FAR_CORNER);
        spirit.startTask(new ScriptCall("goto", Map.of("x", far.getX(), "y", far.getY(), "z", far.getZ())));
        String botPath = "/v1/bots/" + spirit.getStringUUID();
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest
                .post(botPath + "/tasks", gotoBody(helper, UUID.randomUUID().toString(), NEAR_CORNER))
                .thenApply(reply -> {
                    problems.expect(reply.status() == 409,
                            "Дух, занятый задачей команды /spirit, обязан давать 409, получен " + reply.status()
                                    + ": " + reply.body());
                    problems.expect(reply.body().contains("вне REST"),
                            "409 обязан объяснять, что задача поставлена вне REST: " + reply.body());
                    problems.expect(rest.gateway().taskRegistry().size() == 0,
                            "Отказ не должен оставлять запись в реестре, записей "
                                    + rest.gateway().taskRegistry().size());
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario);
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
