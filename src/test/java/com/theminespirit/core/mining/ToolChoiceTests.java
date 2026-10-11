package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.mining.ToolChoice.Choice;
import com.theminespirit.core.mining.ToolChoice.ToolOption;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolChoiceTests {
    @Test
    void best_PickaxeAndAxeOnStone_ChoosesPickaxe() {
        Choice choice = ToolChoice.best(List.of(
                new ToolOption(3, 4.0, false),
                new ToolOption(5, 6.0, true)));

        assertEquals(new Choice(5, 6.0, true), choice,
                "Для камня выбирается кирка (правильный инструмент), даже если топор в слоте раньше: "
                        + "топор не даёт дроп с камня");
    }

    @Test
    void best_TwoCorrectTools_ChoosesFasterOne() {
        Choice choice = ToolChoice.best(List.of(
                new ToolOption(0, 2.0, true),
                new ToolOption(1, 6.0, true),
                new ToolOption(2, 4.0, true)));

        assertEquals(new Choice(1, 6.0, true), choice,
                "Из правильных инструментов берётся самый быстрый: деревянная кирка не должна обгонять железную");
    }

    @Test
    void best_EqualSpeed_ChoosesLowerSlot() {
        Choice choice = ToolChoice.best(List.of(
                new ToolOption(7, 6.0, true),
                new ToolOption(2, 6.0, true),
                new ToolOption(9, 6.0, true)));

        assertEquals(2, choice.slot(),
                "При равной скорости выбирается меньший слот: выбор обязан быть детерминированным при любом порядке списка");
    }

    @Test
    void best_ToolSlowerThanHand_FallsBackToHand() {
        Choice choice = ToolChoice.best(List.of(new ToolOption(4, 1.0, true), new ToolOption(6, 0.5, true)));

        assertEquals(new Choice(ToolChoice.HAND, 1.0, true), choice,
                "Инструмент не быстрее руки не выбирается: износ предмета ради нулевого выигрыша бессмыслен");
    }

    @Test
    void best_NoCorrectToolForHardBlock_ReturnsHandWithoutDrops() {
        Choice choice = ToolChoice.best(List.of(new ToolOption(0, 1.0, false), new ToolOption(1, 4.0, false)));

        assertEquals(new Choice(ToolChoice.HAND, 1.0, false), choice,
                "Нет правильного инструмента для блока, требующего его: ломка рукой без дропа, как у игрока");
    }

    @Test
    void best_EmptyOptions_ReturnsHandWithoutDrops() {
        assertEquals(new Choice(ToolChoice.HAND, 1.0, false), ToolChoice.best(List.of()),
                "Пустой набор вариантов не содержит правильного инструмента: рука без дропа");
    }

    @Test
    void best_HandIsCorrectForSoftBlock_ReturnsHandWithDrops() {
        Choice choice = ToolChoice.best(List.of(new ToolOption(ToolChoice.HAND, 1.0, true),
                new ToolOption(3, 1.0, true)));

        assertEquals(new Choice(ToolChoice.HAND, 1.0, true), choice,
                "Блок, не требующий инструмента, ломается рукой с дропом: земля и дерево не должны терять предметы");
    }
}
