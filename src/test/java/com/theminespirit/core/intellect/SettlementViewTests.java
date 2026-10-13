package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementViewTests {
    private static final UUID FIRST = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static SpiritSnapshot worker(UUID id, SpiritRole role, Optional<WorkTarget> target) {
        return new SpiritSnapshot(id, new Point(0, 64, 0), 1, role, CombatMode.DEFENSIVE, 0, false, target, 5);
    }

    private static SettlementView view(int population, int children, double food, double nutrition, int ore, int iron,
            int weapons, List<SpiritSnapshot> spirits) {
        return new SettlementView(population, children, food, nutrition, ore, iron, weapons, 0, 0.5, spirits, 7);
    }

    @Test
    void adults_PopulationMinusChildren_EqualsDifference() {
        SettlementView settlement = view(5, 2, 2, 2, 64, 32, 2, List.of());

        assertEquals(3, settlement.adults(),
                "Взрослых трое из пяти: каталог деятельностей выбирается по числу взрослых работников");
    }

    @Test
    void idle_BusySpirit_IsExcluded() {
        SpiritSnapshot free = worker(FIRST, SpiritRole.WORKER, Optional.empty());
        SpiritSnapshot busy = worker(SECOND, SpiritRole.WORKER,
                Optional.of(new WorkTarget(WorkKind.TREE, new BlockCoord(1, 64, 1))));
        SettlementView settlement = view(2, 0, 2, 2, 64, 32, 2, List.of(free, busy));

        assertEquals(List.of(free), settlement.idle(),
                "Занятый дух не простаивает: распределитель не отнимет у него цель координатора");
    }

    @Test
    void freeWorkers_DefenderAndBusy_AreExcluded() {
        SpiritSnapshot worker = worker(FIRST, SpiritRole.WORKER, Optional.empty());
        SpiritSnapshot guard = worker(SECOND, SpiritRole.DEFENDER, Optional.empty());
        SettlementView settlement = view(2, 0, 2, 2, 64, 32, 2, List.of(worker, guard));

        assertEquals(List.of(worker), settlement.freeWorkers(),
                "Защитник не свободный работник: пост он держит, даже когда простаивает без цели");
    }

    @Test
    void needsFood_BelowNorm_IsTrue() {
        SettlementView settlement = view(4, 0, 0.5, 0.5, 64, 32, 2, List.of());

        assertTrue(settlement.needsFood(1, 1.5),
                "Сытности 0.5 при норме 1.5 не хватает: ферма обязана подняться в спросе");
        assertFalse(view(4, 0, 0.5, 2, 64, 32, 2, List.of()).needsFood(1, 1.5),
                "Сытности 2 при норме 1.5 хватает: ферма уступает место другим работам");
    }

    @Test
    void needsOre_AnyShortage_IsTrue() {
        assertTrue(view(2, 0, 2, 2, 10, 32, 2, List.of()).needsOre(64, 32, 2),
                "Руды 10 при цели 64 не хватает: добыча обязана подняться в спросе");
        assertFalse(view(2, 0, 2, 2, 64, 32, 2, List.of()).needsOre(64, 32, 2),
                "Запасы в норме: добыча уступает место другим работам");
    }

    @Test
    void constructor_ChildrenAbovePopulation_Throws() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> view(2, 3, 2, 2, 64, 32, 2, List.of()),
                "Детей больше, чем духов: взрослых получится отрицательно и каталог выберется неверно");

        assertTrue(error.getMessage().contains("Детей"),
                "Сообщение называет поле детей: при падении видно, какое число поселения противоречиво");
    }

    @Test
    void constructor_UnorderedSpirits_Throws() {
        SpiritSnapshot first = worker(FIRST, SpiritRole.WORKER, Optional.empty());
        SpiritSnapshot second = worker(SECOND, SpiritRole.WORKER, Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> view(2, 0, 2, 2, 64, 32, 2, List.of(second, first)),
                "Духи не по порядку id: распределение станет зависеть от порядка сбора снимка");
    }

    @Test
    void constructor_NegativeStocksAndBadDay_Throw() {
        assertThrows(IllegalArgumentException.class, () -> view(-1, 0, 2, 2, 64, 32, 2, List.of()),
                "Отрицательное население противоречит поселению: спрос считается на душу");
        assertThrows(IllegalArgumentException.class, () -> view(2, 0, 2, 2, -1, 32, 2, List.of()),
                "Отрицательная руда противоречит запасам: нужда в добыче посчитается неверно");
        assertThrows(IllegalArgumentException.class,
                () -> new SettlementView(2, 0, 2, 2, 64, 32, 2, 0, 1.5, List.of(), 7),
                "Доля суток вне 0..1 противоречит времени: ночной отдых включится не вовремя");
    }

    @Test
    void needsFood_BadHorizon_Throws() {
        SettlementView settlement = view(2, 0, 2, 2, 64, 32, 2, List.of());

        assertThrows(IllegalArgumentException.class, () -> settlement.needsFood(0, 1.5),
                "Норма еды на ноль суток всегда выполнена: голод станет невидимым для спроса");
    }
}
