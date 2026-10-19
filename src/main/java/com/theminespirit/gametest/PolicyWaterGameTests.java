package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PolicyWaterGameTests {
    private static final int LAKE_MIN = 3;
    private static final int LAKE_MAX = 5;
    private static final int POND_DEPTH = 7;
    private static final int AIR_AT_BOTTOM = 120;

    private PolicyWaterGameTests() {}

    private static void carveLake(GameTestHelper helper, int zMin, int zMax) {
        for (int x = LAKE_MIN; x <= LAKE_MAX; x++) {
            for (int z = zMin; z <= zMax; z++) {
                helper.setBlock(x, 0, z, Blocks.WATER);
            }
        }
    }

    private static ScriptCall gotoCall(GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        return new ScriptCall("goto", Map.of("x", absolute.getX(), "y", absolute.getY(), "z", absolute.getZ()));
    }

    @GameTest(template = "arena", batch = "policy_lake_bypass", timeoutTicks = 400)
    public static void lake_GotoWithDryBypass_ArrivesWithoutTouchingWater(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        carveLake(helper, 3, 5);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 4));
        boolean[] touched = {false};
        int[] pathNodesInLake = {-1};
        PolicyScene scene = PolicyScene.launch(
                helper,
                spirit,
                5,
                () -> {
                    Path path = spirit.getNavigation().createPath(helper.absolutePos(new BlockPos(8, 1, 4)), 0);
                    pathNodesInLake[0] = 0;
                    for (int i = 0; path != null && i < path.getNodeCount(); i++) {
                        BlockPos cell = BlockPos.containing(helper.relativeVec(
                                Vec3.atCenterOf(path.getNode(i).asBlockPos())));
                        if (cell.getX() >= LAKE_MIN
                                && cell.getX() <= LAKE_MAX
                                && cell.getZ() >= 3
                                && cell.getZ() <= 5) {
                            pathNodesInLake[0]++;
                        }
                    }
                    if (path == null) {
                        pathNodesInLake[0] = -2;
                    }
                },
                observed -> {
                    touched[0] |= observed.isInWater();
                    return null;
                });
        SpiritRun run = SpiritRun.launch(helper, spirit, gotoCall(helper, new BlockPos(8, 1, 4)), 1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            scene.assertStarted();
            helper.assertTrue(
                    pathNodesInLake[0] == 0,
                    "построенный путь обязан обходить озеро по суше (вода дорога): узлов в озере " + pathNodesInLake[0]
                            + ", -2 — пути нет");
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "обход озера по суше есть: дух обязан дойти, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(!touched[0], "вода имеет высокую цену, а обход дёшев: дух не должен заходить в воду");
            GameTestFixtures.finish(helper);
        });
    }

    // [флак] переправа через озеро вброд: goto до 480 тиков духа, запас под нагрузкой
    @GameTest(template = "arena", batch = "policy_lake_ford", timeoutTicks = 600)
    public static void lake_GotoWithoutBypass_CrossesTheWaterAndArrives(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        carveLake(helper, 0, 8);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 4));
        boolean[] touched = {false};
        PolicyScene scene = PolicyScene.launch(helper, spirit, 1, () -> {}, observed -> {
            touched[0] |= observed.isInWater();
            return null;
        });
        SpiritRun run = SpiritRun.launch(helper, spirit, gotoCall(helper, new BlockPos(8, 1, 4)), 1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            scene.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "обхода нет, вода — цена, а не запрет: дух обязан переправиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(touched[0], "переправа идёт через воду: дух обязан побывать в воде");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "policy_idle_in_water", timeoutTicks = 200)
    public static void lake_IdleSpiritAtTheEdge_ClimbsOutWithin60Ticks(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        carveLake(helper, 3, 5);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(LAKE_MIN, 0, 4));
        PolicyScene scene = PolicyScene.launch(helper, spirit, 1, () -> {});

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(
                    !spirit.isInWater(), "дух без задачи в воде обязан выбраться на берег, тик духа " + scene.ticks());
            helper.assertTrue(
                    scene.ticks() <= 60, "выход из воды обязан уложиться в 60 тиков духа, ушло " + scene.ticks());
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "policy_air_pond", timeoutTicks = 300)
    public static void pond_SpiritOnTheBottomWithLowAir_SurfacesBeforeDrowning(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) {
                for (int y = 0; y < POND_DEPTH; y++) {
                    boolean wall = x == 2 || x == 6 || z == 2 || z == 6;
                    helper.setBlock(x, y, z, wall ? Blocks.STONE : Blocks.WATER);
                }
            }
        }
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 0, 4));
        int[] lowestAir = {Integer.MAX_VALUE};
        PolicyScene scene =
                PolicyScene.launch(helper, spirit, 2, () -> spirit.setAirSupply(AIR_AT_BOTTOM), observed -> {
                    lowestAir[0] = Math.min(lowestAir[0], observed.getAirSupply());
                    return null;
                });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(
                    !spirit.isUnderWater(),
                    "дух на дне пруда глубиной " + POND_DEPTH + " обязан всплыть, тик духа " + scene.ticks());
            helper.assertTrue(lowestAir[0] > 0, "запас воздуха нигде не должен падать до 0, минимум " + lowestAir[0]);
            helper.assertTrue(
                    scene.minHealth() >= spirit.getMaxHealth(),
                    "утопления нет: здоровье не должно падать, минимум " + scene.minHealth());
            GameTestFixtures.finish(helper);
        });
    }
}
