package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.ToolChoice.Choice;
import com.theminespirit.core.mining.ToolChoice.ToolOption;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolChoiceTests {
    private static final ToolReserve RESERVE = ToolReserve.defaults();

    @Test
    void best_PickaxeAndAxeOnStone_ChoosesPickaxe() {
        Choice choice = ToolChoice.best(List.of(ToolOption.of(3, 4.0, false), ToolOption.of(5, 6.0, true)));

        assertEquals(
                new Choice(5, 6.0, true),
                choice,
                "Для камня выбирается кирка (правильный инструмент), даже если топор в слоте раньше: "
                        + "топор не даёт дроп с камня");
    }

    @Test
    void best_MainHandTool_CompetesAsOption() {
        Choice choice = ToolChoice.best(List.of(
                ToolOption.of(ToolChoice.HAND, ToolChoice.HAND_SPEED, true),
                ToolOption.of(ToolChoice.MAIN_HAND, 6.0, true),
                ToolOption.of(3, 4.0, true)));

        assertEquals(
                new Choice(ToolChoice.MAIN_HAND, 6.0, true),
                choice,
                "Кирка в основной руке обязана участвовать в выборе инструмента наравне с инвентарём: "
                        + "иначе надетая кирка игнорировалась бы и дух ломал бы рукой при кирке в руке");
    }

    @Test
    void best_TwoCorrectTools_ChoosesFasterOne() {
        Choice choice = ToolChoice.best(
                List.of(ToolOption.of(0, 2.0, true), ToolOption.of(1, 6.0, true), ToolOption.of(2, 4.0, true)));

        assertEquals(
                new Choice(1, 6.0, true),
                choice,
                "Из правильных инструментов берётся самый быстрый: деревянная кирка не должна обгонять железную");
    }

    @Test
    void best_EqualSpeed_ChoosesLowerSlot() {
        Choice choice = ToolChoice.best(
                List.of(ToolOption.of(7, 6.0, true), ToolOption.of(2, 6.0, true), ToolOption.of(9, 6.0, true)));

        assertEquals(
                2,
                choice.slot(),
                "При равной скорости выбирается меньший слот: выбор обязан быть детерминированным при любом порядке списка");
    }

    @Test
    void best_ToolSlowerThanHand_FallsBackToHand() {
        Choice choice = ToolChoice.best(List.of(ToolOption.of(4, 1.0, true), ToolOption.of(6, 0.5, true)));

        assertEquals(
                new Choice(ToolChoice.HAND, 1.0, true),
                choice,
                "Инструмент не быстрее руки не выбирается: износ предмета ради нулевого выигрыша бессмыслен");
    }

    @Test
    void best_NoCorrectToolForHardBlock_ReturnsHandWithoutDrops() {
        Choice choice = ToolChoice.best(List.of(ToolOption.of(0, 1.0, false), ToolOption.of(1, 4.0, false)));

        assertEquals(
                new Choice(ToolChoice.HAND, 1.0, false),
                choice,
                "Нет правильного инструмента для блока, требующего его: ломка рукой без дропа, как у игрока");
    }

    @Test
    void best_EmptyOptions_ReturnsHandWithoutDrops() {
        assertEquals(
                new Choice(ToolChoice.HAND, 1.0, false),
                ToolChoice.best(List.of()),
                "Пустой набор вариантов не содержит правильного инструмента: рука без дропа");
    }

    @Test
    void best_HandIsCorrectForSoftBlock_ReturnsHandWithDrops() {
        Choice choice =
                ToolChoice.best(List.of(ToolOption.of(ToolChoice.HAND, 1.0, true), ToolOption.of(3, 1.0, true)));

        assertEquals(
                new Choice(ToolChoice.HAND, 1.0, true),
                choice,
                "Блок, не требующий инструмента, ломается рукой с дропом: земля и дерево не должны терять предметы");
    }

    @Test
    void best_SpentToolWithHealthySpare_ChoosesSpare() {
        Choice choice = ToolChoice.best(
                List.of(new ToolOption(0, 6.0, true, 1, 251, false), new ToolOption(1, 6.0, true, 251, 251, false)),
                RESERVE);

        assertEquals(
                1,
                choice.slot(),
                "Изношенная кирка (1 удар, порог 13) уступает целой такой же из инвентаря: иначе дух тратил бы "
                        + "последние удары и работал голыми руками");
    }

    @Test
    void best_OnlySpentCorrectTool_ReturnsHandWithoutDrops() {
        Choice choice = ToolChoice.best(List.of(new ToolOption(0, 6.0, true, 2, 251, false)), RESERVE);

        assertEquals(
                new Choice(ToolChoice.HAND, ToolChoice.HAND_SPEED, false),
                choice,
                "Единственный подходящий инструмент износился: замена нужна, но её нет — рука без дропа, "
                        + "а не трата последних ударов");
    }

    @Test
    void best_HealthySlowerTool_BeatsSpentFasterTool() {
        Choice choice = ToolChoice.best(
                List.of(new ToolOption(0, 8.0, true, 1, 251, false), new ToolOption(1, 4.0, true, 251, 251, false)),
                RESERVE);

        assertEquals(
                1,
                choice.slot(),
                "Изношенная быстрая кирка не выбирается, даже если целая медленнее: износ важнее скорости");
    }

    @Test
    void best_EnchantedToolOnLastUse_IsTreatedAsSpent() {
        ToolOption enchanted = new ToolOption(0, 6.0, true, 1, 100, true);

        assertTrue(
                enchanted.spent(RESERVE),
                "Зачарованный инструмент с последним ударом считается изношенным: последний удар тратить нельзя, "
                        + "иначе зачарование потеряется вместе с предметом");
    }

    @Test
    void best_UnwearingTool_NeverSpent() {
        ToolOption unwearing = ToolOption.of(0, 6.0, true);

        assertFalse(
                unwearing.spent(RESERVE),
                "Инструмент без прочности (maxUses=0) не изнашивается: его нельзя объявлять изношенным");
    }

    @Test
    void shouldRefuseForLackOfTool_SoftBlockWithSpentTool_IsFalse() {
        List<ToolOption> options = List.of(
                new ToolOption(
                        ToolChoice.HAND,
                        ToolChoice.HAND_SPEED,
                        true,
                        ToolChoice.UNWEARING,
                        ToolChoice.UNWEARING,
                        false),
                new ToolOption(0, ToolChoice.HAND_SPEED, true, 1, 100, false));

        assertFalse(
                ToolChoice.shouldRefuseForLackOfTool(ToolChoice.best(options, RESERVE), options),
                "Изношенный инструмент на мягком блоке (не требует инструмента) — не повод для отказа: "
                        + "рука даёт дроп, иначе дух бросал бы землю и брёвна из-за износа лопаты или топора");
    }

    @Test
    void shouldRefuseForLackOfTool_HardBlockAllCorrectSpent_IsTrue() {
        List<ToolOption> options = List.of(
                new ToolOption(
                        ToolChoice.HAND,
                        ToolChoice.HAND_SPEED,
                        false,
                        ToolChoice.UNWEARING,
                        ToolChoice.UNWEARING,
                        false),
                new ToolOption(0, 6.0, true, 2, 251, false));

        assertTrue(
                ToolChoice.shouldRefuseForLackOfTool(ToolChoice.best(options, RESERVE), options),
                "Правильный инструмент есть, но изношен, и рука без дропа: отказ с причиной, "
                        + "а не ломка голыми руками и потеря блока");
    }

    @Test
    void shouldRefuseForLackOfTool_HardBlockNoCorrectTool_IsFalse() {
        List<ToolOption> options = List.of(
                new ToolOption(
                        ToolChoice.HAND,
                        ToolChoice.HAND_SPEED,
                        false,
                        ToolChoice.UNWEARING,
                        ToolChoice.UNWEARING,
                        false),
                new ToolOption(0, 4.0, false, 251, 251, false));

        assertFalse(
                ToolChoice.shouldRefuseForLackOfTool(ToolChoice.best(options, RESERVE), options),
                "Правильного инструмента нет вовсе: это забота проверки уровня руды, а не отказа «нужен инструмент»");
    }
}
