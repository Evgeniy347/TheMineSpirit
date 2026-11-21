package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.perception.PerceptionQuery;
import com.theminespirit.core.perception.PerceptionType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PerceptionParamsTests {
    @Test
    void blocks_RadiusAndFilter_BuildBlocksQueryWithSameValues() {
        PerceptionQuery query = PerceptionParams.blocks(Map.of("radius", 12, "filter", List.of("*_ore")));

        assertEquals(
                PerceptionType.BLOCKS,
                query.type(),
                "scan_blocks обязан строить запрос блоков"
                        + " Иначе тест blocks_RadiusAndFilter_BuildBlocksQueryWithSameValues перестанет ловить эту поломку. При смене blocks_RadiusAndFilter_BuildBlocksQueryWithSameValues проверь это утверждение.");
        assertEquals(
                12,
                query.radius().value(),
                "радиус запроса обязан совпасть с параметром плана"
                        + " Иначе тест blocks_RadiusAndFilter_BuildBlocksQueryWithSameValues перестанет ловить эту поломку. При смене blocks_RadiusAndFilter_BuildBlocksQueryWithSameValues проверь это утверждение.");
        assertEquals(
                IdFilter.of(List.of("*_ore")).patterns(),
                query.filter().patterns(),
                "фильтр запроса обязан совпасть с параметром плана: иначе ИИ получит блоки, которых не просил"
                        + " При смене blocks_RadiusAndFilter_BuildBlocksQueryWithSameValues проверь это утверждение.");
    }

    @Test
    void entities_NoFilter_BuildEntitiesQueryMatchingAll() {
        PerceptionQuery query = PerceptionParams.entities(Map.of("radius", 5));

        assertEquals(
                PerceptionType.ENTITIES,
                query.type(),
                "scan_entities обязан строить запрос сущностей"
                        + " Иначе тест entities_NoFilter_BuildEntitiesQueryMatchingAll перестанет ловить эту поломку. При смене entities_NoFilter_BuildEntitiesQueryMatchingAll проверь это утверждение.");
        assertEquals(
                true,
                query.filter().matchesAll(),
                "без параметра filter запрос сущностей обязан брать всех: пустой фильтр значит «всё»"
                        + " Иначе тест entities_NoFilter_BuildEntitiesQueryMatchingAll перестанет ловить эту поломку. При смене entities_NoFilter_BuildEntitiesQueryMatchingAll проверь это утверждение.");
    }

    @Test
    void blocks_RadiusAboveScanLimit_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PerceptionParams.blocks(Map.of("radius", ScanRadius.MAX + 1)),
                "радиус сверх предела скана обязан отклоняться: больший скан кладёт серверный тик"
                        + " Иначе тест blocks_RadiusAboveScanLimit_IsRejected перестанет ловить эту поломку. При смене blocks_RadiusAboveScanLimit_IsRejected проверь это утверждение.");
    }

    @Test
    void hazards_Radius_BuildHazardsQueryMatchingAllIds() {
        PerceptionQuery query = PerceptionParams.hazards(Map.of("radius", 6));

        assertEquals(
                PerceptionType.HAZARDS,
                query.type(),
                "sense_hazard обязан строить запрос опасностей"
                        + " Иначе тест hazards_Radius_BuildHazardsQueryMatchingAllIds перестанет ловить эту поломку. При смене hazards_Radius_BuildHazardsQueryMatchingAllIds проверь это утверждение.");
        assertEquals(
                6,
                query.radius().value(),
                "радиус запроса опасностей обязан совпасть с параметром плана"
                        + " Иначе тест hazards_Radius_BuildHazardsQueryMatchingAllIds перестанет ловить эту поломку. При смене hazards_Radius_BuildHazardsQueryMatchingAllIds проверь это утверждение.");
    }

    @Test
    void hazards_NoRadius_IsRejectedWithParamName() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PerceptionParams.hazards(Map.of()),
                "radius приходит из каталога с умолчанием; без него разбор обязан падать, а не выдумывать значение"
                        + " Иначе тест hazards_NoRadius_IsRejectedWithParamName перестанет ловить эту поломку. При смене hazards_NoRadius_IsRejectedWithParamName проверь это утверждение.");

        assertEquals(
                true,
                error.getMessage().contains("radius"),
                "сообщение обязано назвать параметр radius, сейчас: " + error.getMessage()
                        + " Иначе тест hazards_NoRadius_IsRejectedWithParamName перестанет ловить эту поломку. При смене hazards_NoRadius_IsRejectedWithParamName проверь это утверждение.");
    }
}
