package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Правило владельца 21.10: пока на сервере есть игрок (в любом мире), чанки поселения тикают,
 * а без игроков выгружаются.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SettlementChunkGameTests {
    private static final int HOME_CHUNKS_AWAY = 16;

    private SettlementChunkGameTests() {}

    @GameTest(template = "arena", batch = "settlement_chunks", timeoutTicks = 300)
    public static void settlementChunks_PlayerJoinsAndLeaves_TogglesSettlementChunks(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        ServerLevel level = helper.getLevel();
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 2, 1));
        spirit.setNoGravity(true);
        BlockPos arena = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos home = arena.offset(HOME_CHUNKS_AWAY * 16, 0, 0);
        spirit.setHome(new BlockCoord(home.getX(), home.getY(), home.getZ()));
        long homeChunk = ChunkPos.asLong(Math.floorDiv(home.getX(), 16), Math.floorDiv(home.getZ(), 16));

        helper.runAfterDelay(3, () -> {
            helper.assertFalse(
                    level.shouldTickBlocksAt(homeChunk),
                    "Без игроков на сервере чанк поселения не должен тикать: иначе поселение тратит ресурсы "
                            + "при пустом сервере");
            ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(1, 2, 1), "Хозяин поселения");
            helper.startSequence()
                    .thenWaitUntil(() -> helper.assertTrue(
                            level.shouldTickBlocksAt(homeChunk),
                            "С первым игроком (в любом мире) чанк дома поселения обязан тикать, даже если "
                                    + "духов рядом нет: тикеты поселения ставятся при входе"))
                    .thenExecute(() -> level.getServer().getPlayerList().remove(player))
                    .thenWaitUntil(() -> helper.assertFalse(
                            level.shouldTickBlocksAt(homeChunk),
                            "После выхода последнего игрока чанк поселения обязан перестать тикать: тикеты "
                                    + "снимаются при выходе последнего игрока"))
                    .thenExecute(() -> {
                        spirit.discard();
                        GameTestFixtures.finish(helper);
                    })
                    .thenSucceed();
        });
    }
}
