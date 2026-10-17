package com.theminespirit.core.social;

import com.theminespirit.core.inventory.BaseKit;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TransferSource(UUID spirit, BlockCoord at, List<Stack> inventory, BaseKit kit,
        List<String> taskNeeds) {
    public TransferSource {
        Objects.requireNonNull(spirit, "Дух-источник не может быть null: передача идёт от конкретного духа");
        Objects.requireNonNull(at, "Точка источника не может быть null: по ней считается близость к получателю");
        Objects.requireNonNull(inventory, "Инвентарь источника не может быть null: излишек считается по слотам");
        Objects.requireNonNull(kit, "Базовый набор не может быть null: без него норма не определена");
        Objects.requireNonNull(taskNeeds, "Нужды задачи не могут быть null: пустые задают пустым списком");
        inventory = Collections.unmodifiableList(new ArrayList<>(inventory));
        taskNeeds = List.copyOf(taskNeeds);
    }
}
