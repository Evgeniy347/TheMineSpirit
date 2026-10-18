package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaterCrossGameTests {
    private static final int DIRT_STOCK = 16;
    private static final int CHANNEL_MIN_X = 2;
    private static final int CHANNEL_MAX_X = 6;
    private static final int PIT_MIN_X = 3;
    private static final int PIT_MAX_X = 6;
    private static final BlockPos START = new BlockPos(1, 1, 4);
    private static final BlockPos FAR_SHORE = new BlockPos(8, 1, 4);
    private static final BlockPos SWIM_START = new BlockPos(3, 0, 4);
    private static final BlockPos SWIM_BANK = new BlockPos(7, 3, 4);
    private static final double ARRIVAL = 1.5;

    private WaterCrossGameTests() {
    }

    private static void carveChannel(GameTestHelper helper, int minX, int maxX, int topY, boolean water) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = 0; z <= 8; z++) {
                for (int y = 0; y <= topY; y++) {
                    helper.setBlock(x, y, z, water ? Blocks.WATER : Blocks.AIR);
                }
            }
        }
    }

    private static void stock(SpiritEntity spirit, int count) {
        spirit.getInventory().addItem(new ItemStack(Items.DIRT, count));
    }

    private static void assertCrossed(GameTestHelper helper, SpiritEntity spirit) {
        helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                "дух обязан переправиться к дальней стороне, статус "
                        + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                        + spirit.taskFailureReason().orElse("нет"));
        double distance = MovementScene.distanceTo(helper, spirit, FAR_SHORE);
        helper.assertTrue(distance <= ARRIVAL,
                "дух обязан дойти до (8, 1, 4) на дальней стороне, а он в " + distance + " блоках от неё");
    }

    // [флак] мост из пяти блоков плюс переход: под нагрузкой полного прогона подход и кладка тянутся
    @GameTest(template = "arena", batch = "water_cross_bridge", timeoutTicks = 600)
    public static void crossWater_FiveBlockChannelWithDirt_BridgesAcross(GameTestHelper helper) {
        MovementScene.prepare(helper);
        carveChannel(helper, CHANNEL_MIN_X, CHANNEL_MAX_X, 0, true);
        SpiritEntity spirit = MovementScene.spirit(helper, START);
        stock(spirit, DIRT_STOCK);
        SpiritRun run = SpiritRun.launch(helper, spirit,
                MovementScene.call("cross_water", MovementScene.pointParams(helper, FAR_SHORE)),
                MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertCrossed(helper, spirit);
            for (int x = CHANNEL_MIN_X; x <= CHANNEL_MAX_X; x++) {
                helper.assertTrue(helper.getBlockState(new BlockPos(x, 0, 4)).is(Blocks.DIRT),
                        "шириной 5 блоков вода не переплывается: cross_water обязан настелить мост, а клетка ("
                                + x + ", 0, 4) не из досок духа");
            }
            MovementScene.finish(helper);
        });
    }

    // [флак] четыре клетки моста и переход: запас под нагрузкой соседних сцен полного прогона
    @GameTest(template = "arena", batch = "water_bridge_pit", timeoutTicks = 600)
    public static void bridge_FourBlockPit_PlacesBlocksAndCrosses(GameTestHelper helper) {
        MovementScene.prepare(helper);
        carveChannel(helper, PIT_MIN_X, PIT_MAX_X, 0, false);
        SpiritEntity spirit = MovementScene.spirit(helper, START);
        stock(spirit, DIRT_STOCK);
        Map<String, Object> params = new HashMap<>(MovementScene.pointParams(helper, FAR_SHORE));
        params.put("length", PIT_MAX_X - PIT_MIN_X + 1);
        SpiritRun run = SpiritRun.launch(helper, spirit, MovementScene.call("bridge", params),
                MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertCrossed(helper, spirit);
            for (int x = PIT_MIN_X; x <= PIT_MAX_X; x++) {
                helper.assertTrue(helper.getBlockState(new BlockPos(x, 0, 4)).is(Blocks.DIRT),
                        "мост через четырёхблочный провал обязан лечь ровно из блоков инвентаря, а клетка ("
                                + x + ", 0, 4) пуста");
            }
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.DIRT)
                            == DIRT_STOCK - (PIT_MAX_X - PIT_MIN_X + 1),
                    "мост тратит ровно длину из инвентаря: остаток "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.DIRT)
                            + " вместо " + (DIRT_STOCK - (PIT_MAX_X - PIT_MIN_X + 1)));
            MovementScene.finish(helper);
        });
    }

    // [флак] заплыв через глубокий пруд: всплытия и подход за плавание под нагрузкой дольше
    @GameTest(template = "arena", batch = "water_swim", timeoutTicks = 800)
    public static void swim_AcrossDeepTank_SurfacesAndDoesNotDrown(GameTestHelper helper) {
        MovementScene.prepare(helper);
        buildTank(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, SWIM_START);
        int[] lowestAir = {Integer.MAX_VALUE};
        float[] lowestHealth = {Float.MAX_VALUE};
        helper.onEachTick(() -> {
            lowestAir[0] = Math.min(lowestAir[0], spirit.getAirSupply());
            lowestHealth[0] = Math.min(lowestHealth[0], spirit.getHealth());
        });
        SpiritRun run = SpiritRun.launch(helper, spirit,
                MovementScene.call("swim", MovementScene.pointParams(helper, SWIM_BANK)),
                MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "дух в глубоком пруду обязан доплыть до цели, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(!spirit.isUnderWater(),
                    "у цели у поверхности дух не должен оставаться под водой: плыть и выныривать — условие задачи");
            helper.assertTrue(lowestAir[0] > 0,
                    "запас воздуха нигде не должен падать до 0, минимум " + lowestAir[0]);
            helper.assertTrue(lowestHealth[0] >= spirit.getMaxHealth(),
                    "утопления нет: здоровье не должно падать, минимум " + lowestHealth[0]);
            MovementScene.finish(helper);
        });
    }

    private static void buildTank(GameTestHelper helper) {
        for (int x = 3; x <= 6; x++) {
            for (int z = 3; z <= 5; z++) {
                for (int y = 0; y <= 2; y++) {
                    helper.setBlock(x, y, z, Blocks.WATER);
                }
            }
        }
        for (int z = 3; z <= 5; z++) {
            for (int y = 0; y <= 2; y++) {
                helper.setBlock(7, y, z, Blocks.STONE);
            }
        }
        for (int y = 0; y <= 3; y++) {
            for (int z = 3; z <= 5; z++) {
                helper.setBlock(2, y, z, Blocks.STONE);
            }
            for (int x = 2; x <= 7; x++) {
                helper.setBlock(x, y, 2, Blocks.STONE);
                helper.setBlock(x, y, 6, Blocks.STONE);
            }
        }
    }
}
