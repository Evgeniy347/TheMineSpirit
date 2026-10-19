package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.rest.SpiritIndex;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RestScaleGameTests {
    private static final int PARALLEL_REQUESTS = 20;
    private static final int SPIRITS = 30;
    private static final int FIVE_OBSERVES = 5;

    private RestScaleGameTests() {
    }

    @GameTest(batch = "rest_concurrency", template = "empty", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void concurrency_TwentyParallelBotsRequests_AllOk(GameTestHelper helper)
            throws IOException {
        RestFixture rest = RestFixture.start(helper.getLevel().getServer());
        List<CompletableFuture<RestFixture.Reply>> replies = new ArrayList<>();
        for (int i = 0; i < PARALLEL_REQUESTS; i++) {
            replies.add(rest.get("/v1/bots"));
        }
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = CompletableFuture.allOf(replies.toArray(new CompletableFuture<?>[0]))
                .thenApply(done -> {
                    long ok = replies.stream().filter(reply -> reply.join().status() == 200).count();
                    problems.expect(ok == PARALLEL_REQUESTS,
                            "Все " + PARALLEL_REQUESTS + " параллельных запросов обязаны получить 200, получили " + ok);
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario);
    }

    @GameTest(batch = "rest_observe_five", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void observe_FiveAtOnce_FifthRateLimited(GameTestHelper helper)
            throws IOException {
        GameTestFixtures.sweepEntities(helper);
        RestFixture rest = RestFixture.start(helper.getLevel().getServer());
        SpiritEntity spirit = rest.spawn(helper, new BlockPos(1, 1, 1));
        String path = "/v1/bots/" + spirit.getStringUUID() + "/observe";
        List<CompletableFuture<RestFixture.Reply>> replies = new ArrayList<>();
        for (int i = 0; i < FIVE_OBSERVES; i++) {
            replies.add(rest.post(path, "{\"radius\":32}".getBytes(StandardCharsets.UTF_8)));
        }
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = CompletableFuture.allOf(replies.toArray(new CompletableFuture<?>[0]))
                .thenApply(done -> {
                    long ok = replies.stream().filter(reply -> reply.join().status() == 200).count();
                    long limited = replies.stream()
                            .filter(reply -> reply.join().status() == 429
                                    && reply.join().json().path("error").path("code").asText().equals("rate_limited"))
                            .count();
                    problems.expect(ok == FIVE_OBSERVES - 1 && limited == 1,
                            "Пять запросов в один тик обязаны дать ровно четыре 200 и один 429 rate_limited, "
                                    + "получили ok " + ok + ", rate_limited " + limited);
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario);
    }

    @GameTest(batch = "rest_index_thirty", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void index_ThirtySpirits_SnapshotReadsExactlyThirtyEntries(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        MinecraftServer minecraft = helper.getLevel().getServer();
        SpiritIndex index = new SpiritIndex(minecraft);
        index.register(NeoForge.EVENT_BUS);
        List<SpiritEntity> spirits = new ArrayList<>();
        for (int i = 0; i < SPIRITS; i++) {
            spirits.add(helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1)));
        }
        long before = index.entriesRead();

        int listed = index.snapshot().size();
        long read = index.entriesRead() - before;

        index.unregister(NeoForge.EVENT_BUS);
        spirits.forEach(SpiritEntity::discard);
        helper.assertTrue(listed == SPIRITS,
                "Индекс обязан видеть всех " + SPIRITS + " заспавненных духов, увидел " + listed
                        + ": join-событие не доходит до индекса");
        helper.assertTrue(read == SPIRITS,
                "Список духов обязан читать ровно по записи на духа (" + SPIRITS + "), прочитано " + read
                        + ": обход сущностей мира вместо индекса растёт с числом сущностей, а не духов");
        helper.succeed();
    }
}
