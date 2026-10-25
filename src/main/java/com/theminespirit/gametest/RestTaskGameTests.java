package com.theminespirit.gametest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RestTaskGameTests {
    private static final BlockPos SPIRIT_START = new BlockPos(1, 1, 1);
    private static final BlockPos FAR_CORNER = new BlockPos(7, 1, 7);
    private static final BlockPos NEAR_CORNER = new BlockPos(2, 1, 1);
    private static final BlockPos PLAYER_AT = new BlockPos(5, 1, 5);
    private static final int RELOAD_AFTER_TICKS = 30;
    private static final int GONE_MIN_TICKS = 98;
    private static final int GONE_MAX_TICKS = 104;

    private RestTaskGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void tasks_PlanGoto_Accepts202AndBotReportsCurrentTaskAndRefusesSecondTask(GameTestHelper helper)
            throws IOException {
        RestFixture rest = arena(helper);
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        ServerPlayer player = rest.mockPlayer(helper, PLAYER_AT);
        String botPath = "/v1/bots/" + spirit.getStringUUID();
        String requestId = UUID.randomUUID().toString();
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest.post(
                        botPath + "/tasks", RestFixture.followGoalBody(requestId, player))
                .thenCompose(accepted -> {
                    JsonNode json = accepted.json();
                    problems.expect(
                            accepted.status() == 202,
                            "POST tasks с планом goto обязан отвечать 202, получен " + accepted.status() + ": "
                                    + accepted.body());
                    problems.expect(
                            "accepted".equals(json.path("state").asText())
                                    && json.path("bot").asText().equals(spirit.getStringUUID())
                                    && json.path("request_id").asText().equals(requestId),
                            "202 обязан нести state=accepted, bot и request_id запроса: " + accepted.body());
                    String taskId = json.path("task_id").asText();
                    return rest.get(botPath).thenCompose(bot -> {
                        problems.expect(
                                bot.json().path("current_task").asText().equals(taskId),
                                "GET bots/{id} обязан показывать current_task=" + taskId + " из реестра, получено: "
                                        + bot.body());
                        problems.expect(
                                "busy".equals(bot.json().path("state").asText()),
                                "Дух с запущенной задачей обязан быть busy: " + bot.body());
                        return rest.post(
                                        botPath + "/tasks",
                                        RestFixture.followGoalBody(
                                                UUID.randomUUID().toString(), player))
                                .thenApply(second -> {
                                    problems.expect(
                                            second.status() == 409,
                                            "Второй POST с новым request_id к занятому духу обязан давать 409, получен "
                                                    + second.status() + ": " + second.body());
                                    problems.expect(
                                            second.body().contains(taskId),
                                            "409 обязан называть текущую задачу " + taskId + ": " + second.body());
                                    return problems.found();
                                });
                    });
                });
        RestFixture.await(helper, rest, scenario);
    }

    @SceneStep(template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void tasks_ReplayAndChangedBody_SameTaskWithoutRestartThenConflict(GameTestHelper helper)
            throws IOException {
        RestFixture rest = arena(helper);
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        ServerPlayer player = rest.mockPlayer(helper, PLAYER_AT);
        String botPath = "/v1/bots/" + spirit.getStringUUID();
        String requestId = UUID.randomUUID().toString();
        String body = RestFixture.followGoalBody(requestId, player);
        String changedBody = gotoBody(helper, requestId, NEAR_CORNER);
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest.post(botPath + "/tasks", body)
                .thenCompose(first -> {
                    int hopsAfterFirst = rest.gateway().serverCall().submittedCount();
                    return rest.post(botPath + "/tasks", body)
                            .thenCompose(replay -> rest.post(botPath + "/tasks", changedBody)
                                    .thenApply(changed -> {
                                        problems.expect(
                                                replay.status() == 202,
                                                "Повтор того же запроса к духу, занятому этой же задачей, обязан давать 202, получен "
                                                        + replay.status() + ": " + replay.body());
                                        problems.expect(
                                                replay.json()
                                                        .path("task_id")
                                                        .asText()
                                                        .equals(first.json()
                                                                .path("task_id")
                                                                .asText()),
                                                "Повтор обязан вернуть тот же task_id: " + first.body() + " и "
                                                        + replay.body());
                                        problems.expect(
                                                changed.status() == 409
                                                        && changed.body().contains(requestId),
                                                "Тот же request_id с другим телом обязан давать 409, называя request_id: "
                                                        + changed.status() + " " + changed.body());
                                        int hopsAfterRepeats =
                                                rest.gateway().serverCall().submittedCount();
                                        problems.expect(
                                                hopsAfterRepeats == hopsAfterFirst,
                                                "Повтор и отказ по request_id не должны обращаться к серверному потоку (дух не "
                                                        + "перезапускается): переходов " + hopsAfterFirst
                                                        + " после первого запроса и "
                                                        + hopsAfterRepeats + " после повторов");
                                        problems.expect(
                                                rest.gateway().taskRegistry().size() == 1,
                                                "В реестре обязана остаться одна запись, а не "
                                                        + rest.gateway()
                                                                .taskRegistry()
                                                                .size());
                                        return problems.found();
                                    }));
                });
        RestFixture.await(helper, rest, scenario);
    }

    @SceneStep(template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void tasks_SpiritBusyWithCommandTask_Returns409OutsideRestWithoutReservation(GameTestHelper helper)
            throws IOException {
        RestFixture rest = arena(helper);
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        spirit.startTask(RestFixture.followPlan(rest.mockPlayer(helper, PLAYER_AT)));
        String botPath = "/v1/bots/" + spirit.getStringUUID();
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest.post(
                        botPath + "/tasks", gotoBody(helper, UUID.randomUUID().toString(), NEAR_CORNER))
                .thenApply(reply -> {
                    problems.expect(
                            reply.status() == 409,
                            "Дух, занятый задачей команды /spirit, обязан давать 409, получен " + reply.status() + ": "
                                    + reply.body());
                    problems.expect(
                            reply.body().contains("вне REST"),
                            "409 обязан объяснять, что задача поставлена вне REST: " + reply.body());
                    problems.expect(
                            rest.gateway().taskRegistry().size() == 0,
                            "Отказ не должен оставлять запись в реестре, записей "
                                    + rest.gateway().taskRegistry().size());
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario);
    }

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

    @SceneStep(template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void tasks_SpiritReloadedWithinGrace_TaskStaysRunningAndSucceeds(GameTestHelper helper)
            throws IOException {
        RestFixture rest = arena(helper);
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        TaskPoller poller = TaskPoller.start(helper, rest);
        AtomicReference<CompoundTag> saved = new AtomicReference<>();
        AtomicLong goneAt = new AtomicLong(-1);
        AtomicBoolean reloaded = new AtomicBoolean();
        RestFixture.Problems problems = new RestFixture.Problems();
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (saved.get() != null && goneAt.get() < 0) {
                goneAt.set(now);
            }
            if (goneAt.get() >= 0 && !reloaded.get() && now - goneAt.get() >= RELOAD_AFTER_TICKS) {
                reloaded.set(true);
                SpiritEntity back = ModEntities.SPIRIT.get().create(helper.getLevel());
                back.load(saved.get());
                helper.getLevel().addFreshEntity(back);
                rest.track(back);
            }
        });
        CompletableFuture<List<String>> scenario = rest.post(
                        "/v1/bots/" + spirit.getStringUUID() + "/tasks",
                        gotoBody(helper, UUID.randomUUID().toString(), FAR_CORNER))
                .thenCompose(accepted -> {
                    poller.follow(accepted.json().path("task_id").asText());
                    return rest.onServer(() -> {
                        CompoundTag tag = new CompoundTag();
                        spirit.saveWithoutId(tag);
                        spirit.discard();
                        saved.set(tag);
                        return tag;
                    });
                })
                .thenApply(tag -> problems.found());
        RestFixture.await(helper, rest, scenario, () -> {
            helper.assertTrue(
                    reloaded.get(), "Дух ещё не возвращён в мир: прошло меньше " + RELOAD_AFTER_TICKS + " тиков");
            helper.assertTrue(
                    !poller.seen().contains("failed"),
                    "Дух, вернувшийся раньше чем через 100 тиков, не должен проваливать задачу; виденные состояния "
                            + poller.seen() + ", ошибка "
                            + poller.lastJson().path("error").asText());
            helper.assertTrue(
                    "succeeded".equals(poller.latest()),
                    "Восстановленный дух обязан довести REST-задачу до succeeded; последнее состояние "
                            + poller.latest() + ", виденные " + poller.seen());
        });
    }

    @SceneStep(template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void tasks_SpiritGoneForever_FailsAfterAboutHundredTicksWithSpiritGoneReason(GameTestHelper helper)
            throws IOException {
        RestFixture rest = arena(helper);
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        ServerPlayer player = rest.mockPlayer(helper, PLAYER_AT);
        TaskPoller poller = TaskPoller.start(helper, rest);
        AtomicLong discardedAt = new AtomicLong(-1);
        AtomicBoolean discard = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (discard.compareAndSet(true, false)) {
                spirit.discard();
                discardedAt.set(helper.getLevel().getGameTime());
            }
        });
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest.post(
                        "/v1/bots/" + spirit.getStringUUID() + "/tasks",
                        RestFixture.followGoalBody(UUID.randomUUID().toString(), player))
                .thenApply(accepted -> {
                    poller.follow(accepted.json().path("task_id").asText());
                    discard.set(true);
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario, () -> {
            helper.assertTrue(
                    "failed".equals(poller.latest()),
                    "Дух, не вернувшийся в мир, обязан провалить задачу; последнее состояние " + poller.latest()
                            + ", виденные " + poller.seen());
            String error = poller.lastJson().path("error").asText();
            helper.assertTrue(
                    error.startsWith("дух исчез"), "Причина обязана начинаться с «дух исчез», получено: " + error);
            long waited = poller.terminalSeenAt() - discardedAt.get();
            helper.assertTrue(
                    waited >= GONE_MIN_TICKS && waited <= GONE_MAX_TICKS,
                    "Задача обязана провалиться через " + GONE_MIN_TICKS + ".." + GONE_MAX_TICKS
                            + " тиков после исчезновения духа (регистр читается тиком, без задержки HTTP), а провалилась через "
                            + waited
                            + ": сторож отсутствия слишком короткий (задача падает при перезагрузке чанка) или "
                            + "слишком длинный (клиент долго видит running у мёртвой задачи)");
        });
    }

    @SceneStep(template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void tasks_CommandReplacesRestTask_RestTaskClosedAsCancelledOutsideRest(GameTestHelper helper)
            throws IOException {
        RestFixture rest = arena(helper);
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        ServerPlayer player = rest.mockPlayer(helper, PLAYER_AT);
        TaskPoller poller = TaskPoller.start(helper, rest);
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest.post(
                        "/v1/bots/" + spirit.getStringUUID() + "/tasks",
                        RestFixture.followGoalBody(UUID.randomUUID().toString(), player))
                .thenCompose(accepted -> {
                    poller.follow(accepted.json().path("task_id").asText());
                    return rest.onServer(() -> {
                        spirit.startTask(RestFixture.followPlan(player));
                        return problems.found();
                    });
                });
        RestFixture.await(helper, rest, scenario, () -> {
            helper.assertTrue(
                    "cancelled".equals(poller.latest()),
                    "Задача духа заменена командой: REST-задача обязана закрыться как cancelled, а не висеть running "
                            + "над чужой работой; последнее состояние " + poller.latest() + ", виденные "
                            + poller.seen());
            String error = poller.lastJson().path("error").asText();
            helper.assertTrue(
                    error.contains("вытеснена вне REST"),
                    "Клиент обязан увидеть причину: «вытеснена вне REST», получено: " + error);
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.RUNNING,
                    "Задача команды обязана продолжать идти: REST только закрывает свою запись, духа не трогает");
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

        long terminalSeenAt() {
            return terminalSeenAt.get();
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
