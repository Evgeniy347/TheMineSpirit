package com.theminespirit.core.board.audit;

import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.TownPlan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ExpectIndex {

    public interface Schemes {
        Optional<Scheme> scheme(String planId, int level);
    }

    public record Scheme(Schematic schematic, BlockCoord entry) {
        public Scheme {
            Objects.requireNonNull(schematic, "Схема не может быть null: ожидания строятся по ней");
            Objects.requireNonNull(entry, "Вход схемы не может быть null: шахтный разворот начинается с него");
        }
    }

    public record Index(Map<BlockCoord, Expectation> cells, int missingSchemes, int collisions) {
        public Index {
            cells = Collections.unmodifiableMap(new LinkedHashMap<>(cells));
            if (missingSchemes < 0 || collisions < 0) {
                throw new IllegalArgumentException("Индекс ожиданий: пропусков " + missingSchemes + ", коллизий "
                        + collisions + " — счётчики не бывают отрицательными");
            }
        }

        public Optional<Expectation> at(BlockCoord cell) {
            return Optional.ofNullable(cells.get(cell));
        }

        public int size() {
            return cells.size();
        }
    }

    private ExpectIndex() {}

    public static Index of(TownPlan plan, int stage, int levelY, Set<String> mineIds, Schemes schemes) {
        Objects.requireNonNull(plan, "План не может быть null: ожидания строятся по нему");
        Objects.requireNonNull(mineIds, "id шахтных планов не могут быть null: иначе ствол станет постройкой");
        Objects.requireNonNull(schemes, "Схемы не могут быть null: без них постройка неизвестна");
        List<Expectation> taken = new ArrayList<>();
        int missing = BuildingEnvelopes.collect(plan, stage, levelY, mineIds, taken, schemes);
        StreetEnvelopes.collect(plan, levelY, taken);
        missing += FenceEnvelopes.collect(plan, levelY, taken, schemes);
        GroundEnvelopes.collect(plan, stage, levelY, taken);
        Map<BlockCoord, Expectation> cells = new LinkedHashMap<>();
        int collisions = 0;
        for (Expectation expectation : taken) {
            if (cells.putIfAbsent(expectation.cell(), expectation) != null) {
                collisions++;
            }
        }
        return new Index(cells, missing, collisions);
    }
}
