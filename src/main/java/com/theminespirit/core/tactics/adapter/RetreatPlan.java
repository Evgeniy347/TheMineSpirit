package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.Point;
import java.util.List;

public sealed interface RetreatPlan {
    record Walk(Point target, double arrival, boolean shooting) implements RetreatPlan {
        public Walk {
            if (target == null || arrival <= 0) {
                throw new IllegalArgumentException("RetreatPlan.Walk: цель обязательна, дистанция прибытия больше нуля; "
                        + "получено target = " + target + ", arrival = " + arrival);
            }
        }
    }

    record Wall(List<BlockCoord> cells) implements RetreatPlan {
        public Wall {
            if (cells == null || cells.isEmpty()) {
                throw new IllegalArgumentException("RetreatPlan.Wall: у баррикады должна быть хотя бы одна клетка; "
                        + "без блоков в инвентаре план не строится вовсе");
            }
            cells = List.copyOf(cells);
        }
    }

    record Tower(int height) implements RetreatPlan {
        public Tower {
            if (height < 1) {
                throw new IllegalArgumentException("RetreatPlan.Tower.height = " + height
                        + ": столб ниже одного блока бессмыслен; без блоков в инвентаре план не строится");
            }
        }
    }
}
