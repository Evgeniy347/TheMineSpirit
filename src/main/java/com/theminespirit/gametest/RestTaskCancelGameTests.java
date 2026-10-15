package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RestTaskCancelGameTests {
    private static final BlockPos SPIRIT_START = new BlockPos(1, 1, 1);
    private static final BlockPos PLAYER_FAR = new BlockPos(7, 1, 7);
    private static final BlockPos PLAYER_NEAR = new BlockPos(1, 1, 7);
    private static final BlockPos MINER_START = new BlockPos(4, 1, 4);
    private static final int PLAYER_SHUTTLE_TICKS = 40;
    private static final double MOVED_ENOUGH_BLOCKS = 1.5;
    private static final double STILL_WITHIN_BLOCKS = 0.5;
    private static final int STILL_TICKS = 20;
    private static final int QUIET_TICKS = 40;
    private static final int HEAP_SIZE = 9;
    private static final int RADIUS = 16;

    private RestTaskCancelGameTests() {
    }

    private record Stopped(Vec3 position, long gameTime) {
    }

    @GameTest(batch = "rest_cancel_move", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void delete_RunningGoto_Returns200CancelledTwiceAnd404ForUnknownAndSpiritStops(
            GameTestHelper helper) throws IOException {
        RestFixture rest = arena(helper);
        SpiritEntity spirit = rest.spawn(helper, SPIRIT_START);
        ServerPlayer player = rest.mockPlayer(helper, PLAYER_FAR);
        helper.onEachTick(() -> RestFixture.moveTo(helper, player,
                helper.getTick() / PLAYER_SHUTTLE_TICKS % 2 == 0 ? PLAYER_FAR : PLAYER_NEAR));
        String botPath = "/v1/bots/" + spirit.getStringUUID();
        Vec3 start = spirit.position();
        CompletableFuture<Void> moved = new CompletableFuture<>();
        helper.onEachTick(() -> {
            if (!moved.isDone() && spirit.position().distanceTo(start) >= MOVED_ENOUGH_BLOCKS) {
                moved.complete(null);
            }
        });
        AtomicReference<Stopped> stopped = new AtomicReference<>();
        RestFixture.Problems problems = new RestFixture.Problems();
        CompletableFuture<List<String>> scenario = rest.post(botPath + "/tasks",
                RestFixture.followGoalBody(UUID.randomUUID().toString(), player))
                .thenCompose(accepted -> {
                    problems.expect(accepted.status() == 202, "POST tasks обязан отвечать 202: " + accepted.body());
                    String taskPath = "/v1/tasks/" + accepted.json().path("task_id").asText();
                    return moved.thenCompose(walking -> rest.delete(taskPath)).thenCompose(first -> {
                        problems.expect(first.status() == 200 && first.json().path("state").asText().equals("cancelled"),
                                "DELETE идущей задачи обязан отвечать 200 со state=cancelled, получено "
                                        + first.status() + ": " + first.body());
                        return rest.delete(taskPath).thenCompose(second -> {
                            problems.expect(second.status() == 200 && second.body().equals(first.body()),
                                    "Повторный DELETE обязан отвечать 200 тем же состоянием: " + first.body() + " и "
                                            + second.status() + " " + second.body());
                            return rest.delete("/v1/tasks/t-9999").thenCompose(unknown -> {
                                problems.expect(unknown.status() == 404,
                                        "DELETE неизвестной задачи обязан отвечать 404, получен " + unknown.status()
                                                + ": " + unknown.body());
                                return rest.get(botPath).thenCompose(bot -> {
                                    problems.expect(bot.json().path("current_task").isNull()
                                            && bot.json().path("state").asText().equals("idle"),
                                            "После отмены дух обязан быть idle без current_task: " + bot.body());
                                    return rest.onServer(() -> new Stopped(spirit.position(),
                                            helper.getLevel().getGameTime()));
                                });
                            });
                        });
                    });
                }).thenApply(snapshot -> {
                    stopped.set(snapshot);
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario, () -> {
            Stopped at = stopped.get();
            helper.assertTrue(helper.getLevel().getGameTime() >= at.gameTime() + STILL_TICKS,
                    "Ждём " + STILL_TICKS + " тиков после отмены");
            helper.assertTrue(spirit.position().distanceTo(at.position()) <= STILL_WITHIN_BLOCKS,
                    "После DELETE дух обязан стоять: за " + STILL_TICKS + " тиков он ушёл на "
                            + spirit.position().distanceTo(at.position())
                            + " блока, значит навигация не остановлена и отмена не дошла до духа");
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.CANCELLED,
                    "У духа задача обязана быть CANCELLED, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
        });
    }

    @GameTest(batch = "rest_cancel_mining", template = "arena", timeoutTicks = RestFixture.TIMEOUT_TICKS)
    public static void delete_GatherMidBreaking_WorldQuietAndNothingLostAfterFortyTicks(GameTestHelper helper)
            throws IOException {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int x = 1; x <= 3; x++) {
            for (int z = 4; z <= 6; z++) {
                helper.setBlock(x, 1, z, Blocks.COBBLESTONE);
            }
        }
        RestFixture rest = RestFixture.start(helper.getLevel().getServer());
        SpiritEntity spirit = rest.spawn(helper, MINER_START);
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        CompletableFuture<Void> breaking = new CompletableFuture<>();
        helper.onEachTick(() -> {
            if (!breaking.isDone() && cobbleTotal(helper, spirit) > 0 && blocksLeft(helper) < HEAP_SIZE) {
                breaking.complete(null);
            }
        });
        AtomicReference<String> quietSnapshot = new AtomicReference<>();
        AtomicReference<Long> cancelledAt = new AtomicReference<>();
        RestFixture.Problems problems = new RestFixture.Problems();
        String body = "{\"request_id\":\"" + UUID.randomUUID() + "\",\"plan\":{\"type\":\"script\",\"script\":\"gather\","
                + "\"params\":{\"block\":\"cobblestone\",\"count\":6,\"radius\":" + RADIUS + "}}}";
        CompletableFuture<List<String>> scenario = rest.post("/v1/bots/" + spirit.getStringUUID() + "/tasks", body)
                .thenCompose(accepted -> {
                    problems.expect(accepted.status() == 202, "POST tasks обязан отвечать 202: " + accepted.body());
                    String taskPath = "/v1/tasks/" + accepted.json().path("task_id").asText();
                    return breaking.thenCompose(started -> rest.delete(taskPath)).thenCompose(deleted -> {
                        problems.expect(deleted.status() == 200
                                && deleted.json().path("state").asText().equals("cancelled"),
                                "DELETE посреди добычи обязан отвечать 200 cancelled: " + deleted.body());
                        return rest.onServer(() -> {
                            quietSnapshot.set(worldSnapshot(helper, spirit));
                            return helper.getLevel().getGameTime();
                        });
                    });
                }).thenApply(time -> {
                    cancelledAt.set(time);
                    return problems.found();
                });
        RestFixture.await(helper, rest, scenario, () -> {
            helper.assertTrue(helper.getLevel().getGameTime() >= cancelledAt.get() + QUIET_TICKS,
                    "Ждём " + QUIET_TICKS + " тиков после отмены");
            helper.assertTrue(worldSnapshot(helper, spirit).equals(quietSnapshot.get()),
                    "Через " + QUIET_TICKS + " тиков после DELETE мир не должен меняться дальше: было "
                            + quietSnapshot.get() + ", стало " + worldSnapshot(helper, spirit));
            helper.assertTrue(cobbleTotal(helper, spirit) + blocksLeft(helper) == HEAP_SIZE,
                    "Каждый блок кучи обязан быть либо цел, либо сломан с дропом (в инвентаре или на земле): целых "
                            + blocksLeft(helper) + ", булыжника у духа и на земле " + cobbleTotal(helper, spirit)
                            + " из " + HEAP_SIZE);
            GameTestFixtures.finish(helper);
        });
    }

    private static int blocksLeft(GameTestHelper helper) {
        int left = 0;
        for (int x = 1; x <= 3; x++) {
            for (int z = 4; z <= 6; z++) {
                if (helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.COBBLESTONE)) {
                    left++;
                }
            }
        }
        return left;
    }

    private static int cobbleTotal(GameTestHelper helper, SpiritEntity spirit) {
        return GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE)
                + GameTestFixtures.countInWorld(helper, Items.COBBLESTONE);
    }

    private static String worldSnapshot(GameTestHelper helper, SpiritEntity spirit) {
        return blocksLeft(helper) + " целых, булыжника " + cobbleTotal(helper, spirit);
    }

    private static RestFixture arena(GameTestHelper helper) throws IOException {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        return RestFixture.start(helper.getLevel().getServer());
    }
}
