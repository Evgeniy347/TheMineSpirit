package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.board.StockReader;
import com.theminespirit.neoforge.policy.PolicyConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

public final class ChestWithdrawal {
    private ChestWithdrawal() {}

    public static boolean inside(SpiritEntity spirit) {
        Objects.requireNonNull(spirit, "Дух не может быть null: поселение проверяется по его позиции");
        return spirit.level() instanceof ServerLevel level
                && settlementArea(level)
                        .filter(area -> {
                            BlockPos at = spirit.blockPosition();
                            return area.contains(at.getX(), at.getZ());
                        })
                        .isPresent();
    }

    public static boolean allowed(SpiritEntity spirit, BlockPos container) {
        Objects.requireNonNull(spirit, "Дух не может быть null: забор проверяется по его политикам");
        Objects.requireNonNull(container, "Контейнер не может быть null: забирать неоткуда");
        if (!(spirit.level() instanceof ServerLevel level)) {
            return false;
        }
        Optional<ColumnMask> area = settlementArea(level);
        return area.isPresent() && allowed(spirit, container, area.orElseThrow());
    }

    public static List<BlockPos> positions(SpiritEntity spirit) {
        Objects.requireNonNull(spirit, "Дух не может быть null: сундуки выбираются для него");
        if (!(spirit.level() instanceof ServerLevel level)) {
            return List.of();
        }
        Optional<ColumnMask> area = settlementArea(level);
        if (area.isEmpty() || !inside(spirit)) {
            return List.of();
        }
        ColumnMask current = area.orElseThrow();
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockCoord pos : StockReader.containerPositions(level)) {
            BlockPos at = new BlockPos(pos.x(), pos.y(), pos.z());
            if (!allowed(spirit, at, current)) {
                continue;
            }
            BlockPos other = otherHalf(level, at);
            if (seen.add(at) && (other.equals(at) || seen.add(other))) {
                found.add(at);
            }
        }
        BlockPos origin = spirit.blockPosition();
        found.sort(Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(origin))
                .thenComparing(Comparator.naturalOrder()));
        return List.copyOf(found);
    }

    public static Predicate<BlockCoord> remoteWithdrawal(ServerLevel level, List<SpiritEntity> spirits) {
        Objects.requireNonNull(level, "Уровень не может быть null: граница поселения берётся из него");
        Objects.requireNonNull(spirits, "Список духов не может быть null: искать того, кто может забрать");
        Optional<ColumnMask> area = settlementArea(level);
        if (area.isEmpty()) {
            return pos -> false;
        }
        ColumnMask current = area.orElseThrow();
        List<SpiritEntity> inside = spirits.stream()
                .filter(spirit -> level.equals(spirit.level()))
                .filter(spirit -> {
                    BlockPos at = spirit.blockPosition();
                    return current.contains(at.getX(), at.getZ());
                })
                .toList();
        return pos -> current.contains(pos.x(), pos.z())
                && inside.stream()
                        .anyMatch(spirit -> allowed(spirit, new BlockPos(pos.x(), pos.y(), pos.z()), current));
    }

    static boolean inside(ColumnMask area, BlockPos spirit, BlockPos container) {
        return area.contains(spirit.getX(), spirit.getZ()) && area.contains(container.getX(), container.getZ());
    }

    private static boolean allowed(SpiritEntity spirit, BlockPos container, ColumnMask area) {
        if (!spirit.level().isLoaded(container) || !inside(area, spirit.blockPosition(), container)) {
            return false;
        }
        BlockState state = spirit.level().getBlockState(container);
        if (!ContainerAccess.allowed(state)) {
            return false;
        }
        BlockCoord coord = new BlockCoord(container.getX(), container.getY(), container.getZ());
        return spirit.protectionGate().checkTravel(coord) instanceof BlockProtection.Allowed
                && !PolicyConfig.zones()
                        .contains(spirit.level().dimension().location().toString(), coord);
    }

    private static BlockPos otherHalf(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return pos.relative(ChestBlock.getConnectedDirection(state));
        }
        return pos;
    }

    private static Optional<ColumnMask> settlementArea(ServerLevel level) {
        return SettlementPlotStore.settlementArea(level);
    }
}
