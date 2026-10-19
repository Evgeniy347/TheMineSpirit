package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlockProtectionUseTests {
    private BlockProtection protection;

    @BeforeAll
    void loadProtection() {
        protection = ProtectionConfig.load();
    }

    private static List<String> around(int artificial, String artificialId, String filler) {
        List<String> list = new ArrayList<>(Collections.nCopies(artificial, artificialId));
        list.addAll(Collections.nCopies(BlockProtection.NEIGHBOUR_COUNT - artificial, filler));
        return list;
    }

    static Stream<Arguments> table() {
        List<String> wild = around(0, "", "minecraft:stone");
        List<String> house = around(8, "minecraft:oak_planks", "minecraft:stone");
        return Stream.of(
                Arguments.of("рычаг в поле", wild, false, false, false, null),
                Arguments.of("кнопка в поле", wild, false, false, false, null),
                Arguments.of("дверь в поле", wild, false, false, false, null),
                Arguments.of("рычаг в доме из восьми блоков", house, false, false, false, PolicyId.PLAYER_BUILDINGS),
                Arguments.of("рычаг, поставленный игроком", wild, true, false, false, PolicyId.PLAYER_BUILDINGS),
                Arguments.of("рычаг в защищённой зоне", wild, false, true, false, PolicyId.PROTECTED_ZONE),
                Arguments.of(
                        "рычаг игрока, но в зоне: зона раньше игрока",
                        wild,
                        true,
                        true,
                        false,
                        PolicyId.PROTECTED_ZONE),
                Arguments.of("рычаг, поставленный игроком, но в разрешённой области", wild, true, false, true, null),
                Arguments.of("рычаг в разрешённой области: здания рядом не мешают", house, false, false, true, null),
                Arguments.of(
                        "рычаг игрока в защищённой зоне и разрешённой области: зона сильнее",
                        wild,
                        true,
                        true,
                        true,
                        PolicyId.PROTECTED_ZONE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("table")
    void checkUse_Table_RefusesOnlyPlayerMechanisms(
            String name,
            List<String> neighbours,
            boolean placed,
            boolean zone,
            boolean unprotectArea,
            PolicyId refusedBy) {
        BlockProtection.Decision decision =
                protection.checkUse(neighbours, new BlockProtection.Placement(placed, zone, unprotectArea));

        if (refusedBy == null) {
            assertInstanceOf(
                    BlockProtection.Allowed.class,
                    decision,
                    "«" + name + "»: механизм без чужой постройки обязан нажиматься, иначе дух не откроет дверь "
                            + "собственной базы");
        } else {
            BlockProtection.Refused refused = assertInstanceOf(
                    BlockProtection.Refused.class,
                    decision,
                    "«" + name + "»: чужой или запрещённый механизм обязан отвергаться. Ответ: " + decision);
            assertEquals(
                    refusedBy,
                    refused.policy(),
                    "«" + name + "»: политика отказа должна называть причину, чтобы скрипт показал понятное сообщение");
        }
    }

    @Test
    void checkUse_DeniedByBreakList_IsStillUsable() {
        BlockProtection.Decision decision = protection.checkUse(
                around(0, "", "minecraft:stone"), new BlockProtection.Placement(false, false, false));

        assertInstanceOf(
                BlockProtection.Allowed.class,
                decision,
                "Использовать рычаг — не ломать его: чёрный список защиты (deny) на нажатие не распространяется, "
                        + "иначе дверь и рычаг собственной базы никогда не сработают");
    }

    @Test
    void checkUse_WrongNeighbourCount_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> protection.checkUse(List.of("minecraft:air"), new BlockProtection.Placement(false, false, false)),
                "Список соседей обязан быть 26: иное число значит, что постройку считают не по 3×3×3");
    }

    @Test
    void checkUse_NullNeighbours_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> protection.checkUse(null, new BlockProtection.Placement(false, false, false)),
                "Без списка соседей нельзя отличить постройку от природы: null обязан отказывать");
    }
}
