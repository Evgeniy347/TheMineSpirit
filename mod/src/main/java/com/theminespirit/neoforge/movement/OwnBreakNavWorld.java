package com.theminespirit.neoforge.movement;

import com.theminespirit.core.navigation.NavActionCosts;
import com.theminespirit.core.navigation.NavWorld;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.function.Predicate;

public final class OwnBreakNavWorld implements NavWorld {
    private final NavWorld live;
    private final Predicate<BlockCoord> own;

    public OwnBreakNavWorld(NavWorld live, Predicate<BlockCoord> own) {
        this.live = Objects.requireNonNull(live, "Мир навигации не может быть null: без него не видно блоков");
        this.own = Objects.requireNonNull(own, "Признак своего блока не может быть null: иначе ломается чужое");
    }

    @Override
    public boolean available(int x, int z) {
        return live.available(x, z);
    }

    @Override
    public boolean passable(int x, int y, int z) {
        return live.passable(x, y, z);
    }

    @Override
    public boolean walkable(int x, int y, int z) {
        return live.walkable(x, y, z);
    }

    @Override
    public boolean water(int x, int y, int z) {
        return live.water(x, y, z);
    }

    @Override
    public boolean climbable(int x, int y, int z) {
        return live.climbable(x, y, z);
    }

    @Override
    public double breakTicks(int x, int y, int z) {
        if (!own.test(new BlockCoord(x, y, z))) {
            return NavActionCosts.COST_INF;
        }
        return live.breakTicks(x, y, z);
    }

    @Override
    public int minY() {
        return live.minY();
    }

    @Override
    public int maxY() {
        return live.maxY();
    }
}
