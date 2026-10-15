package com.theminespirit.core.doors;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

public record DoorState(BlockCoord position, boolean open, long openedSinceTick, List<BlockCoord> players) {
    public DoorState {
        Objects.requireNonNull(position, "Дверь без позиции не отследить: закрывать нечего");
        players = List.copyOf(Objects.requireNonNull(players,
                "Список игроков рядом не может быть null: нет игроков — пустой список"));
    }
}
