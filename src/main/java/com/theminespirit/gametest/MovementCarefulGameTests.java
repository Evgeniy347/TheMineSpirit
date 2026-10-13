package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.movement.DropProfile;
import com.theminespirit.neoforge.CarefulPath;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementCarefulGameTests {
    private static final BlockPos START = new BlockPos(1, 1, 4);
    private static final int PIT_X = 6;
    private static final int PIT_Z = 4;
    private static final int EDGE_Z = 4;
    private static final int CHASM_FIRST_Z = 5;
    private static final int CHASM_LAST_Z = 8;
    private static final int CHASM_DEPTH = 4;
    private static final int PIT_DEPTH_OK = 2;
    private static final int PIT_DEPTH_TOO_DEEP = 3;
    private static final int REFUSAL_LIMIT_TICKS = 5;
    private static final int ARRIVE_LIMIT_TICKS = 250;
    private static final double STANDS_STILL = 0.1;
    private static final int FLOOR_UNDER_FEET_CHECK = 2;

    private MovementCarefulGameTests() {
    }

    @GameTest(template = "arena", batch = "move_careful_pit2", timeoutTicks = 300)
    public static void walkCareful_TargetAtBottomOfTwoDeepPit_StepsDownAndSucceeds(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SavedBlocks saved = digPit(helper, PIT_DEPTH_OK);
        BlockPos bottom = new BlockPos(PIT_X, 1 - PIT_DEPTH_OK, PIT_Z);
        SpiritEntity spirit = MovementScene.spirit(helper, START);
        SpiritRun run = SpiritRun.launch(helper, spirit, MovementScene.pointCall(helper, "walk_careful", bottom),
                MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "спуск на " + PIT_DEPTH_OK + " блока не выше MAX_DROP: walk_careful обязан дойти, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            double distance = MovementScene.distanceTo(helper, spirit, bottom);
            helper.assertTrue(distance <= MovementScene.ARRIVAL,
                    "дух обязан стоять на дне ямы в пределах " + MovementScene.ARRIVAL + ", расстояние " + distance);
            helper.assertTrue(run.spiritTicks() <= ARRIVE_LIMIT_TICKS,
                    "яма в 5 блоках обязана пройтись за " + ARRIVE_LIMIT_TICKS + " тиков духа, ушло "
                            + run.spiritTicks());
            saved.restore();
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "move_careful_pit3", timeoutTicks = 300)
    public static void walkCareful_TargetAtBottomOfThreeDeepPit_RefusesAtOnceAndNeverDescends(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SavedBlocks saved = digPit(helper, PIT_DEPTH_TOO_DEEP);
        BlockPos bottom = new BlockPos(PIT_X, 1 - PIT_DEPTH_TOO_DEEP, PIT_Z);
        SpiritEntity spirit = MovementScene.spirit(helper, START);
        SpiritRun run = SpiritRun.launch(helper, spirit, MovementScene.pointCall(helper, "walk_careful", bottom),
                MovementScene.SETTLE_TICKS);
        double[] startY = {Double.NaN};
        double[] lowestY = {Double.MAX_VALUE};
        helper.onEachTick(() -> {
            if (spirit.tickCount >= MovementScene.SETTLE_TICKS) {
                if (Double.isNaN(startY[0])) {
                    startY[0] = spirit.getY();
                }
                lowestY[0] = Math.min(lowestY[0], spirit.getY());
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "обрыв в " + PIT_DEPTH_TOO_DEEP + " блока глубже MAX_DROP: walk_careful обязан отказать, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("недостижимо осторожным шагом"),
                    "причина обязана быть отказом осторожного шага, а не тайм-аутом или застреванием: " + reason);
            helper.assertTrue(run.spiritTicks() <= REFUSAL_LIMIT_TICKS,
                    "отказ идёт при первом построении пути, за " + REFUSAL_LIMIT_TICKS + " тиков, а прошло "
                            + run.spiritTicks());
            helper.assertTrue(lowestY[0] >= startY[0] - DropProfile.MAX_SAFE_DROP,
                    "дух не падал ниже " + DropProfile.MAX_SAFE_DROP + " блоков от старта " + startY[0]
                            + ", самая низкая точка " + lowestY[0]);
            helper.assertTrue(lowestY[0] >= startY[0] - STANDS_STILL,
                    "отказавший дух стоит на месте и вообще не спускается: старт " + startY[0] + ", ниже всего "
                            + lowestY[0]);
            saved.restore();
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "move_careful_edge", timeoutTicks = 300)
    public static void walkCareful_SixBlocksAlongFourDeepChasm_ArrivesAndAlwaysHasFloorBelow(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SavedBlocks saved = digChasm(helper);
        BlockPos goal = new BlockPos(7, 1, EDGE_Z);
        SpiritEntity spirit = MovementScene.spirit(helper, new BlockPos(1, 1, EDGE_Z));
        SpiritRun run = SpiritRun.launch(helper, spirit, MovementScene.pointCall(helper, "walk_careful", goal),
                MovementScene.SETTLE_TICKS);
        helper.onEachTick(() -> {
            if (spirit.tickCount < MovementScene.SETTLE_TICKS) {
                return;
            }
            BlockPos feet = helper.relativePos(spirit.blockPosition());
            boolean floorBelow = false;
            for (int depth = 1; depth <= FLOOR_UNDER_FEET_CHECK; depth++) {
                floorBelow |= !helper.getBlockState(feet.below(depth)).isAir();
            }
            if (!floorBelow) {
                helper.fail("дух стоял в клетке " + feet + " без пола в пределах " + FLOOR_UNDER_FEET_CHECK
                        + " блоков под ногами: он сорвался в пропасть вдоль края");
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "цель вдоль края достижима по ровному полу: walk_careful обязан дойти, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            double distance = MovementScene.distanceTo(helper, spirit, goal);
            helper.assertTrue(distance <= MovementScene.ARRIVAL,
                    "дух обязан дойти до цели в пределах " + MovementScene.ARRIVAL + ", расстояние " + distance);
            saved.restore();
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "empty", batch = "move_careful_path")
    public static void carefulPath_SyntheticPaths_RefusesOnlyDeepDropsUnreachableAndMissing(GameTestHelper helper) {
        helper.assertTrue(DropProfile.LOOKAHEAD_CELLS == 8,
                "заглядывание вперёд — 8 клеток (решение главного агента к чеклисту 24), в коде "
                        + DropProfile.LOOKAHEAD_CELLS);
        helper.assertTrue(DropProfile.MAX_SAFE_DROP == 2,
                "безопасный спуск — 2 блока (MAX_DROP чеклиста 20), в коде " + DropProfile.MAX_SAFE_DROP);
        helper.assertTrue(CarefulPath.refusal(path(true, 5, 5, 5, 5)) == null, "ровный путь осторожный шаг пропускает");
        helper.assertTrue(CarefulPath.refusal(path(true, 5, 5, 3, 3)) == null,
                "спуск на 2 блока (MAX_DROP) осторожный шаг пропускает");
        String drop = CarefulPath.refusal(path(true, 5, 5, 2, 2));
        helper.assertTrue(drop != null && drop.contains("обрыв"),
                "спуск на 3 блока обязан отказать с причиной про обрыв, получено: " + drop);
        helper.assertTrue(CarefulPath.refusal(path(false, 5, 5, 5, 5)) != null,
                "путь, не доходящий до цели, осторожный шаг отвергает: цель за преградой или обрывом");
        helper.assertTrue(CarefulPath.refusal(null) != null, "отсутствие пути осторожный шаг отвергает");
        int[] farDrop = new int[DropProfile.LOOKAHEAD_CELLS + 3];
        Arrays.fill(farDrop, 9);
        farDrop[farDrop.length - 1] = 4;
        helper.assertTrue(CarefulPath.refusal(path(true, farDrop)) == null,
                "обрыв дальше " + DropProfile.LOOKAHEAD_CELLS + " клеток впереди осторожный шаг пока не проверяет: "
                        + "он увидит его при следующем перестроении пути");
        int[] nearDrop = new int[DropProfile.LOOKAHEAD_CELLS + 1];
        Arrays.fill(nearDrop, 9);
        nearDrop[nearDrop.length - 1] = 4;
        helper.assertTrue(CarefulPath.refusal(path(true, nearDrop)) != null,
                "обрыв ровно на границе заглядывания (клетка " + DropProfile.LOOKAHEAD_CELLS + ") обязан отказать");
        helper.succeed();
    }

    private static Path path(boolean reached, int... heights) {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < heights.length; i++) {
            nodes.add(new Node(i, heights[i], 0));
        }
        return new Path(nodes, new BlockPos(heights.length - 1, heights[heights.length - 1], 0), reached);
    }

    private static SavedBlocks digPit(GameTestHelper helper, int depth) {
        SavedBlocks saved = new SavedBlocks(helper);
        for (int y = 0; y > -depth; y--) {
            saved.set(new BlockPos(PIT_X, y, PIT_Z), Blocks.AIR);
        }
        saved.set(new BlockPos(PIT_X, -depth, PIT_Z), Blocks.STONE);
        return saved;
    }

    private static SavedBlocks digChasm(GameTestHelper helper) {
        SavedBlocks saved = new SavedBlocks(helper);
        for (int x = 0; x < GameTestFixtures.FLOOR_SIZE; x++) {
            for (int z = CHASM_FIRST_Z; z <= CHASM_LAST_Z; z++) {
                for (int y = 0; y > -CHASM_DEPTH; y--) {
                    saved.set(new BlockPos(x, y, z), Blocks.AIR);
                }
                saved.set(new BlockPos(x, -CHASM_DEPTH, z), Blocks.STONE);
            }
        }
        return saved;
    }
}
