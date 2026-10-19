package com.theminespirit.neoforge.crater;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.Crater;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import org.slf4j.Logger;

/**
 * Запоминает воронки от криперов рядом с поселением: центр, радиус и уровень края.
 *
 * <p>Запись идёт по событию Detonate, пока блоки ещё не осыпались: по краю воронки снимается
 * уровень окружающей земли, по которому потом засыпают.
 */
public final class CraterEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int RIM_SAMPLES = 16;
    private static final int RIM_SCAN_DEPTH = 24;

    private CraterEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener(CraterEvents::onDetonate);
    }

    private static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Entity source = event.getExplosion().getDirectSourceEntity();
        if (!(source instanceof Creeper)) {
            return;
        }
        BlockPos center = BlockPos.containing(event.getExplosion().center());
        if (!nearSettlement(level, center)) {
            return;
        }
        int radius = Math.max(
                Crater.RADIUS_MIN, Math.min((int) Math.ceil(event.getExplosion().radius()), Crater.RADIUS_MAX));
        int edge = rimLevel(level, center, radius);
        Crater crater = new Crater(
                UUID.randomUUID(),
                new BlockCoord(center.getX(), center.getY(), center.getZ()),
                radius,
                edge,
                level.getGameTime());
        CraterStore.get(level).add(crater);
        LOGGER.info(
                "Запомнена воронка {} от крипера: центр {}, радиус {}, уровень края {}",
                crater.id(),
                center.toShortString(),
                radius,
                edge);
    }

    private static boolean nearSettlement(ServerLevel level, BlockPos center) {
        int reach = SpiritConfig.craterHomeRadius();
        AABB area = new AABB(center).inflate(reach);
        List<SpiritEntity> spirits = level.getEntitiesOfClass(
                SpiritEntity.class,
                area,
                spirit -> !spirit.isRemoved() && spirit.home().isPresent());
        for (SpiritEntity spirit : spirits) {
            BlockCoord home = spirit.home().orElseThrow();
            long dx = (long) center.getX() - home.x();
            long dz = (long) center.getZ() - home.z();
            if (dx * dx + dz * dz <= (long) reach * reach) {
                return true;
            }
        }
        return false;
    }

    private static int rimLevel(ServerLevel level, BlockPos center, int radius) {
        int baseline = level.getMinBuildHeight();
        int[] histogram = new int[level.getHeight() + 1];
        int rim = radius + 1;
        for (int sample = 0; sample < RIM_SAMPLES; sample++) {
            double angle = 2 * Math.PI * sample / RIM_SAMPLES;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * rim);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * rim);
            int top = surfaceTop(level, x, z, center.getY());
            int index = Math.max(0, Math.min(histogram.length - 1, top - baseline));
            histogram[index]++;
        }
        int bestIndex = 0;
        for (int i = 1; i < histogram.length; i++) {
            if (histogram[i] > histogram[bestIndex]) {
                bestIndex = i;
            }
        }
        return baseline + bestIndex;
    }

    private static int surfaceTop(ServerLevel level, int x, int z, int aroundY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int top = Math.min(level.getMaxBuildHeight() - 1, aroundY + RIM_SCAN_DEPTH);
        int bottom = Math.max(level.getMinBuildHeight(), aroundY - RIM_SCAN_DEPTH);
        for (int y = top; y >= bottom; y--) {
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            if (state.isAir()) {
                continue;
            }
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) {
                continue;
            }
            if (state.isFaceSturdy(level, cursor, Direction.UP)) {
                return y;
            }
        }
        return aroundY;
    }
}
