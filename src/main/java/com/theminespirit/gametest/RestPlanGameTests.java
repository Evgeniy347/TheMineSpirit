package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.SpiritEntity;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RestPlanGameTests {
    private static final BlockPos SPIRIT_START = new BlockPos(1, 1, 1);
    private static final BlockPos PLAYER_AT = new BlockPos(5, 1, 5);
    private static final int OVERSIZED_PLAN_STEPS = 300;
    private static final int NODE_LIMIT = 256;

    private RestPlanGameTests() {}

    @GameTest(batch = "rest_plan_goals", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void tasks_GoalsWithoutImplementedScriptOrTarget_Rejects422WithReasonAndChopAndFollowAccept202(
            GameTestHelper helper) throws IOException {
        RestFixture rest = arena(helper);
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        SpiritEntity follower = rest.spawn(helper, SPIRIT_START);
        ServerPlayer player = rest.mockPlayer(helper, PLAYER_AT);
        String path = "/v1/bots/" + spirit.getStringUUID() + "/tasks";
        String followerPath = "/v1/bots/" + follower.getStringUUID() + "/tasks";
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest.post(
                        path, goalBody("атакуй зомби", ",\"params\":{\"target\":123}"))
                .thenCompose(home -> {
                    expectInvalidPlan(
                            problems,
                            home,
                            "attack",
                            "цель «атакуй зомби» с target=123: неверный тип параметра attack");
                    return rest.post(path, goalBody("абракадабра", ""));
                })
                .thenCompose(nonsense -> {
                    expectInvalidPlan(problems, nonsense, "абракадабра", "бессмысленная цель");
                    return rest.post(path, goalBody("следуй за мной", ""));
                })
                .thenCompose(follow -> {
                    expectInvalidPlan(problems, follow, "target", "цель «следуй за мной» без params.target");
                    problems.expect(
                            rest.gateway().taskRegistry().size() == 0,
                            "Отказанные цели не должны оставлять записей в реестре, записей "
                                    + rest.gateway().taskRegistry().size());
                    return rest.post(path, goalBody("срубить дерево", ""));
                })
                .thenCompose(chop -> {
                    problems.expect(
                            chop.status() == 202,
                            "Цель «срубить дерево» (chop_tree реализован) обязана приниматься (202), получен "
                                    + chop.status() + ": " + chop.body());
                    return rest.post(
                            followerPath,
                            goalBody(
                                    "следуй за мной",
                                    ",\"params\":{\"target\":\""
                                            + player.getName().getString() + "\"}"));
                })
                .thenApply(accepted -> {
                    problems.expect(
                            accepted.status() == 202,
                            "Цель «следуй за мной» с params.target и игроком обязана приниматься (202), получен "
                                    + accepted.status() + ": " + accepted.body());
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario);
    }

    @GameTest(batch = "rest_plan_dry_run", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void plan_ExtraParamAndOversizedAndDryRun_Rejects422WithReasonAndWorldUntouched(GameTestHelper helper)
            throws IOException {
        RestFixture rest = arena(helper);
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        String taskPath = "/v1/bots/" + spirit.getStringUUID() + "/tasks";
        RestFixture.Problems problems = new RestFixture.Problems();
        int hopsBefore = rest.gateway().serverCall().submittedCount();
        String extraParam =
                "{\"type\":\"script\",\"script\":\"goto\",\"params\":{\"x\":1,\"y\":64,\"z\":1,\"speed\":9}}";
        CompletableFuture<List<String>> scenario = rest.post(taskPath, planBody(extraParam))
                .thenCompose(extra -> {
                    expectInvalidPlan(problems, extra, "speed", "план с лишним параметром speed у goto");
                    return rest.post(taskPath, planBody(oversizedPlan()));
                })
                .thenCompose(oversized -> {
                    expectInvalidPlan(
                            problems,
                            oversized,
                            String.valueOf(NODE_LIMIT),
                            "план из " + OVERSIZED_PLAN_STEPS + " узлов");
                    return rest.post(
                            "/v1/plan",
                            "{\"plan\":{\"type\":\"script\",\"script\":\"goto\",\"params\":"
                                    + "{\"x\":1,\"y\":64,\"z\":1}}}");
                })
                .thenCompose(valid -> {
                    problems.expect(
                            valid.status() == 200 && valid.json().path("valid").asBoolean(),
                            "POST /v1/plan с валидным планом обязан отвечать 200 {\"valid\":true}: " + valid.status()
                                    + " " + valid.body());
                    return rest.post("/v1/plan", "{\"plan\":" + extraParam + "}");
                })
                .thenCompose(invalid -> {
                    expectInvalidPlan(problems, invalid, "speed", "POST /v1/plan с лишним параметром");
                    return rest.post("/v1/plan", "{}");
                })
                .thenApply(missing -> {
                    problems.expect(
                            missing.status() == 400
                                    && "invalid_request"
                                            .equals(missing.json()
                                                    .path("error")
                                                    .path("code")
                                                    .asText()),
                            "POST /v1/plan без поля plan обязан отвечать 400 invalid_request: " + missing.status() + " "
                                    + missing.body());
                    int hops = rest.gateway().serverCall().submittedCount() - hopsBefore;
                    problems.expect(
                            hops == 0,
                            "Проверка плана и отказы 422 не обращаются к серверному потоку: лишних переходов " + hops);
                    problems.expect(
                            rest.gateway().taskRegistry().size() == 0,
                            "Сухой прогон и отказы не создают задач, записей "
                                    + rest.gateway().taskRegistry().size());
                    return problems.found();
                });
        RestFixture.await(
                helper,
                rest,
                scenario,
                () -> helper.assertTrue(
                        spirit.taskStatus().isEmpty(),
                        "Сухой прогон и отказы не должны запускать духа: у него есть задача "
                                + spirit.taskStatus().orElse(null)));
    }

    private static void expectInvalidPlan(
            RestFixture.Problems problems, RestFixture.Reply reply, String mention, String scenario) {
        problems.expect(
                reply.status() == 422
                        && "invalid_plan"
                                .equals(reply.json().path("error").path("code").asText()),
                scenario + ": ожидался 422 invalid_plan, получено " + reply.status() + " " + reply.body());
        problems.expect(
                reply.json().path("error").path("message").asText().contains(mention),
                scenario + ": причина обязана называть '" + mention + "', получено " + reply.body());
    }

    private static String goalBody(String goal, String extraFields) {
        return "{\"request_id\":\"" + UUID.randomUUID() + "\",\"goal\":\"" + goal + "\"" + extraFields + "}";
    }

    private static String planBody(String plan) {
        return "{\"request_id\":\"" + UUID.randomUUID() + "\",\"plan\":" + plan + "}";
    }

    private static String oversizedPlan() {
        StringBuilder steps = new StringBuilder();
        for (int i = 0; i < OVERSIZED_PLAN_STEPS; i++) {
            if (i > 0) {
                steps.append(',');
            }
            steps.append("{\"type\":\"script\",\"script\":\"goto\",\"params\":{\"x\":1,\"y\":64,\"z\":1}}");
        }
        return "{\"type\":\"sequence\",\"steps\":[" + steps + "]}";
    }

    private static RestFixture arena(GameTestHelper helper) throws IOException {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        return RestFixture.start(helper.getLevel().getServer());
    }
}
