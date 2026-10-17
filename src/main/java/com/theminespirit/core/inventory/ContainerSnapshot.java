package com.theminespirit.core.inventory;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record ContainerSnapshot(BlockCoord pos, List<Stack> slots) {
    public ContainerSnapshot {
        Objects.requireNonNull(pos, "Позиция контейнера не может быть null: по ней выбирают ближайший");
        Objects.requireNonNull(slots, "Содержимое контейнера не может быть null: пустой контейнер — список null-слотов");
        // Пустой слот — это null, поэтому List.copyOf (он запрещает null) здесь не подходит.
        slots = Collections.unmodifiableList(new ArrayList<>(slots));
    }

    public boolean hasFreeSlot() {
        return freeSlots() > 0;
    }

    public int freeSlots() {
        int free = 0;
        for (Stack stack : slots) {
            if (stack == null) {
                free++;
            }
        }
        return free;
    }

    public int slotCount() {
        return slots.size();
    }

    public boolean hasPartial(Stack kind) {
        for (Stack stack : slots) {
            if (stack != null && stack.sameKindAs(kind) && stack.freeSpace() > 0) {
                return true;
            }
        }
        return false;
    }

    public boolean hasKind(Stack kind) {
        for (Stack stack : slots) {
            if (stack != null && stack.sameKindAs(kind)) {
                return true;
            }
        }
        return false;
    }
}