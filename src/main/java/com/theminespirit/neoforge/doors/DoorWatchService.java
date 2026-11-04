package com.theminespirit.neoforge.doors;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.doors.DoorClosing;
import com.theminespirit.core.doors.DoorState;
import com.theminespirit.core.doors.DoorTuning;
import com.theminespirit.core.doors.DoorWatch;
import com.theminespirit.core.doors.SpiritRef;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;

public final class DoorWatchService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double TICKS_PER_DAY = 24_000.0;

    private static DoorWatchService instance;

    private final DoorWatch watch = DoorWatch.withDefaultRules();
    private final Map<ServerLevel, Tracked> tracked = new HashMap<>();
    private final Map<UUID, SpiritEntity> spirits = new HashMap<>();

    private DoorWatchService() {}

    public static void register() {
        if (instance != null) {
            throw new IllegalStateException(
                    "DoorWatchService уже зарегистрирован: второй экземпляр удвоил бы задачи духам");
        }
        DoorWatchService service = new DoorWatchService();
        NeoForge.EVENT_BUS.register(service);
        instance = service;
    }

    static DoorWatchService instance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "DoorWatchService не зарегистрирован: мод обязан зарегистрировать его в TheMineSpirit");
        }
        return instance;
    }

    @SubscribeEvent
    public void onDoorInteracted(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!level.getBlockState(event.getPos()).hasProperty(BlockStateProperties.OPEN)) {
            return;
        }
        Tracked state = tracked.computeIfAbsent(level, key -> new Tracked());
        state.pendingChecks().add(event.getPos().immutable());
    }

    @SubscribeEvent
    public void onSpiritJoined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof SpiritEntity spirit)) {
            return;
        }
        spirits.put(spirit.getUUID(), spirit);
    }

    @SubscribeEvent
    public void onSpiritLeft(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof SpiritEntity spirit)) {
            return;
        }
        spirits.remove(spirit.getUUID(), spirit);
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            tick(level);
        }
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            forget(level);
        }
    }

    public static void onClosed(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Tracked state = instance().tracked.get(serverLevel);
        if (state == null) {
            return;
        }
        state.closed(pos);
    }

    public void tick(ServerLevel level) {
        Tracked state = tracked.computeIfAbsent(level, key -> new Tracked());
        long now = level.getGameTime();
        checkPending(level, state, now);
        if (now % DoorTuning.loadDefault().recheckIntervalTicks() == 0) {
            recheck(level, state, now);
        }
        decide(level, state, now, watch.isEvening(dayFraction(now)));
    }

    public static void forget(ServerLevel level) {
        instance().tracked.remove(level);
        instance().spirits.values().removeIf(spirit -> level.equals(spirit.level()));
    }

    private void checkPending(ServerLevel level, Tracked state, long now) {
        for (BlockPos pos : state.pendingChecks()) {
            state.rememberDoor(pos, level.getBlockState(pos), now);
        }
        state.pendingChecks().clear();
    }

    private void recheck(ServerLevel level, Tracked state, long now) {
        int radius = DoorTuning.loadDefault().settlementRadius();
        int height = 2;
        for (BlockCoord home : homes()) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = -height; dy <= height + 1; dy++) {
                        int x = home.x() + dx;
                        int y = home.y() + dy;
                        int z = home.z() + dz;
                        if (y < -64 || y > 319) {
                            continue;
                        }
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState block = level.getBlockState(pos);
                        if (block.hasProperty(BlockStateProperties.OPEN)) {
                            state.rememberDoor(pos, block, now);
                        }
                    }
                }
            }
        }
        for (BlockPos pos : state.knownDoors()) {
            state.rememberDoor(pos, level.getBlockState(pos), now);
        }
    }

    private void decide(ServerLevel level, Tracked state, long now, boolean evening) {
        List<BlockCoord> homes = homes();
        List<DoorState> doors = new ArrayList<>();
        for (BlockPos pos : state.knownDoors()) {
            Tracked.DoorInfo info = state.door(pos);
            if (info == null || !info.open()) {
                continue;
            }
            if (!watch.inSettlement(blockCoord(pos), homes)) {
                continue;
            }
            doors.add(new DoorState(blockCoord(pos), true, info.openedSinceTick(), playersNearby(level, pos)));
        }
        List<SpiritRef> spiritRefs = new ArrayList<>();
        for (SpiritEntity spirit : spirits.values()) {
            if (spirit.isRemoved() || spirit.isDemo() || !spirit.level().equals(level)) {
                continue;
            }
            spiritRefs.add(new SpiritRef(spirit.getUUID(), blockCoord(spirit.blockPosition())));
        }
        for (DoorClosing closing : watch.decide(doors, spiritRefs, now, evening)) {
            assign(level, state, closing);
        }
    }

    private void assign(ServerLevel level, Tracked state, DoorClosing closing) {
        if (state.assigned().containsKey(closing.door())) {
            return;
        }
        SpiritEntity spirit = spirits.get(closing.spirit());
        if (spirit == null || spirit.isRemoved() || !spirit.level().equals(level)) {
            return;
        }
        state.assigned().put(closing.door(), closing.spirit());
        Map<String, Object> params = new HashMap<>();
        params.put("x", closing.door().x());
        params.put("y", closing.door().y());
        params.put("z", closing.door().z());
        spirit.startTask(new ScriptCall("close_door", params));
        LOGGER.debug("Дух {} закрывает дверь {}", closing.spirit(), closing.door());
    }

    private List<BlockCoord> playersNearby(ServerLevel level, BlockPos pos) {
        double radius = DoorTuning.loadDefault().playerNearbyRadius();
        List<BlockCoord> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= radius * radius) {
                players.add(blockCoord(player.blockPosition()));
            }
        }
        return players;
    }

    private List<BlockCoord> homes() {
        List<BlockCoord> homes = new ArrayList<>();
        for (SpiritEntity spirit : spirits.values()) {
            Optional<BlockCoord> home = spirit.home();
            homes.add(home.orElseGet(() -> blockCoord(spirit.blockPosition())));
        }
        return homes;
    }

    private static double dayFraction(long now) {
        return Math.floorMod(now, 24_000L) / TICKS_PER_DAY;
    }

    private static BlockCoord blockCoord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    private static final class Tracked {
        private final Map<BlockPos, DoorInfo> doors = new HashMap<>();
        private final Set<BlockPos> pendingChecks = new HashSet<>();
        private final Map<BlockCoord, UUID> assigned = new HashMap<>();

        Set<BlockPos> pendingChecks() {
            return pendingChecks;
        }

        Map<BlockCoord, UUID> assigned() {
            return assigned;
        }

        List<BlockPos> knownDoors() {
            return List.copyOf(doors.keySet());
        }

        DoorInfo door(BlockPos pos) {
            return doors.get(pos);
        }

        void rememberDoor(BlockPos pos, BlockState block, long now) {
            if (!block.hasProperty(BlockStateProperties.OPEN)) {
                doors.remove(pos);
                return;
            }
            boolean open = block.getValue(BlockStateProperties.OPEN);
            DoorInfo current = doors.get(pos);
            if (current == null) {
                if (open) {
                    doors.put(pos, new DoorInfo(true, now));
                }
                return;
            }
            if (open != current.open()) {
                doors.put(pos, new DoorInfo(open, open ? now : 0L));
            }
        }

        void closed(BlockPos pos) {
            doors.remove(pos);
            assigned.entrySet().removeIf(entry -> entry.getKey().equals(blockCoord(pos)));
        }

        private record DoorInfo(boolean open, long openedSinceTick) {}
    }
}
