package com.theminespirit.core.building;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Лишнее в инвентаре на стройке (решение владельца 20.10): пока есть материалы — строим, а предметы, не нужные
 * оставшимся клеткам схемы (дроп мобов, мусор), освобождают место перед заходом за порцией. Иначе забитый
 * подобранным инвентарь не берёт порцию, и стройка кончается недостроенной.
 */
public final class BuildClutter {
    private BuildClutter() {}

    // Слоты к освобождению: предмет в слоте не нужен стройке и слот не из keep (набор духа: инструменты, еда — в
    // обычной игре их не отдают). slots — id предмета по слоту, null — пустой слот.
    public static List<Integer> junk(List<@Nullable String> slots, Set<String> needed, Set<Integer> keep) {
        Objects.requireNonNull(slots, "Слоты инвентаря не могут быть null: пустой слот задаётся null в списке");
        Objects.requireNonNull(needed, "Нужное стройке не может быть null: пустое задаётся пустым множеством");
        Objects.requireNonNull(keep, "Сохраняемые слоты не могут быть null: пустое задаётся пустым множеством");
        List<Integer> junk = new ArrayList<>();
        for (int slot = 0; slot < slots.size(); slot++) {
            String item = slots.get(slot);
            if (item != null && !needed.contains(item) && !keep.contains(slot)) {
                junk.add(slot);
            }
        }
        return List.copyOf(junk);
    }

    // Сколько лишнего освободить перед заходом: только когда свободных слотов нет (заход упал бы), и столько,
    // сколько стопок в порции (stacksWanted), не больше, чем лишнего. Есть хоть один свободный — заход идёт порцией
    // в свободные слоты, лишнее не трогается (решение владельца 20.10 о заходах с двумя свободными слотами).
    public static List<Integer> toRelease(List<Integer> junk, int freeSlots, int stacksWanted) {
        Objects.requireNonNull(junk, "Лишнее не может быть null: пустое задаётся пустым списком");
        if (freeSlots > 0) {
            return List.of();
        }
        int missing = Math.max(0, stacksWanted);
        return List.copyOf(junk.subList(0, Math.min(junk.size(), missing)));
    }
}
