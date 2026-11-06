package com.theminespirit.neoforge.movement;

import com.theminespirit.core.navigation.NavActionCosts;
import com.theminespirit.core.navigation.NavWorld;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.function.Predicate;

public final class RescueNavWorld implements NavWorld {
    private final NavWorld live;
    private final BlockCoord start;
    private final Predicate<BlockCoord> blocked;

    public RescueNavWorld(NavWorld live, BlockCoord start, Predicate<BlockCoord> blocked) {
        this.live = Objects.requireNonNull(live, "Мир навигации не может быть null: без него не видно блоков");
        this.start = Objects.requireNonNull(start, "Старт не может быть null: его клетка не запрещается");
        this.blocked = Objects.requireNonNull(blocked, "Запрет клеток не может быть null: пусто — запрета нет");
    }

    private boolean forbidden(int x, int y, int z) {
        BlockCoord cell = new BlockCoord(x, y, z);
        return !cell.equals(start) && blocked.test(cell);
    }

    @Override
    public boolean available(int x, int z) {
        return live.available(x, z);
    }

    @Override
    public boolean passable(int x, int y, int z) {
        return !forbidden(x, y, z) && live.passable(x, y, z);
    }

    @Override
    public boolean walkable(int x, int y, int z) {
        return !forbidden(x, y, z) && live.walkable(x, y, z);
    }

    @Override
    public boolean water(int x, int y, int z) {
        return !forbidden(x, y, z) && live.water(x, y, z);
    }

    @Override
    public boolean climbable(int x, int y, int z) {
        return !forbidden(x, y, z) && live.climbable(x, y, z);
    }

    @Override
    public double breakTicks(int x, int y, int z) {
        return forbidden(x, y, z) ? NavActionCosts.COST_INF : live.breakTicks(x, y, z);
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
