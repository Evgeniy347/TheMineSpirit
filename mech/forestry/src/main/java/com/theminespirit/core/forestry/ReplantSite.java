package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class ReplantSite {
    public static final Set<String> GREEN_PLOTS = Set.of("orchard");

    private ReplantSite() {}

    public record Plot(String id, int x, int z, int width, int depth) {
        public Plot {
            Objects.requireNonNull(id, "id места не может быть null: по нему решают, сад это или дом");
            if (width < 1 || depth < 1) {
                throw new IllegalArgumentException(
                        "Место '" + id + "' " + width + "×" + depth + ": стороны участка положительны");
            }
        }

        public boolean covers(int px, int pz) {
            return px >= x && px < x + width && pz >= z && pz < z + depth;
        }
    }

    @FunctionalInterface
    public interface Streets {
        boolean contains(int x, int z);
    }

    public record Plan(List<Plot> plots, Streets streets) {
        public Plan {
            plots = List.copyOf(Objects.requireNonNull(plots, "Места построек плана не могут быть null"));
            Objects.requireNonNull(streets, "Улицы плана не могут быть null: по ним проверяют проход");
        }
    }

    public static boolean allowed(@Nullable Plan plan, BlockCoord cell) {
        Objects.requireNonNull(cell, "cell не может быть null: саженец сажают в конкретную клетку");
        if (plan == null) {
            return true;
        }
        for (Plot plot : plan.plots()) {
            if (plot.covers(cell.x(), cell.z())) {
                return GREEN_PLOTS.contains(plot.id());
            }
        }
        return !plan.streets().contains(cell.x(), cell.z());
    }
}
