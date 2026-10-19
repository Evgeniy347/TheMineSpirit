package com.theminespirit.gametest;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.SpiritEntity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RestEventsGameTests {
    private static final BlockPos SPIRIT_START = new BlockPos(1, 1, 1);
    private static final BlockPos FAR_CORNER = new BlockPos(7, 1, 7);
    private static final BlockPos IN_THE_AIR = new BlockPos(4, 12, 4);
    private static final int LEGS = 4;
    private static final int LONG_LEGS = 16;
    private static final long STOP_CLOSE_SECONDS = 5;
    private static final BlockPos PLAYER_AT = new BlockPos(5, 1, 5);
    private static final int MAX_STREAMS = 4;
    private static final int BURST_REQUESTS = 100;
    private static final int BUCKET_CAPACITY = 40;
    private static final int MIN_LIMITED_IN_BURST = 30;

    private RestEventsGameTests() {}

    @GameTest(batch = "rest_events_flow", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void events_SequenceOfGotos_StateFirstThenProgressThenFinalSucceededAndStreamCloses(
            GameTestHelper helper) throws IOException {
        RestFixture rest = arena(helper);
        SpiritEntity spirit = rest.spawnHeldUntilStreamsOpen(helper, SPIRIT_START, 1);
        AtomicReference<RestFixture.SseCapture> stream = new AtomicReference<>();
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = postSequence(helper, rest, spirit, LEGS)
                .thenApply(accepted -> {
                    problems.expect(accepted.status() == 202, "POST tasks обязан отвечать 202: " + accepted.body());
                    stream.set(rest.stream(
                            "/v1/tasks/" + accepted.json().path("task_id").asText() + "/events", Map.of()));
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario, () -> {
            helper.assertTrue(
                    stream.get() != null && stream.get().finished(),
                    "Поток событий обязан закрыться после финального state; кадры пока: "
                            + (stream.get() == null
                                    ? "нет потока"
                                    : stream.get().lines()));
            List<RestFixture.Frame> frames = stream.get().frames();
            helper.assertTrue(
                    stream.get().contentType().startsWith("text/event-stream"),
                    "Content-Type потока обязан быть text/event-stream, получен "
                            + stream.get().contentType());
            helper.assertTrue(
                    !frames.isEmpty() && frames.get(0).event().equals("state"),
                    "Первым кадром обязан идти текущий state, получено " + frames);
            helper.assertTrue(
                    frames.stream().anyMatch(frame -> frame.event().equals("progress")),
                    "Между шагами sequence обязан прийти хотя бы один progress, кадры " + frames);
            RestFixture.Frame last = frames.get(frames.size() - 1);
            helper.assertTrue(
                    last.event().equals("state")
                            && last.json().path("state").asText().equals("succeeded"),
                    "Завершающим кадром обязан быть state succeeded, получено " + last);
            long previous = -1;
            for (RestFixture.Frame frame : frames.subList(1, frames.size())) {
                helper.assertTrue(frame.idNumber() > previous, "id кадров обязаны строго расти: " + frames);
                previous = frame.idNumber();
            }
        });
    }

    @GameTest(batch = "rest_events_resume", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void events_ReconnectWithLastEventId_ReceivesOnlyLaterFramesThenFinalState(GameTestHelper helper)
            throws IOException {
        RestFixture rest = arena(helper);
        SpiritEntity spirit = rest.spawnHeldUntilStreamsOpen(helper, SPIRIT_START, 1);
        AtomicReference<String> eventsPath = new AtomicReference<>();
        AtomicReference<RestFixture.SseCapture> first = new AtomicReference<>();
        AtomicReference<RestFixture.SseCapture> second = new AtomicReference<>();
        AtomicReference<Long> resumedAfter = new AtomicReference<>();
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = postSequence(helper, rest, spirit, LEGS)
                .thenApply(accepted -> {
                    problems.expect(accepted.status() == 202, "POST tasks обязан отвечать 202: " + accepted.body());
                    eventsPath.set(
                            "/v1/tasks/" + accepted.json().path("task_id").asText() + "/events");
                    first.set(rest.stream(eventsPath.get(), Map.of()));
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario, () -> {
            if (second.get() == null) {
                RestFixture.Frame progress = first.get().frames().stream()
                        .filter(frame -> frame.event().equals("progress"))
                        .findFirst()
                        .orElse(null);
                helper.assertTrue(
                        progress != null,
                        "Ждём первый progress в первом потоке: " + first.get().lines());
                first.get().cancel();
                resumedAfter.set(progress.idNumber());
                second.set(rest.stream(eventsPath.get(), Map.of("Last-Event-ID", progress.id())));
            }
            helper.assertTrue(
                    second.get().finished(),
                    "Второй поток обязан закрыться после финального state; кадры: "
                            + second.get().lines());
            List<RestFixture.Frame> frames = second.get().frames();
            helper.assertTrue(!frames.isEmpty(), "После переподключения обязаны прийти оставшиеся кадры");
            for (RestFixture.Frame frame : frames) {
                helper.assertTrue(
                        frame.idNumber() > resumedAfter.get(),
                        "Переподключение с Last-Event-ID " + resumedAfter.get() + " обязано отдавать только более "
                                + "поздние кадры, получен " + frame);
            }
            helper.assertTrue(
                    frames.get(0).idNumber() == resumedAfter.get() + 1,
                    "Первым кадром после Last-Event-ID " + resumedAfter.get() + " обязан идти следующий кадр журнала "
                            + "с id " + (resumedAfter.get() + 1) + ", а не снимок состояния: " + frames.get(0));
            for (int i = 1; i < frames.size(); i++) {
                helper.assertTrue(
                        frames.get(i).idNumber() == frames.get(i - 1).idNumber() + 1,
                        "Кадры журнала обязаны идти подряд без пропусков и повторов: " + frames);
            }
            RestFixture.Frame last = frames.get(frames.size() - 1);
            helper.assertTrue(
                    last.event().equals("state")
                            && last.json().path("state").asText().equals("succeeded"),
                    "Продолжение обязано дойти до завершающего state succeeded, получено " + last);
        });
    }

    @GameTest(batch = "rest_events_failed", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void events_GotoToUnreachableAirPoint_ScriptFailedWithReasonThenStateFailed(GameTestHelper helper)
            throws IOException {
        RestFixture rest = arena(helper);
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        BlockPos air = helper.absolutePos(IN_THE_AIR);
        String body = "{\"request_id\":\"" + UUID.randomUUID() + "\",\"plan\":{\"type\":\"script\",\"script\":\"goto\","
                + "\"params\":{\"x\":" + air.getX() + ",\"y\":" + air.getY() + ",\"z\":" + air.getZ() + "}}}";
        AtomicReference<RestFixture.SseCapture> stream = new AtomicReference<>();
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest.post("/v1/bots/" + spirit.getStringUUID() + "/tasks", body)
                .thenApply(accepted -> {
                    problems.expect(accepted.status() == 202, "POST tasks обязан отвечать 202: " + accepted.body());
                    stream.set(rest.stream(
                            "/v1/tasks/" + accepted.json().path("task_id").asText() + "/events",
                            Map.of("Last-Event-ID", "1")));
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario, () -> {
            helper.assertTrue(
                    stream.get() != null && stream.get().finished(),
                    "Поток обязан закрыться после провала задачи; кадры: "
                            + (stream.get() == null
                                    ? "нет потока"
                                    : stream.get().lines()));
            List<RestFixture.Frame> frames = stream.get().frames();
            JsonNode failed = frames.stream()
                    .filter(frame -> frame.event().equals("script_failed"))
                    .findFirst()
                    .map(RestFixture.Frame::json)
                    .orElse(null);
            helper.assertTrue(failed != null, "Провал goto обязан дать кадр script_failed, кадры " + frames);
            helper.assertTrue(
                    failed.path("script").asText().equals("goto")
                            && !failed.path("reason").asText().isBlank(),
                    "script_failed обязан называть скрипт goto и причину, получено " + failed);
            RestFixture.Frame last = frames.get(frames.size() - 1);
            helper.assertTrue(
                    last.event().equals("state")
                            && last.json().path("state").asText().equals("failed"),
                    "Завершающим кадром обязан быть state failed, получено " + last);
        });
    }

    @GameTest(batch = "rest_events_limit", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void events_FifthConcurrentStream_Returns429ThenSlotFreedAfterClientLeaves(GameTestHelper helper)
            throws IOException {
        RestFixture rest = arena(helper);
        SpiritEntity spirit = rest.spawnHeldUntilStreamsOpen(helper, SPIRIT_START, MAX_STREAMS);
        List<RestFixture.SseCapture> open = new ArrayList<>();
        AtomicReference<String> eventsPath = new AtomicReference<>();
        AtomicReference<RestFixture.SseCapture> fifth = new AtomicReference<>();
        AtomicReference<RestFixture.SseCapture> retry = new AtomicReference<>();
        AtomicInteger retries = new AtomicInteger();
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = postSequence(helper, rest, spirit, LONG_LEGS)
                .thenApply(accepted -> {
                    problems.expect(accepted.status() == 202, "POST tasks обязан отвечать 202: " + accepted.body());
                    eventsPath.set(
                            "/v1/tasks/" + accepted.json().path("task_id").asText() + "/events");
                    for (int i = 0; i < MAX_STREAMS; i++) {
                        open.add(rest.stream(eventsPath.get(), Map.of()));
                    }
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario, () -> {
            for (RestFixture.SseCapture stream : open) {
                helper.assertTrue(
                        stream.status().isDone() && stream.status().join() == 200,
                        "Первые " + MAX_STREAMS + " потоков обязаны открыться со статусом 200");
            }
            if (fifth.get() == null) {
                fifth.set(rest.stream(eventsPath.get(), Map.of()));
            }
            helper.assertTrue(fifth.get().status().isDone(), "Ответ на пятый поток ещё не пришёл");
            helper.assertTrue(
                    fifth.get().status().join() == 429,
                    "Пятый одновременный поток обязан получить 429, получен "
                            + fifth.get().status().join());
            helper.assertTrue(
                    fifth.get().lines().stream().anyMatch(line -> line.contains("rate_limited")),
                    "429 пятого потока обязан нести код rate_limited: "
                            + fifth.get().lines());
            if (retry.get() == null) {
                open.get(0).cancel();
            }
            if (retry.get() == null
                    || retry.get().status().isDone() && retry.get().status().join() == 429) {
                retry.set(rest.stream(eventsPath.get(), Map.of()));
                retries.incrementAndGet();
            }
            helper.assertTrue(
                    retry.get().status().isDone() && retry.get().status().join() == 200,
                    "После ухода клиента слот обязан освободиться, и новый поток откроется со статусом 200; попыток "
                            + retries.get() + ", последний статус "
                            + (retry.get().status().isDone()
                                    ? retry.get().status().join()
                                    : "нет ответа"));
        });
    }

    @GameTest(batch = "rest_events_stop", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void events_GatewayStopped_OpenStreamEndsWithoutWaitingForKeepalive(GameTestHelper helper)
            throws IOException {
        RestFixture rest = arena(helper);
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        ServerPlayer player = rest.mockPlayer(helper, PLAYER_AT);
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest.post(
                        "/v1/bots/" + spirit.getStringUUID() + "/tasks",
                        RestFixture.followGoalBody(UUID.randomUUID().toString(), player))
                .thenCompose(accepted -> {
                    RestFixture.SseCapture stream = rest.stream(
                            "/v1/tasks/" + accepted.json().path("task_id").asText() + "/events", Map.of());
                    return stream.status()
                            .thenCompose(status -> {
                                problems.expect(
                                        status == 200, "Поток событий обязан открыться со статусом 200: " + status);
                                return rest.onServer(() -> {
                                    rest.gateway().stop();
                                    return null;
                                });
                            })
                            .thenCompose(
                                    stopped -> stream.whenFinished().orTimeout(STOP_CLOSE_SECONDS, TimeUnit.SECONDS))
                            .handle((finished, error) -> {
                                problems.expect(
                                        error == null,
                                        "Остановка шлюза обязана закрывать открытые потоки событий за "
                                                + STOP_CLOSE_SECONDS + " с, а не ждать кипалайва: поток остался "
                                                + "открытым, " + error);
                                return problems.found();
                            });
                });
        RestFixture.await(helper, rest, scenario);
    }

    @GameTest(batch = "rest_events_burst", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void requests_BurstOfHundredFromOneAddress_PartRejectedWith429AndRetryAfter(GameTestHelper helper)
            throws IOException {
        RestFixture rest = arena(helper);
        List<CompletableFuture<RestFixture.Reply>> replies = new ArrayList<>();
        for (int i = 0; i < BURST_REQUESTS; i++) {
            replies.add(rest.get("/v1/capabilities"));
        }
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = CompletableFuture.allOf(replies.toArray(new CompletableFuture<?>[0]))
                .thenApply(done -> {
                    long ok = replies.stream()
                            .filter(reply -> reply.join().status() == 200)
                            .count();
                    List<RestFixture.Reply> limited = replies.stream()
                            .map(CompletableFuture::join)
                            .filter(reply -> reply.status() == 429)
                            .toList();
                    problems.expect(
                            ok >= BUCKET_CAPACITY,
                            "Ведро адреса вмещает " + BUCKET_CAPACITY + " запросов: минимум столько обязано пройти, "
                                    + "прошло " + ok);
                    problems.expect(
                            limited.size() >= MIN_LIMITED_IN_BURST,
                            "Из " + BURST_REQUESTS + " мгновенных запросов не менее " + MIN_LIMITED_IN_BURST
                                    + " обязаны получить 429, получили " + limited.size() + " (прошло " + ok + ")");
                    problems.expect(
                            ok + limited.size() == BURST_REQUESTS, "Каждый запрос обязан получить либо 200, либо 429");
                    for (RestFixture.Reply reply : limited) {
                        String retryAfter =
                                reply.headers().firstValue("Retry-After").orElse("");
                        problems.expect(
                                retryAfter.matches("[1-9][0-9]*"),
                                "429 обязан нести Retry-After в целых секундах (минимум 1), получено '" + retryAfter
                                        + "'");
                        problems.expect(
                                reply.json().path("error").path("code").asText().equals("rate_limited"),
                                "429 обязан нести код rate_limited: " + reply.body());
                    }
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario);
    }

    private static CompletableFuture<RestFixture.Reply> postSequence(
            GameTestHelper helper, RestFixture rest, SpiritEntity spirit, int legs) {
        StringBuilder steps = new StringBuilder();
        for (int i = 0; i < legs; i++) {
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
        String body = "{\"request_id\":\"" + UUID.randomUUID() + "\",\"plan\":{\"type\":\"sequence\",\"steps\":["
                + steps + "]}}";
        return rest.post("/v1/bots/" + spirit.getStringUUID() + "/tasks", body);
    }

    private static RestFixture arena(GameTestHelper helper) throws IOException {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        return RestFixture.start(helper.getLevel().getServer());
    }
}
