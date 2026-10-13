package com.theminespirit.gametest;

import com.mojang.authlib.GameProfile;
import com.theminespirit.core.observation.ItemCount;
import com.theminespirit.core.observation.WorldObservation;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class GameTestFixtures {
    static final int FLOOR_SIZE = 9;
    private static final int CLEAR_MARGIN = 2;
    private static final int CLEAR_HEIGHT = 8;
    private static final int MAX_CHUNKS_EAST = 256;

    private GameTestFixtures() {
    }

    static void reset(GameTestHelper helper) {
        reset(helper, CLEAR_HEIGHT);
    }

    static void reset(GameTestHelper helper, int clearHeight) {
        for (int x = -CLEAR_MARGIN; x < FLOOR_SIZE + CLEAR_MARGIN; x++) {
            for (int y = 0; y < clearHeight; y++) {
                for (int z = -CLEAR_MARGIN; z < FLOOR_SIZE + CLEAR_MARGIN; z++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        for (Entity entity : helper.getLevel().getEntities((Entity) null, area(helper, clearHeight),
                e -> !(e instanceof Player))) {
            entity.discard();
        }
    }

    static void buildFloor(GameTestHelper helper, Block block) {
        for (int x = 0; x < FLOOR_SIZE; x++) {
            for (int z = 0; z < FLOOR_SIZE; z++) {
                helper.setBlock(x, 0, z, block);
            }
        }
    }

    static ItemEntity dropAt(GameTestHelper helper, Item item, int count, double x, double y, double z) {
        Vec3 absolute = helper.absoluteVec(new Vec3(x, y, z));
        ItemEntity drop = new ItemEntity(helper.getLevel(), absolute.x, absolute.y, absolute.z, new ItemStack(item, count));
        drop.setDeltaMovement(Vec3.ZERO);
        drop.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(drop);
        return drop;
    }

    static List<ItemEntity> dropsInArea(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(helper),
                drop -> !drop.isRemoved() && drop.getItem().is(item));
    }

    static int countInInventory(Container inventory, Item item) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    static int countInWorld(GameTestHelper helper, Item item) {
        return dropsInArea(helper, item).stream().mapToInt(drop -> drop.getItem().getCount()).sum();
    }

    static int blockCount(WorldObservation observation, String blockId) {
        return observation.blocks().stream().filter(entry -> entry.item().equals(blockId))
                .mapToInt(ItemCount::count).sum();
    }

    static ServerPlayer mockPlayer(GameTestHelper helper, BlockPos relative, String name) {
        MinecraftServer server = helper.getLevel().getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        BlockPos absolute = helper.absolutePos(relative);
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
        helper.testInfo.addListener(new PlayerCleanup(server, player));
        return player;
    }

    static BlockPos firstUnloadedChunkEast(GameTestHelper helper, BlockPos base) {
        int chunkX = base.getX() >> 4;
        int chunkZ = base.getZ() >> 4;
        for (int k = 1; k <= MAX_CHUNKS_EAST; k++) {
            if (!helper.getLevel().hasChunk(chunkX + k, chunkZ)) {
                return new BlockPos((chunkX + k) * 16, base.getY(), base.getZ());
            }
        }
        return null;
    }

    private static AABB area(GameTestHelper helper) {
        return area(helper, CLEAR_HEIGHT);
    }

    private record PlayerCleanup(MinecraftServer server, ServerPlayer player) implements GameTestListener {
        @Override
        public void testStructureLoaded(GameTestInfo testInfo) {
        }

        @Override
        public void testPassed(GameTestInfo test, GameTestRunner runner) {
            server.getPlayerList().remove(player);
        }

        @Override
        public void testFailed(GameTestInfo test, GameTestRunner runner) {
            server.getPlayerList().remove(player);
        }

        @Override
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
        }
    }

    private static AABB area(GameTestHelper helper, int clearHeight) {
        Vec3 first = helper.absoluteVec(new Vec3(-CLEAR_MARGIN, 0, -CLEAR_MARGIN));
        Vec3 second = helper.absoluteVec(new Vec3(FLOOR_SIZE + CLEAR_MARGIN, clearHeight, FLOOR_SIZE + CLEAR_MARGIN));
        return new AABB(first, second);
    }
}
