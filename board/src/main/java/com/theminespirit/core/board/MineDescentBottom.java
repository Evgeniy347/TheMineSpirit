package com.theminespirit.core.board;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public final class MineDescentBottom {
    private MineDescentBottom() {}

    public static Optional<BlockCoord> standOf(BlockCoord entrance) {
        Objects.requireNonNull(entrance, "Вход в шахту не может быть null: низ спуска считается от него");
        Optional<Preset> preset = PresetLibrary.current().byId(TicketPlans.MINE_DESCENT_SCHEME);
        if (preset.isEmpty()) {
            return Optional.empty();
        }
        MineLayout layout = MineLayout.place(
                preset.get().schematic(),
                preset.get().entry(),
                entrance,
                BuildRotation.parse(TicketPlans.MINE_DESCENT_ROTATION),
                MineLayout.Extent.NONE);
        if (layout.air().isEmpty()) {
            return Optional.of(layout.entrance());
        }
        return layout.air().stream()
                .min(Comparator.comparingInt(BlockCoord::y).thenComparing(Comparator.naturalOrder()));
    }
}
