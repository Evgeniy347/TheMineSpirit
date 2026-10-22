package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.coordinator.SpiritRoles;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.intellect.activity.ChopActivity;
import com.theminespirit.core.intellect.activity.CraftActivity;
import com.theminespirit.core.intellect.activity.DefendActivity;
import com.theminespirit.core.intellect.activity.FarmActivity;
import com.theminespirit.core.intellect.activity.ForageActivity;
import com.theminespirit.core.intellect.activity.MineActivity;
import com.theminespirit.core.intellect.activity.RestActivity;
import com.theminespirit.core.intellect.activity.SmeltActivity;
import com.theminespirit.core.intellect.activity.UnloadActivity;
import com.theminespirit.core.processing.ProcessingStock;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Живой прогон 21.10: духи без задачи стояли в {@code wait}, хотя брёвна были, а досок не было. Свободный дух без
 * плана классифицируется ролью {@link SpiritRole#DEFENDER}; работа поселения обязана доставаться и ему, пока рядом
 * нет угроз, иначе ему годен только отдых.
 */
class IdleSpiritWorkTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void assign_IdleSpiritWithLogsNoPlanks_GetsCraftNotRest() {
        SpiritRole idle = SpiritRoles.classify(Optional.empty(), false);
        SettlementView view = view(List.of(spirit(idle, 0)), logsOnly());
        ActivityAssigner assigner = new ActivityAssigner();
        ActivityCatalog catalog = catalog();

        ActivityPlan plan = assigner.assign(view, DemandModel.evaluate(view, catalog), new ActivityLedger(), 100);

        assertEquals(1, plan.assignments().size(), "Свободный дух обязан получить занятие: иначе он простаивает");
        assertEquals(
                ActivityId.CRAFT,
                plan.assignments().get(0).activity(),
                "Свободный дух без плана при брёвнах и пустом запасе досок обязан крафтить доски, а не ждать: "
                        + "иначе постоянный запас досок и палок не пополняется (живой прогон 21.10, «стоят в wait»)");
    }

    @Test
    void canWork_IdleWithoutThreats_True() {
        SpiritSnapshot idle = spirit(SpiritRoles.classify(Optional.empty(), false), 0);
        assertTrue(
                WorkRoles.canWork(idle, view(List.of(idle), logsOnly())),
                "Свободный дух без угроз рядом годен для работы поселения: роль DEFENDER у него — только ярлык простоя");
    }

    @Test
    void canWork_IdleWithThreatNear_False() {
        SpiritSnapshot near = spirit(SpiritRole.DEFENDER, 1);
        assertFalse(
                WorkRoles.canWork(near, view(List.of(near), logsOnly())),
                "Свободный дух с угрозой рядом остаётся защитником: работа увела бы его от врага");
    }

    @Test
    void canWork_Building_False() {
        SpiritSnapshot builder = spirit(SpiritRole.BUILDING, 0);
        assertFalse(
                WorkRoles.canWork(builder, view(List.of(builder), logsOnly())),
                "Строитель не берёт фоновую работу: стройку прерывать нельзя");
    }

    private static ActivityCatalog catalog() {
        return ActivityCatalog.adultsOnly(List.of(
                new ChopActivity(),
                new FarmActivity(),
                new ForageActivity(),
                new MineActivity(),
                new RestActivity(),
                new DefendActivity(),
                new UnloadActivity(),
                new SmeltActivity(),
                new CraftActivity()));
    }

    private static ProcessingStock logsOnly() {
        return new ProcessingStock(64, 0, 0, 0, Map.of(), Map.of("minecraft:oak_log", 8));
    }

    private static SpiritSnapshot spirit(SpiritRole role, int threats) {
        return new SpiritSnapshot(
                SPIRIT, new Point(0, 64, 0), 1.0, role, CombatMode.DEFENSIVE, threats, false, Optional.empty(), 0, 1.0);
    }

    private static SettlementView view(List<SpiritSnapshot> spirits, ProcessingStock processing) {
        return SettlementViews.withStations(
                new SettlementView(
                        spirits.size(),
                        0,
                        2,
                        2,
                        64,
                        32,
                        2,
                        0,
                        0.3,
                        FarmWork.NONE,
                        Forage.NONE,
                        spirits,
                        100,
                        processing),
                SettlementViews.stocked());
    }
}
