package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.chunk.ChunkCoord;
import com.theminespirit.core.chunk.ChunkSquare;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChunkLoadingGameTests {
    private ChunkLoadingGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void chunkLoading_SpiritSpawnedMovedAndDiscarded_KeepsTickingSquare(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int radius = SpiritConfig.radiusFor(level);
        SpiritEntity spirit = spawnFloating(helper);

        helper.runAfterDelay(3, () -> {
            ChunkPos center = spirit.chunkPosition();
            Set<ChunkCoord> expected = ChunkSquare.chunks(new ChunkCoord(center.x, center.z), radius);
            helper.assertTrue(spirit.trackedChunks().equals(expected),
                    "Заспавненный дух обязан держать ровно квадрат своего чанка: ожидалось " + expected.size()
                            + " чанков вокруг (" + center.x + ", " + center.z + "), удержано "
                            + spirit.trackedChunks().size());
            helper.assertTrue(level.shouldTickBlocksAt(ChunkPos.asLong(center.x + radius, center.z + radius)),
                    "Дальний угол квадрата обязан тикать, как у игрока: иначе дух не выполняет задачу вдали от игрока");

            spirit.teleportTo(spirit.getX() + 16.0, spirit.getY(), spirit.getZ());
            helper.runAfterDelay(20, () -> {
                ChunkPos moved = spirit.chunkPosition();
                helper.assertTrue(moved.x == center.x + 1 && moved.z == center.z,
                        "Дух обязан оказаться в соседнем по X чанке: был (" + center.x + ", " + center.z
                                + "), стал (" + moved.x + ", " + moved.z + ")");
                Set<ChunkCoord> expectedMoved = ChunkSquare.chunks(new ChunkCoord(moved.x, moved.z), radius);
                helper.assertTrue(spirit.trackedChunks().equals(expectedMoved),
                        "После перехода в соседний чанк набор обязан сдвинуться на один ряд: ожидалось "
                                + expectedMoved.size() + " чанков вокруг (" + moved.x + ", " + moved.z
                                + "), удержано " + spirit.trackedChunks().size());

                long ticketsBeforeDiscard = ticketCount(level);
                spirit.discard();
                long ticketsAfterDiscard = ticketCount(level);
                helper.assertTrue(spirit.trackedChunks().isEmpty(),
                        "После discard дух обязан перестать держать чанки: осталось " + spirit.trackedChunks().size());
                helper.assertTrue(ticketsBeforeDiscard - ticketsAfterDiscard == squareSize(radius),
                        "После discard обязаны сняться ровно все билеты духа: ожидалось " + squareSize(radius)
                                + ", снялось " + (ticketsBeforeDiscard - ticketsAfterDiscard)
                                + ". Иначе мёртвый дух держит чанки нагруженными навсегда");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chunkLoading_UnloadedToChunk_KeepsTickets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int radius = SpiritConfig.radiusFor(level);
        SpiritEntity spirit = spawnFloating(helper);

        helper.runAfterDelay(3, () -> {
            long ticketsBeforeUnload = ticketCount(level);
            spirit.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);

            helper.assertTrue(!spirit.trackedChunks().isEmpty(),
                    "remove(UNLOADED_TO_CHUNK) — это выгрузка, а не смерть: дух обязан сохранить свои чанки, "
                            + "иначе перед снятием с мира снимутся билеты и он потеряет тикающие чанки");
            helper.assertTrue(ticketCount(level) == ticketsBeforeUnload,
                    "remove(UNLOADED_TO_CHUNK) не должен снимать билеты: было " + ticketsBeforeUnload
                            + ", стало " + ticketCount(level));

            spirit.remove(Entity.RemovalReason.DISCARDED);
            helper.assertTrue(spirit.trackedChunks().isEmpty(),
                    "Смерть (discard) поверх выгрузки обязана освободить чанки: осталось "
                            + spirit.trackedChunks().size());
            helper.assertTrue(ticketsBeforeUnload - ticketCount(level) == squareSize(radius),
                    "Смерть обязана снять ровно все билеты духа, что не сняла выгрузка: ожидалось "
                            + squareSize(radius) + ", снялось " + (ticketsBeforeUnload - ticketCount(level)));
            helper.succeed();
        });
    }

    private static SpiritEntity spawnFloating(GameTestHelper helper) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 2, 1));
        spirit.setNoGravity(true);
        return spirit;
    }

    private static long ticketCount(ServerLevel level) {
        ForcedChunksSavedData data = level.getDataStorage().computeIfAbsent(ForcedChunksSavedData.factory(), "chunks");
        long count = 0;
        for (LongSet chunks : data.getEntityForcedChunks().getTickingChunks().values()) {
            LongIterator iterator = chunks.iterator();
            while (iterator.hasNext()) {
                iterator.nextLong();
                count++;
            }
        }
        return count;
    }

    private static long squareSize(int radius) {
        return (2L * radius + 1) * (2L * radius + 1);
    }
}
