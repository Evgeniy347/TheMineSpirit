package com.theminespirit.core.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.graph.HerdCap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PetRulesTests {
    private static final PetRules.PetWay TAME_WOLF = new PetRules.PetWay(
            "tame_wolf", BoardCase.CAPTURE_ANIMAL, "wolf", List.of("minecraft:bone", "minecraft:beef"));
    private static final PetRules.PetWay TRUST_CAT =
            new PetRules.PetWay("trust_cat", BoardCase.CAPTURE_ANIMAL, "cat", List.of("minecraft:cod"));
    private static final PetRules.PetWay BREED_WOLF =
            new PetRules.PetWay("breed_wolf", BoardCase.BREED_ANIMALS, "wolf", List.of("minecraft:beef"));
    private static final PetRules.PetWay OTHER =
            new PetRules.PetWay("breed_cat", BoardCase.BREED_ANIMALS, "cat", List.of("minecraft:salmon"));

    @Test
    void tameFoods_TameAndTrustWays_ReturnConsumesInOrder() {
        assertEquals(
                List.of("minecraft:bone", "minecraft:beef"),
                PetRules.tameFoods(PetKind.WOLF, List.of(TAME_WOLF, TRUST_CAT, BREED_WOLF)),
                "Корм приручения — только пути tame_/trust_ этого вида, в порядке путей и без повторов");
    }

    @Test
    void breedFoods_BreedWays_ReturnConsumesOfOwnKindOnly() {
        assertEquals(
                List.of("minecraft:beef"),
                PetRules.breedFoods(PetKind.WOLF, List.of(TAME_WOLF, BREED_WOLF, OTHER)),
                "Корм разведения — только пути BREED_ANIMALS для своего вида: чужой корм не считается");
    }

    @Test
    void tameFoods_NoMatchingWay_ThrowsWithKind() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> PetRules.tameFoods(PetKind.WOLF, List.of(OTHER)),
                "Без пути приручения кормить нечем: молчаливое пустое меню оставило бы стадо диким");

        assertTrue(
                error.getMessage().contains("wolf"),
                "Отказ обязан называть вид питомца. Фактическое: " + error.getMessage());
    }

    @Test
    void cap_FixedAndPerSpirit_RespectBothForms() {
        Map<String, HerdCap> caps = Map.of("wolf", new HerdCap(4, null), "cat", new HerdCap(null, 1.5));

        assertEquals(4, PetRules.cap(PetKind.WOLF, 100, caps), "Фиксированный предел не зависит от числа духов");
        assertEquals(3, PetRules.cap(PetKind.CAT, 2, caps), "Доля на духа округляется вниз: 1.5 × 2 = 3");
    }

    @Test
    void cap_NoLimitForKind_ThrowsWithKind() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> PetRules.cap(PetKind.OCELOT, 1, Map.of()),
                "Без herdCaps стадо росло бы бесконтрольно: вид без лимита обязан падать");

        assertTrue(
                error.getMessage().contains("ocelot"),
                "Отказ обязан называть вид питомца. Фактическое: " + error.getMessage());
    }
}
