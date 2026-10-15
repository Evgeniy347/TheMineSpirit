package com.theminespirit.core.armor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class ArmorChoiceProperties {
    private static final ArmorTable TABLE = ArmorTable.load();

    private static final String[] HEAD_ITEMS = {"leather_helmet", "golden_helmet", "chainmail_helmet", "iron_helmet",
            "turtle_helmet", "diamond_helmet", "netherite_helmet"};
    private static final String[] CHEST_ITEMS = {"leather_chestplate", "golden_chestplate", "chainmail_chestplate",
            "iron_chestplate", "diamond_chestplate", "netherite_chestplate"};

    @Provide
    Arbitrary<ArmorPiece> headPieces() {
        return piece(HEAD_ITEMS, ArmorSlot.HEAD, 1, 4);
    }

    @Provide
    Arbitrary<ArmorPiece> chestPieces() {
        return piece(CHEST_ITEMS, ArmorSlot.CHEST, 3, 8);
    }

    private static Arbitrary<ArmorPiece> piece(String[] items, ArmorSlot slot, int minDefense, int maxDefense) {
        Arbitrary<String> item = Arbitraries.of(items);
        Arbitrary<Integer> defense = Arbitraries.integers().between(minDefense, maxDefense);
        Arbitrary<Integer> protection = Arbitraries.integers().between(0, 4);
        Arbitrary<Integer> durabilityPercent = Arbitraries.integers().between(0, 100);
        return Combinators.combine(item, defense, protection, durabilityPercent)
                .as((id, def, prot, percent) -> new ArmorPiece("minecraft:" + id, slot, def,
                        TABLE.materialRank("minecraft:" + id) >= 4 ? 2.0 : 0.0, prot, percent / 100.0));
    }

    @Property
    void decide_Chosen_IsNeverWorseThanWorn(@ForAll("headPieces") ArmorPiece worn,
            @ForAll("headPieces") ArmorPiece candidate) {
        Map<ArmorSlot, ArmorPiece> chosen = ArmorChoice.decide(List.of(candidate), Map.of(ArmorSlot.HEAD, worn),
                TABLE);

        ArmorPiece result = chosen.get(ArmorSlot.HEAD);
        assertTrue(ArmorChoice.compare(result, worn, TABLE) >= 0,
                "Выбранный предмет обязан быть не хуже надетого: дух не снимает хорошую броню ради худшей. "
                        + "Надет " + worn + ", выбран " + result);
    }

    @Property
    void decide_EqualWornAndCandidate_KeepsWorn(@ForAll("headPieces") ArmorPiece worn) {
        Map<ArmorSlot, ArmorPiece> chosen = ArmorChoice.decide(List.of(worn), Map.of(ArmorSlot.HEAD, worn), TABLE);

        assertEquals(worn, chosen.get(ArmorSlot.HEAD),
                "При равном кандидате остаётся надетый: замена только при строгом улучшении, иначе дух "
                        + "дёргается между равными. Предмет " + worn);
    }

    @Property
    void isStrictlyBetter_RepeatedDecision_IsStable(@ForAll("chestPieces") ArmorPiece worn,
            @ForAll("chestPieces") ArmorPiece candidate) {
        boolean better = ArmorChoice.isStrictlyBetter(candidate, worn, TABLE);
        boolean reverse = ArmorChoice.isStrictlyBetter(worn, candidate, TABLE);

        assertTrue(!(better && reverse),
                "Строгое улучшение не может быть взаимным: иначе замена дёргалась бы между двумя предметами "
                        + "каждый тик. Надет " + worn + ", кандидат " + candidate);
    }

    @Property
    void compare_Pieces_IsAntisymmetric(@ForAll("chestPieces") ArmorPiece left, @ForAll("chestPieces") ArmorPiece right) {
        int forward = Integer.signum(ArmorChoice.compare(left, right, TABLE));
        int backward = Integer.signum(ArmorChoice.compare(right, left, TABLE));

        assertEquals(-forward, backward,
                "Сравнение обязано быть антисимметричным: иначе порядок «лучше» зависел бы от порядка "
                        + "аргументов. " + left + " и " + right);
    }
}