package com.theminespirit.neoforge.intellect;

import com.theminespirit.core.board.StockView;
import com.theminespirit.core.intellect.DescentKit;
import com.theminespirit.core.intellect.OutsideRules;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.neoforge.inventory.InventoryTables;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ExpeditionKit {
    private ExpeditionKit() {}

    public static List<String> missing(List<? extends ExpeditionMember> team, StockView stock) {
        Objects.requireNonNull(team, "Команда похода не может быть null: пустой состав задают пустым списком");
        Objects.requireNonNull(stock, "Запасы поселения не могут быть null: пустые задают пустой картой");
        List<Stack> inventory = new ArrayList<>();
        for (ExpeditionMember spirit : team) {
            inventory.addAll(present(spirit.inventory()));
        }
        stock.available().forEach((item, count) -> {
            for (int left = count; left > 0; left -= 64) {
                inventory.add(new Stack(item, 0, Math.min(left, 64), 64));
            }
        });
        return DescentKit.missing(OutsideRules.loadDefault().mineKit(), inventory, InventoryTables.values());
    }

    static List<Stack> present(List<Stack> stacks) {
        Objects.requireNonNull(stacks, "Инвентарь духа не может быть null: пустой задают пустым списком");
        List<Stack> present = new ArrayList<>(stacks.size());
        for (Stack stack : stacks) {
            if (stack != null) {
                present.add(stack);
            }
        }
        return List.copyOf(present);
    }
}
