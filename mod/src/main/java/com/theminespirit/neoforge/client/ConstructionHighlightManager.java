package com.theminespirit.neoforge.client;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;

public final class ConstructionHighlightManager {
    private static final ConstructionHighlightManager INSTANCE = new ConstructionHighlightManager();

    private boolean enabled;
    private Set<BlockPos> wrongBlocks = Set.of();
    private Set<BlockPos> missingBlocks = Set.of();
    private Set<BlockPos> pendingWrong = Set.of();
    private Set<BlockPos> pendingMissing = Set.of();

    private ConstructionHighlightManager() {}

    public static ConstructionHighlightManager instance() {
        return INSTANCE;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Set<BlockPos> wrongBlocks() {
        return wrongBlocks;
    }

    public Set<BlockPos> missingBlocks() {
        return missingBlocks;
    }

    public void toggle() {
        enabled = !enabled;
        if (!enabled) {
            clear();
        }
    }

    public void clear() {
        wrongBlocks = Set.of();
        missingBlocks = Set.of();
        pendingWrong = Set.of();
        pendingMissing = Set.of();
    }

    public void updateData(Set<BlockPos> wrong, Set<BlockPos> missing) {
        wrongBlocks = Set.copyOf(wrong);
        missingBlocks = Set.copyOf(missing);
    }

    public void beginCycle() {
        pendingWrong = Set.of();
        pendingMissing = Set.of();
    }

    public void addData(Set<BlockPos> wrong, Set<BlockPos> missing) {
        Set<BlockPos> nextWrong = new LinkedHashSet<>(pendingWrong);
        nextWrong.addAll(wrong);
        pendingWrong = Set.copyOf(nextWrong);
        Set<BlockPos> nextMissing = new LinkedHashSet<>(pendingMissing);
        nextMissing.addAll(missing);
        pendingMissing = Set.copyOf(nextMissing);
    }

    public void commitCycle() {
        wrongBlocks = pendingWrong;
        missingBlocks = pendingMissing;
        pendingWrong = Set.of();
        pendingMissing = Set.of();
    }
}
