package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class CarriedItems {
    private CarriedItems() {}

    public static List<String> of(List<String> inventory, Optional<String> ownedMainHand) {
        Objects.requireNonNull(inventory, "Инвентарь не может быть null: пустой — пустой список");
        Objects.requireNonNull(ownedMainHand, "Своя вещь в руке не может быть null: пустая рука — Optional.empty()");
        List<String> ids = new ArrayList<>(inventory);
        ownedMainHand.ifPresent(ids::add);
        return List.copyOf(ids);
    }
}
