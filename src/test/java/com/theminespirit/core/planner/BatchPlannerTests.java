package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ScriptCatalog;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BatchPlannerTests {
    private static final WorldObservation OBSERVATION = Observations.empty("spirit_1");
    private static final ScriptCatalog FOLLOW_CATALOG =
            CoreScripts.defaultCatalog().restrictedTo(Set.of("goto", "follow"));
    private static final ScriptCatalog GOTO_CATALOG =
            CoreScripts.defaultCatalog().restrictedTo(Set.of("goto"));

    @Test
    void planAll_SecondCatalogCannotPlan_ThrowsNamingSecond() {
        Goal goal = new Goal("следуй за мной", Map.of("speaker", "Steve"));

        BatchPlanException error = assertThrows(BatchPlanException.class,
                () -> BatchPlanner.planAll(List.of(FOLLOW_CATALOG, GOTO_CATALOG), goal, OBSERVATION),
                "Если второй дух не может принять цель, её не должен получить и первый: иначе '/spirit goal' "
                        + "применяется частично, и часть духов остаётся без задачи");

        assertEquals(1, error.index(),
                "Ошибка обязана называть второго духа (индекс 1), которому цель не поставилась: по номеру игрок "
                        + "видит, кого чинить. Фактическое сообщение: " + error.getMessage());
    }

    @Test
    void planAll_EveryCatalogCanPlan_ReturnsPlanForEach() {
        Goal goal = new Goal("следуй за мной", Map.of("speaker", "Steve"));

        List<TaskNode> plans = BatchPlanner.planAll(List.of(FOLLOW_CATALOG, FOLLOW_CATALOG), goal, OBSERVATION);

        assertEquals(2, plans.size(),
                "Когда цель по силам всем духам, план обязан быть у каждого: иначе часть духов остаётся без задачи");
    }
}
