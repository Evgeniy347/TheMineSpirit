package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record ChestSpace(int free, int slots) {
    public ChestSpace {
        if (free < 0 || slots < free) {
            throw new IllegalArgumentException("Свободно " + free + " из " + slots
                    + " слотов: свободных не больше всех и не меньше нуля, иначе учёт сундуков сломан");
        }
    }

    public record Spot(BlockCoord pos, int free, int slots) {
        public Spot {
            Objects.requireNonNull(pos, "Позиция сундука не может быть null: по ней отбирается склад поселения");
            if (free < 0 || slots < free) {
                throw new IllegalArgumentException("Сундук " + pos + ": свободно " + free + " из " + slots
                        + " слотов — свободных не больше всех и не меньше нуля");
            }
        }
    }

    public int freePercent() {
        return slots == 0 ? 100 : free * 100 / slots;
    }

    public static ChestSpace of(List<Spot> spots, @Nullable SettlementReach reach) {
        Objects.requireNonNull(spots, "Список сундуков не может быть null: пустой задают пустым списком");
        int free = 0;
        int slots = 0;
        for (Spot spot : spots) {
            if (reach != null && !reach.covers(spot.pos().x(), spot.pos().z())) {
                continue;
            }
            free += spot.free();
            slots += spot.slots();
        }
        return new ChestSpace(free, slots);
    }
}
