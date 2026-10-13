package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FortifyPlanTests {
    private static FortifyRoster shelterCrew() {
        UUID builder = UUID.randomUUID();
        return new FortifyRoster(builder, List.of(UUID.randomUUID()), Optional.of(new Point(0, 64, 0)),
                FortifyMode.SHELTER);
    }

    @Test
    void plan_ShelterRoster_ShelterCells() {
        FortifyRoster crew = shelterCrew();

        FortifyPlan plan = FortifyPlan.plan(Optional.of(crew), new BlockCoord(0, 64, 0), Optional.empty(),
                Optional.empty());

        assertTrue(plan.order().isPresent(),
                "Ростер укрытия обязан давать приказ: иначе решение SHELTER висит без исполнителя");
        assertEquals(24, plan.order().get().cells().size(),
                "Укрытие 4×4 высотой 2 — это 24 клетки стен: иначе в стене дыра");
        assertEquals(crew.guards(), plan.order().get().guards(),
                "Защитники приказа обязаны совпадать с ростером: иначе кроет не тот, кого назначили");
        assertEquals(crew.builder(), plan.order().get().builder(),
                "Строитель приказа обязан совпадать с ростером: иначе строит не тот, кого назначили");
    }

    @Test
    void plan_BarricadeRoster_SeamCells() {
        UUID builder = UUID.randomUUID();
        FortifyRoster crew = new FortifyRoster(builder, List.of(UUID.randomUUID()),
                Optional.of(new Point(0, 64, 0)), FortifyMode.BARRICADE);

        FortifyPlan plan = FortifyPlan.plan(Optional.of(crew), new BlockCoord(9, 64, 9),
                Optional.of(new BlockCoord(0, 64, 0)), Optional.of(new BlockCoord(2, 64, 0)));

        assertTrue(plan.order().isPresent(),
                "Ростер шва обязан давать приказ: иначе решение BARRICADE висит без исполнителя");
        assertEquals(6, plan.order().get().cells().size(),
                "Шов 3×2 обязан давать 6 клеток: иначе проход не запечатан");
        assertEquals(FortifyMode.BARRICADE, plan.order().get().mode(),
                "Режим приказа обязан совпадать с ростером: иначе строитель ставит не то, что приказано");
    }

    @Test
    void plan_BarricadeWithoutBanks_ShelterFallback() {
        UUID builder = UUID.randomUUID();
        FortifyRoster crew = new FortifyRoster(builder, List.of(),
                Optional.of(new Point(0, 64, 0)), FortifyMode.BARRICADE);

        FortifyPlan plan = FortifyPlan.plan(Optional.of(crew), new BlockCoord(0, 64, 0), Optional.empty(),
                Optional.empty());

        assertTrue(plan.order().isPresent(),
                "Шов без берегов обязан откатываться на укрытие, а не пропадать: иначе строитель встаёт без дела");
        assertEquals(24, plan.order().get().cells().size(),
                "Откат обязан давать полное укрытие: иначе дух строит огрызок вместо обороны");
    }

    @Test
    void plan_EmptyRoster_EmptyPlan() {
        FortifyPlan plan = FortifyPlan.plan(Optional.empty(), new BlockCoord(0, 64, 0), Optional.empty(),
                Optional.empty());

        assertEquals(Optional.empty(), plan.order(),
                "Пустой ростер обязан давать пустой план: иначе духи строятся без решения");
        assertEquals(List.of(), plan.batches(),
                "Пустой план обязан держать пустые порции: иначе исполнитель ставит блоки из пустоты");
    }

    @Test
    void plan_Batches_RespectPerTickLimit() {
        FortifyPlan plan = FortifyPlan.plan(Optional.of(shelterCrew()), new BlockCoord(0, 64, 0), Optional.empty(),
                Optional.empty());

        for (List<BlockCoord> batch : plan.batches()) {
            assertTrue(batch.size() <= FortifyTuning.FORTIFY_BLOCKS_PER_TICK,
                    "Порция обязана держать не больше двух блоков: иначе стройка грузит серверный тик");
        }
        List<BlockCoord> joined = new ArrayList<>();
        for (List<BlockCoord> batch : plan.batches()) {
            joined.addAll(batch);
        }
        assertEquals(plan.order().orElseThrow().cells(), joined,
                "Склейка порций обязана давать клетки приказа: потеря клетки — дыра в укреплении");
    }

    @Test
    void plan_NullInput_Throws() {
        assertThrows(NullPointerException.class,
                () -> FortifyPlan.plan(null, new BlockCoord(0, 64, 0), Optional.empty(), Optional.empty()),
                "План без ростера обязан отвергаться: без него неизвестно, кто строит");
        assertThrows(NullPointerException.class,
                () -> FortifyPlan.plan(Optional.empty(), null, Optional.empty(), Optional.empty()),
                "План без клетки строителя обязан отвергаться: без неё духа замуруют");
    }
}
