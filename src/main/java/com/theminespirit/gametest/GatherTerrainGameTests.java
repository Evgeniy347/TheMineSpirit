package com.theminespirit.gametest;

import static com.theminespirit.gametest.MineSceneSupport.CAVE_SHIFT_INSIDE_ARENA;
import static com.theminespirit.gametest.MineSceneSupport.DIRT_HEAP_TIMEOUT_TICKS;
import static com.theminespirit.gametest.MineSceneSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.MineSceneSupport.SPIRIT_CORNER;
import static com.theminespirit.gametest.MineSceneSupport.buildCave;
import static com.theminespirit.gametest.MineSceneSupport.countBlock;
import static com.theminespirit.gametest.MineSceneSupport.dropSummary;
import static com.theminespirit.gametest.MineSceneSupport.gatherCall;
import static com.theminespirit.gametest.MineSceneSupport.spawnWith;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.HarvestSession;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class GatherTerrainGameTests {
    private GatherTerrainGameTests() {}

    // [флак] замер 584 тика: 18 земли, подбор дропа зависит от случайного разлёта предметов;
    // потолок 1000 ≈ 1.7× замера
    @SceneStep(template = "arena", timeoutTicks = 1000)
    public static void gather_TerracottaHeapOfEighteen_CollectsAllAndKeepsFloor(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 1; z <= 3; z++) {
                    helper.setBlock(x + CAVE_SHIFT_INSIDE_ARENA, y, z + CAVE_SHIFT_INSIDE_ARENA, Blocks.TERRACOTTA);
                }
            }
        }
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_CORNER.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, gatherCall("terracotta", 18, 4), SETTLE_TICKS);
        HarvestProbe probe = HarvestProbe.attach(
                helper,
                spirit,
                run::started,
                () -> GameTestFixtures.countInInventory(spirit.getInventory(), Items.TERRACOTTA),
                () -> GameTestFixtures.countInWorld(helper, Items.TERRACOTTA),
                () -> dropSummary(helper, Items.TERRACOTTA),
                () -> countBlock(helper, Blocks.TERRACOTTA, CAVE_SHIFT_INSIDE_ARENA));
        helper.onEachTick(() -> {
            if (run.started()
                    && spirit.taskStatus().orElse(null) != RunStatus.SUCCEEDED
                    && helper.getTick() >= DIRT_HEAP_TIMEOUT_TICKS) {
                helper.fail(probe.report("тайм-аут " + DIRT_HEAP_TIMEOUT_TICKS + " тиков игры"));
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "куча из 18 блоков терракоты обязана собраться целиком, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет") + "\n" + probe.report("нет успеха"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.TERRACOTTA) == 18,
                    "в инвентаре обязано быть 18 терракоты, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.TERRACOTTA) + "\n"
                            + probe.report("недобрал"));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(2 + CAVE_SHIFT_INSIDE_ARENA, 0, 2 + CAVE_SHIFT_INSIDE_ARENA))
                            .is(Blocks.STONE),
                    "пол под кучей обязан остаться: блок под ногами не ломается" + "\n" + probe.report("пол сломан"));
            helper.assertTrue(
                    run.spiritTicks() <= 720,
                    "куча из 18 блоков терракоты обязана собраться за 720 тиков (замерено 565–612, порог ≈1.2×), "
                            + "заняло " + run.spiritTicks() + " тиков духа, рабочих " + probe.workingTicks()
                            + ", пауз " + probe.pausedTicks() + "\n" + probe.report("не уложился в 720"));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void gather_TerracottaRingAroundSealedPocket_MinedFromOutsideWithinReach(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        int[][] dirt = {{4, 1, 3}, {5, 1, 4}, {4, 1, 5}, {3, 1, 4}, {4, 2, 3}, {5, 2, 4}, {4, 2, 5}, {3, 2, 4}};
        for (int[] cell : dirt) {
            helper.setBlock(cell[0], cell[1], cell[2], Blocks.TERRACOTTA);
        }
        int[][] stone = {
            {3, 1, 3}, {5, 1, 3}, {3, 1, 5}, {5, 1, 5}, {6, 1, 4}, {2, 1, 4}, {4, 1, 6}, {4, 1, 2}, {3, 2, 3},
            {5, 2, 3}, {3, 2, 5}, {5, 2, 5}, {6, 2, 4}, {2, 2, 4}, {4, 2, 6}, {4, 2, 2}
        };
        for (int[] cell : stone) {
            helper.setBlock(cell[0], cell[1], cell[2], Blocks.STONE);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                helper.setBlock(4 + dx, 3, 4 + dz, Blocks.STONE);
            }
        }
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_CORNER.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, gatherCall("terracotta", 8, 4), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "терракота вокруг запечатанной стоянки добывается снаружи, со стоянок в досягаемости (упрощение 44.3), "
                            + "статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    countBlock(helper, Blocks.TERRACOTTA, CAVE_SHIFT_INSIDE_ARENA) == 0
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.TERRACOTTA) == 8,
                    "все 8 блоков терракоты обязаны оказаться в инвентаре, в мире осталось "
                            + countBlock(helper, Blocks.TERRACOTTA, CAVE_SHIFT_INSIDE_ARENA));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(4, 3, 4)).is(Blocks.STONE),
                    "каменный колпак над стоянкой не трогается: ломается только цель");
            GameTestFixtures.finish(helper);
        });
    }

    // [флак] 1200 тиков HARVEST_NO_PROGRESS_TICKS: отказ обязан прийти не позже предела простоя
    @SceneStep(template = "arena", timeoutTicks = 1400)
    public static void gather_StoneBehindGlassWall_FailsWithNoPathReasonWithinTimeout(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.DIRT);
        for (int x = -2; x <= 10; x++) {
            for (int z = -2; z <= 10; z++) {
                for (int y = -3; y <= -1; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
            }
        }
        for (int x = 3; x <= 9; x++) {
            for (int z = 3; z <= 9; z++) {
                for (int y = 1; y <= 4; y++) {
                    helper.setBlock(x, y, z, Blocks.GLASS);
                }
            }
        }
        for (int x = 4; x <= 8; x++) {
            for (int z = 4; z <= 8; z++) {
                for (int y = 1; y <= 3; y++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        helper.setBlock(6, 1, 6, Blocks.STONE);
        SpiritEntity spirit = spawnWith(helper, new BlockPos(2, 1, 2), Items.IRON_PICKAXE);
        AtomicReference<HarvestSession> session = new AtomicReference<>();
        AtomicReference<Integer> startTick = new AtomicReference<>();
        helper.onEachTick(() -> {
            if (session.get() == null) {
                if (spirit.tickCount >= SETTLE_TICKS) {
                    session.set(HarvestSession.gather(spirit, "stone", 1, 4));
                    startTick.set(spirit.tickCount);
                }
                return;
            }
            if (session.get().outcome() == HarvestSession.Outcome.RUNNING) {
                session.get().tick();
            }
        });

        helper.succeedWhen(() -> {
            HarvestSession run = session.get();
            helper.assertTrue(run != null, "сессия обязана стартовать после оседания духа");
            helper.assertTrue(
                    run.outcome() != HarvestSession.Outcome.RUNNING,
                    "камень за преградой обязан завершиться отказом, а не висеть RUNNING: " + run.outcome() + ", тиков "
                            + (spirit.tickCount - startTick.get()));
            helper.assertTrue(
                    run.outcome() == HarvestSession.Outcome.FAILED,
                    "камень за преградой обязан завершиться отказом, а не успехом: " + run.outcome());
            helper.assertTrue(
                    spirit.tickCount - startTick.get() <= CatalogLimits.HARVEST_NO_PROGRESS_TICKS,
                    "отказ обязан прийти не позже предела простоя " + CatalogLimits.HARVEST_NO_PROGRESS_TICKS
                            + " тиков, заняло " + (spirit.tickCount - startTick.get()));
            BlockPos target = helper.absolutePos(new BlockPos(6, 1, 6));
            String expected = "нет пути к блоку (" + target.getX() + ", " + target.getY() + ", " + target.getZ() + ")";
            String reason = run.failureReason();
            helper.assertTrue(
                    reason != null && reason.contains(expected),
                    "причина отказа обязана назвать блок и «нет пути» (" + expected + "): " + reason);
            GameTestFixtures.finish(helper);
        });
    }

    // [флак] 18 камня деревянной киркой (твёрдость 1.5) и разлёт дропа: запас 2× к ~600 тикам
    @SceneStep(template = "arena", timeoutTicks = 1400)
    public static void gather_StoneHeapWithWoodenPickaxe_MinesAll(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 1; z <= 3; z++) {
                    helper.setBlock(x + CAVE_SHIFT_INSIDE_ARENA, y, z + CAVE_SHIFT_INSIDE_ARENA, Blocks.STONE);
                }
            }
        }
        SpiritEntity spirit = spawnWith(
                helper,
                SPIRIT_CORNER.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA),
                Items.WOODEN_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, gatherCall("stone", 18), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "куча камня под деревянной киркой обязана добыться целиком, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 18,
                    "в инвентаре обязано быть 18 булыжника, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }
}
