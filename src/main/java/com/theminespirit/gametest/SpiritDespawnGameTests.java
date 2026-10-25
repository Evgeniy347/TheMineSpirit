package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Дух не исчезает, когда игрок далеко (игра 21.10: духи дальних построек витрины city 5 пропадали, пока владелец стоял
 * на другом конце улицы): проверка самой игровой проверки исчезновения с игроком в 300 блоках.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpiritDespawnGameTests {
    private static final int FAR_AWAY = 300;
    private static final int CHECKS = 2_000;

    private SpiritDespawnGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 100)
    public static void checkDespawn_PlayerFarAway_SpiritStays(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        // Как /spirit spawn и демо (create + addFreshEntity): helper.spawn ставит мобу PersistenceRequired и прячет
        // исчезновение.
        SpiritEntity spirit = ModEntities.SPIRIT.get().create(helper.getLevel());
        helper.assertTrue(spirit != null, "тип духа обязан создавать сущность");
        BlockPos feet = helper.absolutePos(new BlockPos(2, 1, 2));
        spirit.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        helper.getLevel().addFreshEntity(spirit);
        helper.assertTrue(
                !spirit.isPersistenceRequired(),
                "дух из игры не помечен постоянным: проверка исчезновения иначе пуста");
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(2, 1, 2), "Далёкий владелец");
        player.moveTo(spirit.getX() + FAR_AWAY, spirit.getY(), spirit.getZ());
        for (int i = 0; i < CHECKS && !spirit.isRemoved(); i++) {
            spirit.checkDespawn();
        }
        boolean kept = !spirit.isRemoved();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(
                kept,
                "дух обязан оставаться в мире, когда ближний игрок в " + FAR_AWAY + " блоках: иначе стройки и дела "
                        + "дальних духов пропадают (игра 21.10, /spirit demo city 5)");
        spirit.discard();
        GameTestFixtures.finish(helper);
        helper.succeed();
    }
}
