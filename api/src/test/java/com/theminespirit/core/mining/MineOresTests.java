package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.theminespirit.core.mining.api.MineOres;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MineOresTests {
    private static final MineOres.Pick PLAIN_IRON = new MineOres.Pick(0, true, 0, false);
    private static final MineOres.Pick WOODEN = new MineOres.Pick(1, false, 0, false);
    private static final MineOres.Pick FORTUNE_TWO = new MineOres.Pick(4, true, 2, false);
    private static final MineOres.Pick FORTUNE_THREE = new MineOres.Pick(5, true, 3, false);
    private static final MineOres.Pick SILK = new MineOres.Pick(6, true, 0, true);

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "minecraft:diamond_ore",
                "minecraft:deepslate_diamond_ore",
                "minecraft:lapis_ore",
                "minecraft:deepslate_lapis_ore",
                "minecraft:emerald_ore",
                "minecraft:deepslate_emerald_ore"
            })
    void kindOf_LuckOre_IsLuck(String block) {
        assertEquals(
                MineOres.Kind.LUCK,
                MineOres.kindOf(block),
                "алмаз, лазурит и изумруд — руды на удачу: без Удачи их выход теряется навсегда; смена списка — "
                        + "решение владельца 22.10"
                        + " Иначе тест kindOf_LuckOre_IsLuck перестанет ловить эту поломку. При смене kindOf_LuckOre_IsLuck проверь это утверждение.");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "minecraft:coal_ore",
                "minecraft:iron_ore",
                "minecraft:deepslate_copper_ore",
                "minecraft:gold_ore",
                "minecraft:redstone_ore"
            })
    void kindOf_CommonOre_IsOrdinary(String block) {
        assertEquals(
                MineOres.Kind.ORDINARY,
                MineOres.kindOf(block),
                "уголь, железо, медь, золото и красный камень добываются сразу подходящей киркой — "
                        + "иначе стройка шахты ждёт Удачи ради дешёвой руды"
                        + " При смене kindOf_CommonOre_IsOrdinary проверь это утверждение.");
    }

    @Test
    void kindOf_Stone_IsNone() {
        assertEquals(
                MineOres.Kind.NONE,
                MineOres.kindOf("minecraft:stone"),
                "камень — порода, а не руда"
                        + " Иначе тест kindOf_Stone_IsNone перестанет ловить эту поломку. При смене kindOf_Stone_IsNone проверь это утверждение.");
    }

    @Test
    void decide_LuckOreWithPlainPickOnly_LeavesBlock() {
        MineOres.Decision decision = MineOres.decide("minecraft:diamond_ore", List.of(PLAIN_IRON, WOODEN));

        assertInstanceOf(
                MineOres.Leave.class,
                decision,
                "алмаз без кирки с Удачей и без Шёлкового касания обязан остаться в стене: простая кирка даёт один "
                        + "алмаз вместо до четырёх, решение владельца 22.10; фактически " + decision
                        + " Иначе тест decide_LuckOreWithPlainPickOnly_LeavesBlock перестанет ловить эту поломку. При смене decide_LuckOreWithPlainPickOnly_LeavesBlock проверь это утверждение.");
    }

    @Test
    void decide_LuckOreWithFortunePicks_MinesWithHighestFortune() {
        MineOres.Decision decision =
                MineOres.decide("minecraft:lapis_ore", List.of(PLAIN_IRON, FORTUNE_TWO, SILK, FORTUNE_THREE));

        assertEquals(
                OptionalInt.of(FORTUNE_THREE.slot()),
                ((MineOres.Mine) decision).slot(),
                "лазурит добывается киркой с наибольшей Удачей — она даёт больше всего; фактически " + decision
                        + " Иначе тест decide_LuckOreWithFortunePicks_MinesWithHighestFortune перестанет ловить эту поломку. При смене decide_LuckOreWithFortunePicks_MinesWithHighestFortune проверь это утверждение.");
    }

    @Test
    void decide_LuckOreWithSilkTouchOnly_MinesWithSilkTouch() {
        MineOres.Decision decision = MineOres.decide("minecraft:deepslate_diamond_ore", List.of(PLAIN_IRON, SILK));

        assertEquals(
                OptionalInt.of(SILK.slot()),
                ((MineOres.Mine) decision).slot(),
                "без Удачи алмаз берётся Шёлковым касанием целиком — блок на склад до кирки с Удачей; фактически "
                        + decision
                        + " Иначе тест decide_LuckOreWithSilkTouchOnly_MinesWithSilkTouch перестанет ловить эту поломку. При смене decide_LuckOreWithSilkTouchOnly_MinesWithSilkTouch проверь это утверждение.");
    }

    @Test
    void decide_LuckOreWithSilkTouchOfWrongTier_LeavesBlock() {
        MineOres.Pick woodenSilk = new MineOres.Pick(2, false, 0, true);

        assertInstanceOf(
                MineOres.Leave.class,
                MineOres.decide("minecraft:diamond_ore", List.of(woodenSilk)),
                "Шёлковое касание на деревянной кирке не снимает алмазную руду — ломка дала бы пустоту, руда остаётся"
                        + " Иначе тест decide_LuckOreWithSilkTouchOfWrongTier_LeavesBlock перестанет ловить эту поломку. При смене decide_LuckOreWithSilkTouchOfWrongTier_LeavesBlock проверь это утверждение.");
    }

    @Test
    void decide_OrdinaryOreWithCorrectPick_MinesWithAnyTool() {
        MineOres.Decision decision = MineOres.decide("minecraft:iron_ore", List.of(WOODEN, PLAIN_IRON));

        assertEquals(
                OptionalInt.empty(),
                ((MineOres.Mine) decision).slot(),
                "обычная руда добывается подходящей киркой, выбор инструмента — за ломкой; фактически " + decision
                        + " Иначе тест decide_OrdinaryOreWithCorrectPick_MinesWithAnyTool перестанет ловить эту поломку. При смене decide_OrdinaryOreWithCorrectPick_MinesWithAnyTool проверь это утверждение.");
    }

    @Test
    void decide_OrdinaryOreWithoutCorrectPick_LeavesBlock() {
        assertInstanceOf(
                MineOres.Leave.class,
                MineOres.decide("minecraft:iron_ore", List.of(WOODEN)),
                "железо деревянной киркой не добывается — ломка уничтожила бы руду, её оставляют до каменной кирки"
                        + " Иначе тест decide_OrdinaryOreWithoutCorrectPick_LeavesBlock перестанет ловить эту поломку. При смене decide_OrdinaryOreWithoutCorrectPick_LeavesBlock проверь это утверждение.");
    }

    @Test
    void decide_Stone_MinesWithAnyTool() {
        assertInstanceOf(
                MineOres.Mine.class,
                MineOres.decide("minecraft:stone", List.of()),
                "порода в проходе ломается всегда — решает только наличие инструмента у ломки"
                        + " При смене decide_Stone_MinesWithAnyTool проверь это утверждение.");
    }

    @Test
    void decideDescent_OrdinaryOreWithoutCorrectPick_MinesThrough() {
        MineOres.Decision decision = MineOres.decideDescent("minecraft:copper_ore", List.of(WOODEN));

        assertInstanceOf(
                MineOres.Mine.class,
                decision,
                "спуск идёт насквозь через любую руду, кроме алмаза: обходы и остановки на меди оставлены "
                        + "решением владельца 19.11; фактически " + decision
                        + " Иначе тест decideDescent_OrdinaryOreWithoutCorrectPick_MinesThrough перестанет ловить эту поломку. При смене decideDescent_OrdinaryOreWithoutCorrectPick_MinesThrough проверь это утверждение.");
    }

    @Test
    void decideDescent_LuckOreWithoutFortuneOtherThanDiamond_MinesThrough() {
        MineOres.Decision decision = MineOres.decideDescent("minecraft:lapis_ore", List.of(PLAIN_IRON));

        assertInstanceOf(
                MineOres.Mine.class,
                decision,
                "изумруд и лазурит в спуске прокапываются насквозь без Удачи: оставляют только алмаз; фактически "
                        + decision
                        + " Иначе тест decideDescent_LuckOreWithoutFortuneOtherThanDiamond_MinesThrough перестанет ловить эту поломку. При смене decideDescent_LuckOreWithoutFortuneOtherThanDiamond_MinesThrough проверь это утверждение.");
    }

    @Test
    void decideDescent_DiamondWithoutFortune_LeavesBlock() {
        assertInstanceOf(
                MineOres.Leave.class,
                MineOres.decideDescent("minecraft:deepslate_diamond_ore", List.of(PLAIN_IRON)),
                "алмаз без Удачи и Шёлка остаётся в стене и в спуске: терять его выход запрещено; фактически "
                        + MineOres.decideDescent("minecraft:deepslate_diamond_ore", List.of(PLAIN_IRON))
                        + " Иначе тест decideDescent_DiamondWithoutFortune_LeavesBlock перестанет ловить эту поломку. При смене decideDescent_DiamondWithoutFortune_LeavesBlock проверь это утверждение.");
    }

    @Test
    void decideDescent_DiamondWithFortune_MinesWithFortunePick() {
        MineOres.Decision decision = MineOres.decideDescent("minecraft:diamond_ore", List.of(PLAIN_IRON, FORTUNE_TWO));

        assertEquals(
                OptionalInt.of(FORTUNE_TWO.slot()),
                ((MineOres.Mine) decision).slot(),
                "алмаз с Удачей в спуске берётся киркой с наибольшей Удачей; фактически " + decision
                        + " Иначе тест decideDescent_DiamondWithFortune_MinesWithFortunePick перестанет ловить эту поломку. При смене decideDescent_DiamondWithFortune_MinesWithFortunePick проверь это утверждение.");
    }
}
