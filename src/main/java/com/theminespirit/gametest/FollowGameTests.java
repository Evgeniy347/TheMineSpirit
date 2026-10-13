package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
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

    private static void buildFloor(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
    }
}
