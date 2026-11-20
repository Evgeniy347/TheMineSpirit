package com.theminespirit.core.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.api.HerdCap;
import com.theminespirit.core.husbandry.api.PetKind;
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
                "Корм приручения — только пути tame_/trust_ этого вида, в порядке путей и без повторов: иначе дух "
                        + "предложит волку чужой корм, потратит кости на кота и стадо останется диким. При смене "
                        + "отбора путей в PetRules.tameFoods проверь живой захват волка костью и trust-кота треской");
    }

    @Test
    void breedFoods_BreedWays_ReturnConsumesOfOwnKindOnly() {
        assertEquals(
                List.of("minecraft:beef"),
                PetRules.breedFoods(PetKind.WOLF, List.of(TAME_WOLF, BREED_WOLF, OTHER)),
                "Корм разведения — только пути BREED_ANIMALS своего вида: чужой корм не считается, иначе волка "
                        + "кормят рыбой кота, размножение молча не срабатывает и пара стоит до тайм-аута. При смене "
                        + "фильтра в PetRules.breedFoods проверь живое кормление пары волков говядиной");
    }

    @Test
    void tameFoods_NoMatchingWay_ThrowsWithKind() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> PetRules.tameFoods(PetKind.WOLF, List.of(OTHER)),
                "Без пути приручения кормить нечем: молчаливое пустое меню оставило бы стадо диким, а дух ходил "
                        + "бы по загону с пустым списком корма и без причины отказа. При смене PetRules.tameFoods "
                        + "проверь, что вид без tame_/trust_-пути падает отказом, а не возвращает пустой список");

        assertTrue(
                error.getMessage().contains("wolf"),
                "Отказ обязан называть вид питомца: иначе в логе «нет корма» без вида, и не понять, какое стадо "
                        + "не приручается, а опечатку в feeds не найти. Фактическое: " + error.getMessage());
    }

    @Test
    void cap_FixedAndPerSpirit_RespectBothForms() {
        Map<String, HerdCap> caps = Map.of("wolf", new HerdCap(4, null), "cat", new HerdCap(null, 1.5));

        assertEquals(
                4,
                PetRules.cap(PetKind.WOLF, 100, caps),
                "Фиксированный предел не зависит от числа духов: иначе с каждым духом лимит рос бы, стадо "
                        + "расползалось сверх загона и захват шёл бы бесконечно. При смене PetRules.cap проверь "
                        + "оба вида предела и соседнюю дробную долю на двух духах");
        assertEquals(
                3,
                PetRules.cap(PetKind.CAT, 2, caps),
                "Доля на духа округляется вниз: 1.5 × 2 = 3, иначе лишний кот попадает в загон, не помещается "
                        + "в стойло и висит без работы. При смене округления в PetRules.cap проверь живой захват "
                        + "третьего кота при двух духах: дух обязан ждать, а не тащить четвёртого");
    }

    @Test
    void cap_NoLimitForKind_ThrowsWithKind() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> PetRules.cap(PetKind.OCELOT, 1, Map.of()),
                "Без herdCaps стадо росло бы бесконтрольно: вид без лимита обязан падать, иначе план захвата "
                        + "молча отключит предел и загон переполнится. При смене PetRules.cap проверь, что "
                        + "отсутствие записи вида даёт отказ с видом в тексте");

        assertTrue(
                error.getMessage().contains("ocelot"),
                "Отказ обязан называть вид питомца: иначе не найти вид в herdCaps и не добавить предел, а "
                        + "ошибка выглядит как «нет лимита» вообще. Фактическое: " + error.getMessage());
    }
}
