package com.theminespirit.core.chunk;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.TownPlan;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class SettlementZone {
    private @Nullable TownPlan plan;
    private Set<BlockCoord> anchors = Set.of();
    private Set<ColumnMask.Cell> posts = Set.of();
    private Set<ChunkCoord> spiritChunks = Set.of();
    private int spiritRadius = -1;
    private int probeRadius = -1;
    private Set<ChunkCoord> work = Set.of();
    private Set<ChunkCoord> settlement = Set.of();

    public boolean update(
            @Nullable TownPlan plan,
            Set<BlockCoord> anchors,
            Collection<BlockCoord> spirits,
            int spiritRadius,
            int probeRadius,
            Collection<ColumnMask.Cell> posts) {
        Objects.requireNonNull(anchors, "anchors не может быть null: без якорей зона поселения пустая, а не null");
        Objects.requireNonNull(spirits, "spirits не может быть null: без духов зона работ — только поселение");
        Objects.requireNonNull(posts, "posts не может быть null: без столбов ограды их чанки не удержать");
        if (spiritRadius < 0) {
            throw new IllegalArgumentException("Радиус чанков духа не может быть отрицательным: " + spiritRadius);
        }
        if (probeRadius < 0) {
            throw new IllegalArgumentException("Радиус обмера поселения не может быть отрицательным: " + probeRadius);
        }
        Set<ChunkCoord> chunks = spiritChunks(spirits);
        if ((same(plan, this.plan) || Objects.equals(plan, this.plan))
                && spiritRadius == this.spiritRadius
                && probeRadius == this.probeRadius
                && anchors.equals(this.anchors)
                && chunks.equals(this.spiritChunks)
                && (same(posts, this.posts) || Set.copyOf(posts).equals(this.posts))) {
            return false;
        }
        Set<ColumnMask.Cell> postSet = Set.copyOf(posts);
        this.plan = plan;
        this.anchors = Set.copyOf(anchors);
        this.posts = postSet;
        this.spiritChunks = chunks;
        this.spiritRadius = spiritRadius;
        this.probeRadius = probeRadius;
        this.work = SettlementChunks.workZone(plan, this.anchors, spirits, spiritRadius);
        this.settlement = SettlementChunks.settlement(plan, this.anchors, probeRadius, this.posts);
        return true;
    }

    @SuppressWarnings("ReferenceEquality")
    private static boolean same(@Nullable Object left, @Nullable Object right) {
        return left == right;
    }

    public Set<ChunkCoord> workZone() {
        return work;
    }

    public Set<ChunkCoord> settlement() {
        return settlement;
    }

    private static Set<ChunkCoord> spiritChunks(Collection<BlockCoord> spirits) {
        Set<ChunkCoord> chunks = new LinkedHashSet<>();
        for (BlockCoord spirit : spirits) {
            chunks.add(new ChunkCoord(Math.floorDiv(spirit.x(), 16), Math.floorDiv(spirit.z(), 16)));
        }
        return Set.copyOf(chunks);
    }
}
