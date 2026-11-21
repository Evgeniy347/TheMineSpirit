package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.forestry.api.TreeSpecies;
import com.theminespirit.core.script.api.CatalogLimits;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChopTreeParamsTests {
    @Test
    void from_AllParamsPresent_ReturnsSpeciesCountRadiusReplant() {
        ChopTreeParams params =
                ChopTreeParams.from(Map.of("species", "oak", "count", 3, "radius", 16, "replant", true));

        assertEquals(
                new ChopTreeParams(TreeSpecies.parse("oak"), 3, 16, true),
                params,
                "chop_tree обязан принять породу, число деревьев, радиус и флаг посадки: по ним скрипт "
                        + "знает, что искать, сколько рубить и куда не ходить"
                        + " Иначе тест from_AllParamsPresent_ReturnsSpeciesCountRadiusReplant перестанет ловить эту поломку. При смене from_AllParamsPresent_ReturnsSpeciesCountRadiusReplant проверь это утверждение.");
    }

    @Test
    void from_SpeciesAny_ReturnsAllSpecies() {
        ChopTreeParams params =
                ChopTreeParams.from(Map.of("species", "any", "count", 1, "radius", 8, "replant", false));

        assertEquals(
                TreeSpecies.ALL,
                params.species(),
                "species=any обязан означать все породы: без этого дух не срубит берёзу с дубом в одном лесу"
                        + " При смене from_SpeciesAny_ReturnsAllSpecies проверь это утверждение.");
    }

    @Test
    void from_UnknownSpecies_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ChopTreeParams.from(Map.of("species", "pine", "count", 1, "radius", 8, "replant", false)),
                "Неизвестная порода обязана отклонить параметры: опечатку нельзя тихо трактовать как any"
                        + " Иначе тест from_UnknownSpecies_IsRejected перестанет ловить эту поломку. При смене from_UnknownSpecies_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("oak") && error.getMessage().contains("any"),
                "Сообщение обязано назвать допустимые породы: по нему правят species. Фактическое: "
                        + error.getMessage()
                        + " Иначе тест from_UnknownSpecies_IsRejected перестанет ловить эту поломку. При смене from_UnknownSpecies_IsRejected проверь это утверждение.");
    }

    @Test
    void from_CountBelowRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChopTreeParams.from(Map.of("species", "oak", "count", 0, "radius", 8, "replant", false)),
                "count ниже минимума обязан быть отклонён: рубить ноль деревьев — пустая команда"
                        + " Иначе тест from_CountBelowRange_IsRejected перестанет ловить эту поломку. При смене from_CountBelowRange_IsRejected проверь это утверждение.");
    }

    @Test
    void from_CountAboveRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChopTreeParams.from(Map.of("species", "oak", "count", 257, "radius", 8, "replant", false)),
                "count выше максимума обязан быть отклонён: заявленный лес не существует, а прогон бесполезен"
                        + " Иначе тест from_CountAboveRange_IsRejected перестанет ловить эту поломку. При смене from_CountAboveRange_IsRejected проверь это утверждение.");
    }

    @Test
    void from_RadiusBelowRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChopTreeParams.from(Map.of("species", "oak", "count", 1, "radius", 0, "replant", false)),
                "Радиус нуль обязан быть отклонён: пустое пятно под духом не покрывает ни одного дерева"
                        + " Иначе тест from_RadiusBelowRange_IsRejected перестанет ловить эту поломку. При смене from_RadiusBelowRange_IsRejected проверь это утверждение.");
    }

    @Test
    void from_RadiusAboveRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChopTreeParams.from(Map.of(
                        "species", "oak", "count", 1, "radius", CatalogLimits.CHOP_RADIUS_MAX + 1, "replant", false)),
                "Радиус выше максимума обязан быть отклонён: поиск на всю карту обходится слишком дорого"
                        + " Иначе тест from_RadiusAboveRange_IsRejected перестанет ловить эту поломку. При смене from_RadiusAboveRange_IsRejected проверь это утверждение.");
    }

    @Test
    void from_ReplantWrongType_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChopTreeParams.from(Map.of("species", "oak", "count", 1, "radius", 8, "replant", "yes")),
                "replant строкой обязан быть отклонён: тип bool обеспечивает валидатор плана, "
                        + "а строку нельзя молча превратить во флаг"
                        + " Иначе тест from_ReplantWrongType_IsRejected перестанет ловить эту поломку. При смене from_ReplantWrongType_IsRejected проверь это утверждение.");
    }

    @Test
    void from_MissingRadius_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ChopTreeParams.from(Map.of("species", "oak", "count", 1, "replant", false)),
                "chop_tree без радиуса обязан отказывать: валидатор плана подставляет умолчание до запуска, "
                        + "а без него скрипт не знает область поиска"
                        + " Иначе тест from_MissingRadius_IsRejected перестанет ловить эту поломку. При смене from_MissingRadius_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("radius"),
                "Отказ обязан называть недостающий параметр 'radius'. Фактическое: " + error.getMessage()
                        + " Иначе тест from_MissingRadius_IsRejected перестанет ловить эту поломку. При смене from_MissingRadius_IsRejected проверь это утверждение.");
    }

    @Test
    void from_MissingSpecies_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ChopTreeParams.from(Map.of("count", 1, "radius", 8, "replant", false)),
                "chop_tree без породы обязан отказывать: без вида скрипт не знает, какие брёвна искать"
                        + " Иначе тест from_MissingSpecies_IsRejected перестанет ловить эту поломку. При смене from_MissingSpecies_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("species"),
                "Отказ обязан называть недостающую породу 'species'. Фактическое: " + error.getMessage()
                        + " Иначе тест from_MissingSpecies_IsRejected перестанет ловить эту поломку. При смене from_MissingSpecies_IsRejected проверь это утверждение.");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> ChopTreeParams.from(null),
                "ChopTreeParams.from(null) обязан отказывать: без карты параметров маппинг только читает состояние мира"
                        + " Иначе тест from_NullParams_IsRejected перестанет ловить эту поломку. При смене from_NullParams_IsRejected проверь это утверждение.");
    }
}
