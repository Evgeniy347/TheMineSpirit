package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.StandSearch;
import com.theminespirit.core.building.RetreatPath;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.movement.NetworkLevelView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

final class HomeStand {
    private static final int REACH = 12;
    private static final int HEIGHT = 5;

    private HomeStand() {}

    static BlockCoord of(
            ServerLevel level, StageSite stage, List<SpiritEntity> spirits, Optional<BlockCoord> remembered) {
        BlockCoord center = SettlementHome.center(SettlementHome.homes(spirits)).orElse(stage.anchor());
        return at(level, center, spirits, remembered);
    }

    static BlockCoord at(
            ServerLevel level, BlockCoord home, List<SpiritEntity> spirits, Optional<BlockCoord> remembered) {
        LevelSafetyView view = new LevelSafetyView(level);
        Optional<BlockCoord> reachable = reachable(level, home, spirits, remembered);
        if (reachable.isPresent()) {
            return reachable.orElseThrow();
        }
        if (remembered.isPresent() && MiningSafety.canStand(remembered.orElseThrow(), view)) {
            return remembered.orElseThrow();
        }
        return at(home, cell -> MiningSafety.canStand(cell, view));
    }

    static BlockCoord at(BlockCoord home, Predicate<BlockCoord> canStand) {
        return of(List.of(home), home, canStand);
    }

    static BlockCoord of(List<BlockCoord> homes, BlockCoord anchor, Predicate<BlockCoord> canStand) {
        BlockCoord center = SettlementHome.center(homes).orElse(anchor);
        return StandSearch.nearest(center, canStand);
    }

    static Optional<BlockCoord> reachable(
            ServerLevel level, BlockCoord center, List<SpiritEntity> spirits, Optional<BlockCoord> remembered) {
        if (spirits.isEmpty()) {
            return Optional.empty();
        }
        NetworkLevelView world = new NetworkLevelView(level);
        List<Set<BlockCoord>> components = new ArrayList<>();
        for (SpiritEntity spirit : nearby(center, spirits)) {
            BlockCoord feet = feet(spirit);
            if (components.stream().anyMatch(component -> component.contains(feet))) {
                continue;
            }
            RetreatPath.walkable(feet, world, cell -> within(center, cell)).ifPresent(components::add);
        }
        return largest(center, components, remembered);
    }

    static Optional<BlockCoord> largest(
            BlockCoord center, List<Set<BlockCoord>> components, Optional<BlockCoord> remembered) {
        if (remembered.isPresent()
                && components.stream().anyMatch(component -> component.contains(remembered.orElseThrow()))) {
            return remembered;
        }
        return largest(center, components);
    }

    static Optional<BlockCoord> largest(BlockCoord center, List<Set<BlockCoord>> components) {
        Set<BlockCoord> bestComponent = null;
        BlockCoord bestCell = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Set<BlockCoord> component : components) {
            Optional<BlockCoord> cell = nearest(center, component);
            if (cell.isEmpty()) {
                continue;
            }
            int distance = distance(center, cell.orElseThrow());
            if (bestComponent == null
                    || component.size() > bestComponent.size()
                    || (component.size() == bestComponent.size() && distance < bestDistance)) {
                bestComponent = component;
                bestCell = cell.orElseThrow();
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(bestCell);
    }

    private static List<SpiritEntity> nearby(BlockCoord center, List<SpiritEntity> spirits) {
        return spirits.stream()
                .filter(spirit -> within(center, feet(spirit)))
                .sorted(Comparator.comparingInt(spirit -> distance(center, feet(spirit))))
                .toList();
    }

    private static boolean within(BlockCoord center, BlockCoord cell) {
        return Math.abs(cell.x() - center.x()) <= REACH
                && Math.abs(cell.y() - center.y()) <= HEIGHT
                && Math.abs(cell.z() - center.z()) <= REACH;
    }

    private static BlockCoord feet(SpiritEntity spirit) {
        BlockPos position = spirit.blockPosition();
        return new BlockCoord(position.getX(), position.getY(), position.getZ());
    }

    private static Optional<BlockCoord> nearest(BlockCoord center, Set<BlockCoord> cells) {
        BlockCoord best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (BlockCoord cell : cells) {
            int distance = distance(center, cell);
            if (distance < bestDistance) {
                best = cell;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    private static int distance(BlockCoord first, BlockCoord second) {
        int dx = first.x() - second.x();
        int dy = first.y() - second.y();
        int dz = first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
