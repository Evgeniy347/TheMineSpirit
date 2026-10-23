package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.MineOreLedger;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
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
        BlockPos origin = helper.absolutePos(new BlockPos(FIRST_X, TOP, LANE_Z));
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
}
