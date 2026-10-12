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

        assertEquals(PerceptionType.BLOCKS, query.type(), "scan_blocks обязан строить запрос блоков");
        assertEquals(12, query.radius().value(), "радиус запроса обязан совпасть с параметром плана");
        assertEquals(IdFilter.of(List.of("*_ore")).patterns(), query.filter().patterns(),
                "фильтр запроса обязан совпасть с параметром плана: иначе ИИ получит блоки, которых не просил");
    }

    @Test
    void entities_NoFilter_BuildEntitiesQueryMatchingAll() {
        PerceptionQuery query = PerceptionParams.entities(Map.of("radius", 5));

        assertEquals(PerceptionType.ENTITIES, query.type(), "scan_entities обязан строить запрос сущностей");
        assertEquals(true, query.filter().matchesAll(),
                "без параметра filter запрос сущностей обязан брать всех: пустой фильтр значит «всё»");
    }

    @Test
    void blocks_RadiusAboveScanLimit_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PerceptionParams.blocks(Map.of("radius", ScanRadius.MAX + 1)),
                "радиус сверх предела скана обязан отклоняться: больший скан кладёт серверный тик");
    }

    @Test
    void hazards_Radius_BuildHazardsQueryMatchingAllIds() {
        PerceptionQuery query = PerceptionParams.hazards(Map.of("radius", 6));

        assertEquals(PerceptionType.HAZARDS, query.type(), "sense_hazard обязан строить запрос опасностей");
        assertEquals(6, query.radius().value(), "радиус запроса опасностей обязан совпасть с параметром плана");
    }

    @Test
    void hazards_NoRadius_IsRejectedWithParamName() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PerceptionParams.hazards(Map.of()),
                "radius приходит из каталога с умолчанием; без него разбор обязан падать, а не выдумывать значение");

        assertEquals(true, error.getMessage().contains("radius"),
                "сообщение обязано назвать параметр radius, сейчас: " + error.getMessage());
    }
}
