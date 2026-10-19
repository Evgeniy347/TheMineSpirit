package com.theminespirit.gametest;

import com.mojang.authlib.GameProfile;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.observation.ItemCount;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritTaskStore;
import com.theminespirit.neoforge.board.BoardRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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

    static Optional<String> taskSnapshot(SpiritEntity spirit) {
        CompoundTag tag = new CompoundTag();
        spirit.saveWithoutId(tag);
        return SpiritTaskStore.read(tag);
    }
    private static final int CLEAR_MARGIN = 2;
    private static final int CLEAR_HEIGHT = 8;
    private static final int SWEEP_MARGIN = 48;
    private static final int SWEEP_BELOW = 16;
    private static final int SWEEP_ABOVE = 40;
    private static final int SWEEP_MIN_AGE_TICKS = 5;
    private static final int MAX_CHUNKS_EAST = 256;
    private static final Map<GameTestInfo, AABB> ACTIVE_SCENES = new ConcurrentHashMap<>();
    private static final Map<GameTestInfo, TimeRestore> TIME_HOLDS = new ConcurrentHashMap<>();

    private GameTestFixtures() {
    }

    static void reset(GameTestHelper helper) {
        reset(helper, CLEAR_HEIGHT);
    }

    static void reset(GameTestHelper helper, int clearHeight) {
        clearBlocks(helper, clearHeight);
        clearEntities(helper, clearHeight);
        sweepEntities(helper);
    }

    static void finish(GameTestHelper helper) {
        finish(helper, CLEAR_HEIGHT);
    }

    static void finish(GameTestHelper helper, int clearHeight) {
        clearBlocks(helper, clearHeight);
        clearEntities(helper, clearHeight);
        unregisterScene(helper);
    }

    private static void clearBlocks(GameTestHelper helper, int clearHeight) {
        for (int x = -CLEAR_MARGIN; x < FLOOR_SIZE + CLEAR_MARGIN; x++) {
            for (int y = 0; y < clearHeight; y++) {
                for (int z = -CLEAR_MARGIN; z < FLOOR_SIZE + CLEAR_MARGIN; z++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
    }

    private static void clearEntities(GameTestHelper helper, int clearHeight) {
        for (Entity entity : helper.getLevel().getEntities((Entity) null, area(helper, clearHeight),
                e -> !(e instanceof Player) && clearable(helper, e))) {
            entity.discard();
        }
    }

    static void sweepEntities(GameTestHelper helper) {
        // Тесты пачки стартуют одновременно: сетапы идут подряд, соседи в этот момент
        // новорождённые (возраст 0), а leftovers прошлых пачек прожили свои тесты. Живые
        // сцены пачки дополнительно прикрыты реестром границ: чужой дух, моб и дроп
        // внутри активной структуры не трогаются в любом возрасте.
        registerScene(helper);
        for (Entity entity : helper.getLevel().getEntities((Entity) null, sweepArea(helper),
                e -> !(e instanceof Player) && e.tickCount > SWEEP_MIN_AGE_TICKS && clearable(helper, e))) {
            entity.discard();
        }
        sweepSpiritLeftovers(helper);
        clearSettlementBoardTickets(helper);
    }

    /**
     * Духи-остатки прошлых пачек живут в чужих структурах, куда локальная зачистка (запас 48)
     * не достаёт, но службы поселения видят их целиком: {@code BoardService.census},
     * {@code CoordinatorLifecycle.census} и {@code FirstStepsRunner} обходят всех духов уровня.
     * Чужой дух с домом уводит общий билет или плодит задания первых шагов — сцена получает чужую
     * работу (флак 17.10: forage, firststeps, supply). Здесь доводим зачистку духов до всего уровня:
     * дух вне активной сцены и старше сетапа пачки — остаток. Мобы остаются локальной зачистке: их
     * радиус восприятия той же сцены не превышает запаса. Живая сцена пачки не тронута — реестр
     * границ и возраст берегут её.
     */
    private static void sweepSpiritLeftovers(GameTestHelper helper) {
        List<Entity> leftovers = new ArrayList<>();
        for (Entity entity : helper.getLevel().getAllEntities()) {
            if (entity instanceof SpiritEntity && entity.tickCount > SWEEP_MIN_AGE_TICKS
                    && clearable(helper, entity)) {
                leftovers.add(entity);
            }
        }
        for (Entity entity : leftovers) {
            entity.discard();
        }
    }

    /**
     * Задания поселения ({@code first:*}, {@code gather:*}) — работа своей сцены, и жить они должны
     * ровно на время сцены: доска уровня билеты не забывает, а свободного духа чужой сцены он забирает
     * раньше, чем та поставит свой (флак 17.10: духу combat/portal/REST доставался чужой
     * {@code first:stock_chests} или {@code gather:oak_planks}, и он уходил его проваливать). Сцена
     * свою цепочку ставит уже после зачистки, поэтому снятие чужих first:/gather: её не трогает;
     * тест цепочки чужие шаги и так закрывает, а сцены первых шагов работают на своей доске.
     */
    private static void clearSettlementBoardTickets(GameTestHelper helper) {
        Board board = BoardRegistry.get(helper.getLevel()).board();
        for (BoardTicket ticket : new ArrayList<>(board.all())) {
            if (ticket.id().startsWith("first:") || ticket.id().startsWith("gather:")) {
                board.cancel(ticket.id());
            }
        }
    }

    private static void registerScene(GameTestHelper helper) {
        pruneScenes();
        try {
            ACTIVE_SCENES.put(helper.testInfo, helper.getBounds());
        } catch (IllegalStateException structureNotPlaced) {
            // Структура ещё не размещена: регистрации нет, остаётся фильтр возраста.
        }
    }

    private static void unregisterScene(GameTestHelper helper) {
        ACTIVE_SCENES.remove(helper.testInfo);
        pruneScenes();
    }

    private static void pruneScenes() {
        ACTIVE_SCENES.keySet().removeIf(info -> info.hasSucceeded() || info.hasFailed());
    }

    private static boolean clearable(GameTestHelper helper, Entity entity) {
        Vec3 at = entity.position();
        for (Map.Entry<GameTestInfo, AABB> scene : ACTIVE_SCENES.entrySet()) {
            if (scene.getKey() != helper.testInfo && scene.getValue().contains(at)) {
                return false;
            }
        }
        return true;
    }

    // Сцена может просить время несколько раз (сон: ночь на старте, утро при засыпании). Слушатель у сцены
    // один и помнит первое значение: вторая просьба только двигает часы, а не подменяет точку возврата.
    // С отдельным слушателем на каждый вызов время утекало — GameTestInfo.testPassed зовёт слушателей в
    // порядке добавления, и последний возвращал не исходное время, а сохранённое перед своим вызовом (ночь
    // после сна падала на следующие пачки; порядок пачек в прогоне неупорядочен).
    static void holdDayTime(GameTestHelper helper, long dayTime) {
        ServerLevel level = helper.getLevel();
        if (TIME_HOLDS.get(helper.testInfo) == null) {
            TimeRestore hold = new TimeRestore(level, level.getDayTime());
            TIME_HOLDS.put(helper.testInfo, hold);
            helper.testInfo.addListener(hold);
        }
        level.setDayTime(dayTime);
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

    static ServerPlayer mockPlayer(GameTestHelper helper, BlockPos relative, String name,
            List<Component> capturedMessages) {
        MinecraftServer server = helper.getLevel().getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public void sendSystemMessage(Component message) {
                capturedMessages.add(message);
            }
        };
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

    private static AABB sweepArea(GameTestHelper helper) {
        Vec3 first = helper.absoluteVec(new Vec3(-SWEEP_MARGIN, -SWEEP_BELOW, -SWEEP_MARGIN));
        Vec3 second = helper.absoluteVec(
                new Vec3(FLOOR_SIZE + SWEEP_MARGIN, SWEEP_ABOVE, FLOOR_SIZE + SWEEP_MARGIN));
        return new AABB(first, second);
    }

    private record TimeRestore(ServerLevel level, long savedDayTime) implements GameTestListener {
        @Override
        public void testStructureLoaded(GameTestInfo testInfo) {
        }

        @Override
        public void testPassed(GameTestInfo test, GameTestRunner runner) {
            level.setDayTime(savedDayTime);
            TIME_HOLDS.remove(test);
        }

        @Override
        public void testFailed(GameTestInfo test, GameTestRunner runner) {
            level.setDayTime(savedDayTime);
            TIME_HOLDS.remove(test);
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
