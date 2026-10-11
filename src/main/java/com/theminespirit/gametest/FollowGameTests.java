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

    private FollowGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void followScript_StationaryMockPlayer_HoldsRadiusWithFewRebuilds(GameTestHelper helper) {
        buildFloor(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 1));
        ServerPlayer player = spawnMockPlayer(helper, new BlockPos(PLAYER_X, 1, 1));
        helper.runAfterDelay(20, () -> spirit.startTask(new ScriptCall("follow",
                Map.of("target", player.getName().getString(), "radius", 3))));

        helper.runAfterDelay(120, () -> {
            helper.assertTrue(spirit.pathBuilds() <= 2,
                    "follow за неподвижной целью обязан построить путь не более двух раз за 100 тиков, а построил "
                            + spirit.pathBuilds() + ": перебор ищет путь каждый тик и кладёт серверный тик");
            double distance = spirit.position().distanceTo(player.position());
            helper.assertTrue(distance <= 3.0,
                    "догнав игрока, дух обязан оказаться в радиусе удержания: расстояние " + distance
                            + " больше радиуса 3. Иначе follow не выполняет свою задачу");
            removePlayer(helper, player);
            helper.succeed();
        });
    }

    // NeoForge 21.1 не даёт иного способа получить ServerPlayer, попавшего в players() уровня; метод помечен
    // deprecated, но в этой версии работает и удаления не произошло.
    @SuppressWarnings("removal")
    private static ServerPlayer spawnMockPlayer(GameTestHelper helper, BlockPos relative) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos absolute = helper.absolutePos(relative);
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
        return player;
    }

    private static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static void buildFloor(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
    }
}