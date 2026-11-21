package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.ToolChoice.Choice;
import com.theminespirit.core.mining.ToolChoice.ToolOption;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolChoiceTests {
    static final ToolReserve RESERVE = ToolReserve.defaults();
    static final List<String> REPAIR_TIERS = List.of("iron", "diamond");

    @Test
    void slots_HandAndMainHand_AreMinusOneAndMinusTwo() {
        assertEquals(
                -1,
                ToolChoice.HAND,
                "Слот «рука» в выборе инструмента равен -1: на нём держится разбор выбора"
                        + " Иначе тест slots_HandAndMainHand_AreMinusOneAndMinusTwo перестанет ловить эту поломку. При смене slots_HandAndMainHand_AreMinusOneAndMinusTwo проверь это утверждение.");
        assertEquals(
                -2,
                ToolChoice.MAIN_HAND,
                "Слот основной руки равен -2 (рядом с рукой -1): при смене значения ломка путает основную руку "
                        + "с инвентарём, и разбор выбора нужно пересмотреть"
                        + " Иначе тест slots_HandAndMainHand_AreMinusOneAndMinusTwo перестанет ловить эту поломку. При смене slots_HandAndMainHand_AreMinusOneAndMinusTwo проверь это утверждение.");
    }

    @Test
    void best_PickaxeAndAxeOnStone_ChoosesPickaxe() {
        Choice choice = ToolChoice.best(List.of(ToolOption.of(3, 4.0, false), ToolOption.of(5, 6.0, true)));

        assertEquals(
                new Choice(5, 6.0, true),
                choice,
                "Для камня выбирается кирка (правильный инструмент), даже если топор в слоте раньше: "
                        + "топор не даёт дроп с камня"
                        + " Иначе тест best_PickaxeAndAxeOnStone_ChoosesPickaxe перестанет ловить эту поломку. При смене best_PickaxeAndAxeOnStone_ChoosesPickaxe проверь это утверждение.");
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
                        + "иначе надетая кирка игнорировалась бы и дух ломал бы рукой при кирке в руке"
                        + " При смене best_MainHandTool_CompetesAsOption проверь это утверждение.");
    }

    @Test
    void best_TwoCorrectTools_ChoosesFasterOne() {
        Choice choice = ToolChoice.best(
                List.of(ToolOption.of(0, 2.0, true), ToolOption.of(1, 6.0, true), ToolOption.of(2, 4.0, true)));

        assertEquals(
                new Choice(1, 6.0, true),
                choice,
                "Из правильных инструментов берётся самый быстрый: деревянная кирка не должна обгонять железную"
                        + " Иначе тест best_TwoCorrectTools_ChoosesFasterOne перестанет ловить эту поломку. При смене best_TwoCorrectTools_ChoosesFasterOne проверь это утверждение.");
    }

    @Test
    void best_EqualSpeed_ChoosesLowerSlot() {
        Choice choice = ToolChoice.best(
                List.of(ToolOption.of(7, 6.0, true), ToolOption.of(2, 6.0, true), ToolOption.of(9, 6.0, true)));

        assertEquals(
                2,
                choice.slot(),
                "При равной скорости выбирается меньший слот: выбор обязан быть детерминированным при любом порядке списка"
                        + " Иначе тест best_EqualSpeed_ChoosesLowerSlot перестанет ловить эту поломку. При смене best_EqualSpeed_ChoosesLowerSlot проверь это утверждение.");
    }

    @Test
    void best_ToolSlowerThanHand_FallsBackToHand() {
        Choice choice = ToolChoice.best(List.of(ToolOption.of(4, 1.0, true), ToolOption.of(6, 0.5, true)));

        assertEquals(
                new Choice(ToolChoice.HAND, 1.0, true),
                choice,
                "Инструмент не быстрее руки не выбирается: износ предмета ради нулевого выигрыша бессмыслен"
                        + " Иначе тест best_ToolSlowerThanHand_FallsBackToHand перестанет ловить эту поломку. При смене best_ToolSlowerThanHand_FallsBackToHand проверь это утверждение.");
    }

    @Test
    void best_NoCorrectToolForHardBlock_ReturnsHandWithoutDrops() {
        Choice choice = ToolChoice.best(List.of(ToolOption.of(0, 1.0, false), ToolOption.of(1, 4.0, false)));

        assertEquals(
                new Choice(ToolChoice.HAND, 1.0, false),
                choice,
                "Нет правильного инструмента для блока, требующего его: ломка рукой без дропа, как у игрока"
                        + " Иначе тест best_NoCorrectToolForHardBlock_ReturnsHandWithoutDrops перестанет ловить эту поломку. При смене best_NoCorrectToolForHardBlock_ReturnsHandWithoutDrops проверь это утверждение.");
    }

    @Test
    void best_EmptyOptions_ReturnsHandWithoutDrops() {
        assertEquals(
                new Choice(ToolChoice.HAND, 1.0, false),
                ToolChoice.best(List.of()),
                "Пустой набор вариантов не содержит правильного инструмента: рука без дропа"
                        + " Иначе тест best_EmptyOptions_ReturnsHandWithoutDrops перестанет ловить эту поломку. При смене best_EmptyOptions_ReturnsHandWithoutDrops проверь это утверждение.");
    }

    @Test
    void best_HandIsCorrectForSoftBlock_ReturnsHandWithDrops() {
        Choice choice =
                ToolChoice.best(List.of(ToolOption.of(ToolChoice.HAND, 1.0, true), ToolOption.of(3, 1.0, true)));

        assertEquals(
                new Choice(ToolChoice.HAND, 1.0, true),
                choice,
                "Блок, не требующий инструмента, ломается рукой с дропом: земля и дерево не должны терять предметы"
                        + " При смене best_HandIsCorrectForSoftBlock_ReturnsHandWithDrops проверь это утверждение.");
    }

    @Test
    void best_SpentToolWithHealthySpare_ChoosesSpare() {
        Choice choice = ToolChoice.best(
                List.of(
                        new ToolOption(0, 6.0, true, 1, 251, false, "minecraft:iron_pickaxe"),
                        new ToolOption(1, 6.0, true, 251, 251, false, "minecraft:iron_pickaxe")),
                RESERVE,
                REPAIR_TIERS);

        assertEquals(
                1,
                choice.slot(),
                "Изношенная кирка (1 удар, порог 13) уступает целой такой же из инвентаря: иначе дух тратил бы "
                        + "последние удары и работал голыми руками"
                        + " При смене best_SpentToolWithHealthySpare_ChoosesSpare проверь это утверждение.");
    }

    @Test
    void best_OnlySpentCorrectTool_ReturnsHandWithoutDrops() {
        Choice choice = ToolChoice.best(
                List.of(new ToolOption(0, 6.0, true, 2, 251, false, "minecraft:iron_pickaxe")), RESERVE, REPAIR_TIERS);

        assertEquals(
                new Choice(ToolChoice.HAND, ToolChoice.HAND_SPEED, false),
                choice,
                "Единственный подходящий инструмент износился: замена нужна, но её нет — рука без дропа, "
                        + "а не трата последних ударов"
                        + " Иначе тест best_OnlySpentCorrectTool_ReturnsHandWithoutDrops перестанет ловить эту поломку. При смене best_OnlySpentCorrectTool_ReturnsHandWithoutDrops проверь это утверждение.");
    }

    @Test
    void best_HealthySlowerTool_BeatsSpentFasterTool() {
        Choice choice = ToolChoice.best(
                List.of(
                        new ToolOption(0, 8.0, true, 1, 251, false, "minecraft:iron_pickaxe"),
                        new ToolOption(1, 4.0, true, 251, 251, false, "minecraft:iron_pickaxe")),
                RESERVE,
                REPAIR_TIERS);

        assertEquals(
                1,
                choice.slot(),
                "Изношенная быстрая кирка не выбирается, даже если целая медленнее: износ важнее скорости"
                        + " Иначе тест best_HealthySlowerTool_BeatsSpentFasterTool перестанет ловить эту поломку. При смене best_HealthySlowerTool_BeatsSpentFasterTool проверь это утверждение.");
    }

    @Test
    void best_EnchantedToolOnLastUse_IsTreatedAsSpent() {
        ToolOption enchanted = new ToolOption(0, 6.0, true, 1, 100, true, "minecraft:iron_pickaxe");

        assertTrue(
                enchanted.spent(RESERVE),
                "Зачарованный инструмент с последним ударом считается изношенным: последний удар тратить нельзя, "
                        + "иначе зачарование потеряется вместе с предметом"
                        + " При смене best_EnchantedToolOnLastUse_IsTreatedAsSpent проверь это утверждение.");
    }

    @Test
    void best_UnwearingTool_NeverSpent() {
        ToolOption unwearing = ToolOption.of(0, 6.0, true);

        assertFalse(
                unwearing.spent(RESERVE),
                "Инструмент без прочности (maxUses=0) не изнашивается: его нельзя объявлять изношенным"
                        + " Иначе тест best_UnwearingTool_NeverSpent перестанет ловить эту поломку. При смене best_UnwearingTool_NeverSpent проверь это утверждение.");
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
                        false,
                        ""),
                new ToolOption(0, ToolChoice.HAND_SPEED, true, 1, 100, false, "minecraft:iron_shovel"));

        assertFalse(
                ToolChoice.shouldRefuseForLackOfTool(ToolChoice.best(options, RESERVE, REPAIR_TIERS), options),
                "Изношенный инструмент на мягком блоке (не требует инструмента) — не повод для отказа: "
                        + "рука даёт дроп, иначе дух бросал бы землю и брёвна из-за износа лопаты или топора"
                        + " При смене shouldRefuseForLackOfTool_SoftBlockWithSpentTool_IsFalse проверь это утверждение.");
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
                        false,
                        ""),
                new ToolOption(0, 6.0, true, 2, 251, false, "minecraft:iron_pickaxe"));

        assertTrue(
                ToolChoice.shouldRefuseForLackOfTool(ToolChoice.best(options, RESERVE, REPAIR_TIERS), options),
                "Правильный инструмент есть, но изношен, и рука без дропа: отказ с причиной, "
                        + "а не ломка голыми руками и потеря блока"
                        + " Иначе тест shouldRefuseForLackOfTool_HardBlockAllCorrectSpent_IsTrue перестанет ловить эту поломку. При смене shouldRefuseForLackOfTool_HardBlockAllCorrectSpent_IsTrue проверь это утверждение.");
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
                        false,
                        ""),
                new ToolOption(0, 4.0, false, 251, 251, false, "minecraft:iron_pickaxe"));

        assertFalse(
                ToolChoice.shouldRefuseForLackOfTool(ToolChoice.best(options, RESERVE, REPAIR_TIERS), options),
                "Правильного инструмента нет вовсе: это забота проверки уровня руды, а не отказа «нужен инструмент»"
                        + " Иначе тест shouldRefuseForLackOfTool_HardBlockNoCorrectTool_IsFalse перестанет ловить эту поломку. При смене shouldRefuseForLackOfTool_HardBlockNoCorrectTool_IsFalse проверь это утверждение.");
    }

    @Test
    void best_WoodenPickaxeOneUseLeft_ChosenOverHand() {
        Choice choice = ToolChoice.best(
                List.of(new ToolOption(0, 4.0, true, 1, 59, false, "minecraft:wooden_pickaxe")), RESERVE, REPAIR_TIERS);

        assertEquals(
                0,
                choice.slot(),
                "Деревянная кирка с последним ударом выбирается для работы: дерево тратят до поломки, а не берегут"
                        + " Иначе тест best_WoodenPickaxeOneUseLeft_ChosenOverHand перестанет ловить эту поломку. При смене best_WoodenPickaxeOneUseLeft_ChosenOverHand проверь это утверждение.");
    }

    @Test
    void best_IronPickaxeAtThreshold_DeferredToHand() {
        Choice choice = ToolChoice.best(
                List.of(new ToolOption(0, 6.0, true, 3, 251, false, "minecraft:iron_pickaxe")), RESERVE, REPAIR_TIERS);

        assertEquals(
                new Choice(ToolChoice.HAND, ToolChoice.HAND_SPEED, false),
                choice,
                "Железная кирка на пороге износа откладывается под ремонт: рука без дропа вместо траты запаса прочности"
                        + " Иначе тест best_IronPickaxeAtThreshold_DeferredToHand перестанет ловить эту поломку. При смене best_IronPickaxeAtThreshold_DeferredToHand проверь это утверждение.");
    }

    @Test
    void best_StoneShovelOneUseLeft_ChosenOverHand() {
        Choice choice = ToolChoice.best(
                List.of(new ToolOption(0, 2.0, true, 1, 131, false, "minecraft:stone_shovel")), RESERVE, REPAIR_TIERS);

        assertEquals(
                0,
                choice.slot(),
                "Каменная лопата с последним ударом выбирается для работы: камень тратят до поломки, а не берегут"
                        + " Иначе тест best_StoneShovelOneUseLeft_ChosenOverHand перестанет ловить эту поломку. При смене best_StoneShovelOneUseLeft_ChosenOverHand проверь это утверждение.");
    }

    @Test
    void refusesHandDig_PlannedPickaxeBlockWithoutPick_Allows() {
        Choice hand = new Choice(ToolChoice.HAND, ToolChoice.HAND_SPEED, false);

        assertEquals(
                false,
                ToolChoice.refusesHandDig(hand, List.of(), true, ToolChoice.DigMode.PLANNED),
                "плановый прокоп спуска останавливать нельзя: без кирки руда/сланец режутся руками без дропа, "
                        + "иначе шахта стоит до кирки, которой у духа может не быть"
                        + " При смене refusesHandDig_PlannedPickaxeBlockWithoutPick_Allows проверь это утверждение.");
    }
}
