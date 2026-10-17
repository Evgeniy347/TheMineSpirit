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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import java.util.List;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FollowGameTests {
    private static final int PLAYER_X = 4;
    private static final int SETTLE_TICKS = 20;
    private static final int WINDOW_TICKS = 100;
    private static final String PLAYER_NAME = "gt_follow_hold_player";

    private FollowGameTests() {
    }

    @GameTest(template = "arena", batch = "follow_hold", timeoutTicks = 300)
    public static void followScript_StationaryMockPlayer_HoldsRadiusWithFewRebuilds(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildFloor(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 1));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(PLAYER_X, 1, 1), PLAYER_NAME);
        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("follow", Map.of("target", PLAYER_NAME, "radius", 3)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(run.spiritTicks() >= WINDOW_TICKS,
                    "окно наблюдения за сопровождением ещё не прошло: тиков духа " + run.spiritTicks());
            helper.assertTrue(spirit.pathBuilds() <= 2,
                    "follow за неподвижной целью обязан построить путь не более двух раз за " + WINDOW_TICKS
                            + " тиков духа, а построил " + spirit.pathBuilds()
                            + ": перебор ищет путь каждый тик и кладёт серверный тик. Если целью стал чужой игрок"
                            + " с тем же именем или цель вне загруженных чанков, путь не строится и счёт растёт"
                            + " по одному на каждые 21 тик");
            double distance = spirit.position().distanceTo(player.position());
            helper.assertTrue(distance <= 3.0,
                    "догнав игрока, дух обязан оказаться в радиусе удержания: расстояние " + distance
                            + " больше радиуса 3. Иначе follow не выполняет свою задачу");
        });
    }

    @GameTest(template = "arena", batch = "follow_drop", timeoutTicks = 400)
    public static void followScript_FullInventoryNearPlayer_DropsCheapestAndPicksDiamond(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        buildFloor(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 1));
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        GameTestFixtures.mockPlayer(helper, new BlockPos(PLAYER_X, 1, 1), PLAYER_NAME);
        GameTestFixtures.dropAt(helper, Items.DIAMOND, 1, 1.5, 1, 1.5);
        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("follow", Map.of("target", PLAYER_NAME, "radius", 3)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.DIAMOND) == 1,
                    "дух у игрока обязан подобрать алмаз, выбросив дешёвый булыжник: алмазов в инвентаре "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.DIAMOND));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE)
                            == 35 * 64,
                    "в инвентаре обязано остаться 35 стопок булыжника: выброшена ровно одна, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            List<ItemEntity> dropped = GameTestFixtures.dropsInArea(helper, Items.COBBLESTONE);
            helper.assertTrue(dropped.size() == 1 && dropped.get(0).getItem().getCount() == 64,
                    "выброшенная стопка булыжника обязана лежать в мире целой: иначе дух потерял бы "
                            + "предметы, а не обменял дешёвое на ценное");
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.RUNNING,
                    "сопровождение обязано продолжаться после обмена, статус "
                            + spirit.taskStatus().orElse(null));
            GameTestFixtures.finish(helper);
        });
    }

    private static void buildFloor(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
    }
}
