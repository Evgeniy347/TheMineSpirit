package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CarriedItemsTests {
    @Test
    void of_PickaxeOwnedInMainHand_CountedForToolGate() {
        List<String> items = CarriedItems.of(List.of("minecraft:cobblestone"), Optional.of("minecraft:stone_pickaxe"));

        assertTrue(
                ToolGate.allows(BoardCase.BUILD_MINE, items),
                "Кирка в своей руке — кирка духа (INC-94: запасная после износа уходит в руку, учёт видел только "
                        + "инвентарь и ставил «кирки нет ни у одного духа»), учтено " + items
                        + " Иначе тест of_PickaxeOwnedInMainHand_CountedForToolGate перестанет ловить эту поломку. При смене of_PickaxeOwnedInMainHand_CountedForToolGate проверь это утверждение.");
    }

    @Test
    void of_EmptyHand_OnlyInventory() {
        assertEquals(
                List.of("minecraft:oak_log"),
                CarriedItems.of(List.of("minecraft:oak_log"), Optional.empty()),
                "Пустая или чужая (показанная копия) рука ничего не добавляет: иначе вещь из инвентаря считается "
                        + "дважды" + " При смене of_EmptyHand_OnlyInventory проверь это утверждение.");
    }
}
