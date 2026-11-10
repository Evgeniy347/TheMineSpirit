package com.theminespirit.neoforge;

import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

final class ChopPursuit {
    private final SpiritEntity spirit;
    private final List<BlockPos> used = new ArrayList<>();
    private @Nullable Tree tree;
    private ChopStand.@Nullable Placement current;

    ChopPursuit(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    void begin(Tree target) {
        tree = target;
        used.clear();
        current = null;
    }

    ChopStand.@Nullable Placement first() {
        return remember(ChopStand.first(spirit, requireTree()));
    }

    ChopStand.@Nullable Placement next(List<BlockCoord> rest) {
        return remember(ChopStand.next(spirit, requireTree(), rest, used));
    }

    @Nullable
    BlockPos stand() {
        ChopStand.Placement picked = current;
        return picked == null ? null : picked.feet();
    }

    @Nullable
    BlockCoord climbFrom() {
        ChopStand.Placement picked = current;
        return picked == null ? null : picked.climbFrom();
    }

    int tried() {
        return used.size();
    }

    private Tree requireTree() {
        Tree target = tree;
        if (target == null) {
            throw new IllegalStateException("Поиск стоянки до начала рубки: дерево не задано");
        }
        return target;
    }

    private ChopStand.@Nullable Placement remember(ChopStand.@Nullable Placement picked) {
        if (picked != null) {
            used.add(picked.feet());
            current = picked;
        }
        return picked;
    }
}
