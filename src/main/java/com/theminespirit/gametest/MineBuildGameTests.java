package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.MineOreLedger;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Строитель шахты по схеме владельца mine_descent_v1: спуск 1×3, шаг вперёд — на блок ниже. Порода — камень до y=12,
// вход — над поверхностью в x=1; спуск идёт по +x до пола y=4 (9 ступеней, x=1..9).
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MineBuildGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final int TOP = 12;
    private static final int BOTTOM = 4;
    private static final int LANE_Z = 5;
    private static final int FIRST_X = 1;
    private static final int STEPS = TOP - BOTTOM + 1;
    private static final double TELEPORT_JUMP = 2.0;
    private static final double RETURN_ARRIVAL = 1.5;

    private MineBuildGameTests() {}

    private static void rock(GameTestHelper helper) {
        GameTestFixtures.reset(helper, 16);
        for (int x = 0; x <= 10; x++) {
            for (int y = 1; y <= TOP; y++) {
                for (int z = 2; z <= 8; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
    }

    private static SpiritEntity miner(GameTestHelper helper) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(FIRST_X, TOP + 1, LANE_Z));
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        spirit.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 16));
        return spirit;
    }

    private static ScriptCall descent(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(FIRST_X, TOP, LANE_Z - 1));
        return new ScriptCall(
                "build_schematic",
                Map.of(
                        "file", "mine_descent_v1",
                        "x", origin.getX(),
                        "y", origin.getY(),
                        "z", origin.getZ(),
                        "segments", STEPS));
    }

    private static BlockPos step(int k, int dy) {
        return new BlockPos(FIRST_X + k, TOP - k + dy, LANE_Z);
    }

    private static void assertPassageOpen(GameTestHelper helper) {
        for (int k = 0; k < STEPS; k++) {
            for (int dy = 1; dy <= 3; dy++) {
                BlockState state = helper.getBlockState(step(k, dy));
                helper.assertTrue(
                        state.isAir() && state.getFluidState().isEmpty(),
                        "клетка спуска " + step(k, dy) + " обязана быть сухим воздухом: по ней дух ходит вверх и "
                                + "вниз, фактически " + state);
            }
            helper.assertTrue(
                    !helper.getBlockState(step(k, 0)).isAir(),
                    "пол ступени " + step(k, 0) + " обязан быть твёрдым: без него дух провалится");
        }
    }

    private static Map<String, Object> data(GameTestHelper helper, SpiritEntity spirit) {
        helper.assertTrue(
                spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                "проходка спуска обязана завершиться успехом, статус "
                        + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                        + spirit.taskFailureReason().orElse("нет"));
        return ((ScriptResult.Data) spirit.taskResult().orElseThrow()).fields();
    }

    // [флак] 27 блоков камня железной киркой, 9 ступеней вниз и выход назад пешком; замерено ~700 тиков, запас 2×
    @GameTest(template = "mine_arena", batch = "mine_build_descent", timeoutTicks = 1600)
    public static void mineDescent_RockToDepth_DigsStepsAndWalksBackUp(GameTestHelper helper) {
        rock(helper);
        SpiritEntity spirit = miner(helper);
        AtomicBoolean teleported = new AtomicBoolean();
        AtomicReference<Vec3> last = new AtomicReference<>(spirit.position());
        helper.onEachTick(() -> {
            if (spirit.position().distanceTo(last.get()) > TELEPORT_JUMP) {
                teleported.set(true);
            }
            last.set(spirit.position());
        });
        SpiritRun run = SpiritRun.launch(helper, spirit, descent(helper), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            Map<String, Object> data = data(helper, spirit);
            helper.assertTrue(
                    Integer.valueOf(STEPS).equals(data.get("columns")),
                    "спуск до пола y=" + BOTTOM + " — " + STEPS + " ступеней по схеме, итог " + data);
            assertPassageOpen(helper);
            BlockPos feet = spirit.blockPosition();
            helper.assertTrue(
                    feet.equals(helper.absolutePos(step(0, 1))),
                    "после проходки дух обязан выйти наверх ко входу " + helper.absolutePos(step(0, 1))
                            + " (INC-14: духи оставались под землёй), стоит в " + feet);
            helper.assertTrue(
                    !teleported.get(),
                    "выход наверх — пешком по ступеням: скачок позиции больше " + TELEPORT_JUMP
                            + " блока значит телепорт, а не проходимый спуск");
            GameTestFixtures.finish(helper, 16);
        });
    }

    // [флак] спуск 9 ступеней, добыча камня поселением ×12 и возврат домой наверх; запас 2× к ~900 тиков
    @GameTest(template = "mine_arena", batch = "mine_gather_exit", timeoutTicks = 2400)
    public static void mineGather_SettledStone_SpiritWalksBackHome(GameTestHelper helper) {
        rock(helper);
        SpiritEntity spirit = miner(helper);
        BlockPos home = helper.absolutePos(step(0, 1));
        spirit.setHome(new BlockCoord(home.getX(), home.getY(), home.getZ()));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new SequenceNode(List.of(
                        descent(helper),
                        new ScriptCall("gather", Map.of("block", "stone", "count", 12, "radius", 32)),
                        new ScriptCall("return_home", Map.of()))),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "спуск, добыча камня и возврат обязаны пройти, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет") + ", дух в " + spirit.blockPosition());
            helper.assertTrue(
                    spirit.blockPosition().distManhattan(home) <= 2,
                    "после добычи камня в шахте дух обязан выйти домой " + home + " (INC-43: духи оставались под "
                            + "землёй), стоит в " + spirit.blockPosition());
            GameTestFixtures.finish(helper, 16);
        });
    }

    // [флак] спуск 9 ступеней, затем возврат тактики (Approach) со дна к якорю наверху; запас 2× к ~900 тиков
    @GameTest(template = "mine_arena", batch = "mine_approach_exit", timeoutTicks = 2000)
    public static void mineApproach_FromBottom_ReachesAnchorOnTop(GameTestHelper helper) {
        rock(helper);
        SpiritEntity spirit = miner(helper);
        SpiritRun run = SpiritRun.launch(helper, spirit, descent(helper), SETTLE_TICKS);
        BlockPos top = helper.absolutePos(step(0, 1));
        BlockPos bottom = helper.absolutePos(step(STEPS - 1, 1));
        Approach approach = new Approach(spirit);
        AtomicReference<String> state = new AtomicReference<>("-");
        helper.onEachTick(() -> {
            if (spirit.taskStatus().orElse(null) != RunStatus.SUCCEEDED || "ARRIVED".equals(state.get())) {
                return;
            }
            if ("-".equals(state.get())) {
                // Дух у дна спуска, как живые духи в шахте (INC-43): оттуда — к якорю наверху.
                spirit.teleportTo(bottom.getX() + 0.5, bottom.getY(), bottom.getZ() + 0.5);
                approach.begin(new Vec3(top.getX() + 0.5, top.getY(), top.getZ() + 0.5), RETURN_ARRIVAL);
                state.set("MOVING");
                return;
            }
            Approach.State now = approach.tick();
            if (now != Approach.State.MOVING) {
                state.set(now.name() + (approach.refusal() == null ? "" : ": " + approach.refusal()));
                if (now != Approach.State.ARRIVED) {
                    approach.begin(new Vec3(top.getX() + 0.5, top.getY(), top.getZ() + 0.5), RETURN_ARRIVAL);
                }
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    state.get().startsWith("ARRIVED"),
                    "со дна спуска дух обязан дойти до якоря " + top + " подходом тактики (INC-43), итог подхода "
                            + state.get() + ", стоит в " + spirit.blockPosition());
            GameTestFixtures.finish(helper, 16);
        });
    }

    // [флак] спуск с обходом руды: 9 ступеней + 3 колонки обхода и выход наверх; замерено ~850 тиков, запас 2×
    @GameTest(template = "mine_arena", batch = "mine_build_luck_ore", timeoutTicks = 1800)
    public static void mineDescent_DiamondWithoutFortune_LeavesOreDetoursAndRemembers(GameTestHelper helper) {
        rock(helper);
        BlockPos ore = step(3, 2);
        helper.setBlock(ore, Blocks.DIAMOND_ORE);
        SpiritEntity spirit = miner(helper);
        SpiritRun run = SpiritRun.launch(helper, spirit, descent(helper), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            Map<String, Object> data = data(helper, spirit);
            helper.assertTrue(
                    helper.getBlockState(ore).is(Blocks.DIAMOND_ORE),
                    "алмаз без кирки с Удачей и Шёлковым касанием обязан остаться в стене (решение владельца 22.10)");
            BlockPos absolute = helper.absolutePos(ore);
            helper.assertTrue(
                    MineOreLedger.get(helper.getLevel())
                            .ores()
                            .containsKey(new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ())),
                    "оставленный алмаз обязан быть в памяти поселения — к нему вернутся с киркой с Удачей");
            helper.assertTrue(
                    Integer.valueOf(1).equals(data.get("detours"))
                            && Integer.valueOf(1).equals(data.get("ores_left")),
                    "обход один, оставленная руда одна; итог " + data);
            helper.assertTrue(
                    spirit.blockPosition().equals(helper.absolutePos(step(0, 1))),
                    "дух обходит руду и выходит наверх, а не застревает у неё; стоит в " + spirit.blockPosition());
            BlockPos below = step(STEPS - 1, 1);
            helper.assertTrue(
                    helper.getBlockState(below).isAir(),
                    "за рудой спуск продолжается до дна " + below + ": обход соединил ход");
            GameTestFixtures.finish(helper, 16);
        });
    }

    // [флак] спуск через пещеру: пол и стенки над пустотой, 9 ступеней и выход; замерено ~650 тиков, запас 2×
    @GameTest(template = "mine_arena", batch = "mine_build_cave", timeoutTicks = 1600)
    public static void mineDescent_CaveUnderSteps_PlacesFloorAndWallsInAir(GameTestHelper helper) {
        rock(helper);
        for (int x = FIRST_X + 4; x <= FIRST_X + 6; x++) {
            for (int y = 2; y <= TOP - 3; y++) {
                for (int z = 2; z <= 8; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        SpiritEntity spirit = miner(helper);
        spirit.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 64));
        SpiritRun run = SpiritRun.launch(helper, spirit, descent(helper), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            data(helper, spirit);
            assertPassageOpen(helper);
            for (int k = 0; k < STEPS; k++) {
                for (int dy = 1; dy <= 3; dy++) {
                    for (int side : new int[] {-1, 1}) {
                        BlockPos wall = step(k, dy).offset(0, 0, side);
                        helper.assertTrue(
                                !helper.getBlockState(wall).isAir(),
                                "стенка спуска " + wall + " обязана быть твёрдой и над пещерой (решение владельца "
                                        + "23.10): иначе дух и игрок падают в пропасть, скелеты стреляют в проход");
                    }
                }
            }
            GameTestFixtures.finish(helper, 16);
        });
    }

    // [флак] спуск с водой в стене и потолке: пробки и 9 ступеней с выходом; замерено ~750 тиков, запас 2×
    @GameTest(template = "mine_arena", batch = "mine_build_water", timeoutTicks = 1600)
    public static void mineDescent_WaterBehindWall_PlugsAndSpiritNeverUnderWater(GameTestHelper helper) {
        rock(helper);
        BlockPos wallWater = step(3, 1).south();
        BlockPos ceilingWater = step(5, 4);
        helper.setBlock(wallWater, Blocks.WATER);
        helper.setBlock(ceilingWater, Blocks.WATER);
        SpiritEntity spirit = miner(helper);
        AtomicBoolean wet = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (spirit.isUnderWater() || spirit.isInWater()) {
                wet.set(true);
            }
        });
        SpiritRun run = SpiritRun.launch(helper, spirit, descent(helper), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            Map<String, Object> data = data(helper, spirit);
            assertPassageOpen(helper);
            helper.assertTrue(
                    helper.getBlockState(wallWater).is(Blocks.COBBLESTONE)
                            && helper.getBlockState(ceilingWater).is(Blocks.COBBLESTONE),
                    "источники воды у прохода обязаны быть перекрыты булыжником до вскрытия (INC-19), стена "
                            + helper.getBlockState(wallWater) + ", потолок " + helper.getBlockState(ceilingWater));
            helper.assertTrue(
                    !wet.get(),
                    "дух ни разу не оказался в воде: проход не затапливается, копать в воду нельзя; итог " + data);
            GameTestFixtures.finish(helper, 16);
        });
    }

    @GameTest(template = "arena", batch = "mine_build_surface_stone", timeoutTicks = 200)
    public static void gather_SettledSpiritStoneUnderSky_RefusesSurfaceStone(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        BlockPos home = helper.absolutePos(new BlockPos(4, 1, 4));
        spirit.setHome(new BlockCoord(home.getX(), home.getY(), home.getZ()));
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("gather", Map.of("block", "minecraft:stone", "count", 1, "radius", 4)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED
                            && spirit.taskFailureReason().orElse("").contains("только в шахте"),
                    "дух поселения не копает камень под открытым небом — булыжник только из шахты (владелец 23.10); "
                            + "статус " + spirit.taskStatus() + ", причина " + spirit.taskFailureReason());
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(4, 0, 4)).is(Blocks.STONE),
                    "пол площадки цел: яма у дома ради булыжника запрещена");
            GameTestFixtures.finish(helper);
        });
    }
}
