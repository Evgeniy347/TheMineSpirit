package com.theminespirit.core.town;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SettlementReach(int minX, int minZ, int maxX, int maxZ) {
    public static final int MARGIN = 64;

    public enum Verdict {
        ALLOWED,
        NO_SETTLEMENT,
        OTHER_DIMENSION,
        TOO_FAR
    }

    public record Access(Verdict verdict, int distance) {
        public Access {
            Objects.requireNonNull(verdict, "Решение о доступе не может быть null");
            if (distance < 0) {
                throw new IllegalArgumentException("Расстояние до поселения не бывает отрицательным: " + distance);
            }
        }

        public boolean allowed() {
            return verdict == Verdict.ALLOWED;
        }
    }

    public SettlementReach {
        if (maxX < minX || maxZ < minZ) {
            throw new IllegalArgumentException("Граница поселения [" + minX + ".." + maxX + "]×[" + minZ + ".." + maxZ
                    + "] пуста: участок плана обязан иметь хотя бы одну клетку");
        }
    }

    public static SettlementReach of(TownPlan plan, List<ColumnMask.Cell> extraPosts) {
        Objects.requireNonNull(plan, "План поселения не может быть null: граница берётся из его участка");
        Objects.requireNonNull(extraPosts, "Столбы ограды не могут быть null: нет столбов — пустой список");
        ColumnMask region = plan.region();
        int[] box = {
            region.minX(), region.minZ(), region.minX() + region.width() - 1, region.minZ() + region.depth() - 1
        };
        include(box, plan.fence().posts());
        include(box, extraPosts);
        return new SettlementReach(box[0], box[1], box[2], box[3]);
    }

    public static Access access(Optional<SettlementReach> reach, boolean sameDimension, double x, double z) {
        Objects.requireNonNull(reach, "Граница не может быть null: нет поселения — Optional.empty()");
        if (reach.isEmpty()) {
            return new Access(Verdict.NO_SETTLEMENT, 0);
        }
        if (!sameDimension) {
            return new Access(Verdict.OTHER_DIMENSION, 0);
        }
        int distance = reach.orElseThrow().distance(x, z);
        return new Access(distance <= MARGIN ? Verdict.ALLOWED : Verdict.TOO_FAR, distance);
    }

    public boolean covers(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public int distance(double x, double z) {
        double dx = Math.max(0, Math.max(minX - x, x - (maxX + 1)));
        double dz = Math.max(0, Math.max(minZ - z, z - (maxZ + 1)));
        return (int) Math.ceil(Math.sqrt(dx * dx + dz * dz));
    }

    private static void include(int[] box, List<ColumnMask.Cell> cells) {
        for (ColumnMask.Cell cell : cells) {
            box[0] = Math.min(box[0], cell.x());
            box[1] = Math.min(box[1], cell.z());
            box[2] = Math.max(box[2], cell.x());
            box[3] = Math.max(box[3], cell.z());
        }
    }
}
