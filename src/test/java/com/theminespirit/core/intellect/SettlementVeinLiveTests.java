package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.coordinator.WorkReservations;
import com.theminespirit.core.intellect.activity.DefendActivity;
import com.theminespirit.core.intellect.activity.FarmActivity;
import com.theminespirit.core.intellect.activity.MineActivity;
import com.theminespirit.core.intellect.activity.RestActivity;
import com.theminespirit.core.tactics.Point;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementVeinLiveTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UUID WORKER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static JsonNode live() {
        try (InputStream in = SettlementVeinLiveTests.class.getResourceAsStream("/live/observe17-a8/scenario.json")) {
            if (in == null) {
                throw new IllegalStateException("Нет фикстуры наблюдения 17 A8: /live/observe17-a8/scenario.json");
            }
            return MAPPER.readTree(in);
        } catch (IOException error) {
            throw new IllegalStateException("Фикстура наблюдения 17 A8 не читается", error);
        }
    }

    private static Point point(JsonNode node) {
        return new Point(
                node.get(0).asDouble(), node.get(1).asDouble(), node.get(2).asDouble());
    }

    private static TacticalIntellect intellect() {
        return new TacticalIntellect(
                ActivityCatalog.adultsOnly(
                        List.of(new MineActivity(), new FarmActivity(), new RestActivity(), new DefendActivity())),
                StrengthAssessorTests.PROFILES,
                StrengthAssessorTests.ARMOR);
    }

    @Test
    void liveObserve17_MineInsideSettlement_LoneWorkerAssigned() {
        JsonNode root = live();
        Point home = point(root.path("home"));
        Point mine = point(root.path("mine"));
        int radius = root.path("settlementRadius").asInt();
        int spirits = root.path("spirits").asInt();
        int freeWorkers = root.path("freeWorkers").asInt();
        double dx = mine.x() - home.x();
        double dz = mine.z() - home.z();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        assertTrue(
                horizontal <= radius,
                "Фикстура наблюдения 17: шахта " + mine + " в " + horizontal + " блоках от дома " + home
                        + " — внутри границы поселения радиусом " + radius + " (духов " + spirits + ")");

        TacticalIntellect mind = intellect();
        mind.trackHome(home);
        mind.trackVein(mine);
        SettlementView view = new SettlementView(
                1,
                0,
                2,
                2,
                0,
                0,
                0,
                0,
                0.2,
                SettlementViews.farmWork(),
                Forage.NONE,
                List.of(SettlementViews.spirit(WORKER)),
                100);

        TacticalDecision decision =
                mind.decide(view, new ActivityLedger(), new WorkReservations(), new StrategicQueue(), 100);

        boolean assigned = decision.activities().assignments().stream()
                .anyMatch(assignment -> assignment.activity() == ActivityId.MINE);
        assertTrue(
                assigned,
                "При " + freeWorkers + " свободном духе шахта внутри поселения обязана получить добычу: до правки"
                        + " она отвергалась как внешняя");
        assertFalse(
                decision.refusals().stream().anyMatch(reason -> reason.contains("MINE")),
                "Отказа по границе поселения быть не может, фактически: " + decision.refusals());
        assertTrue(
                decision.notes().stream().anyMatch(note -> note.contains("-189.0 63.0 -518.0")),
                "Журнал обязан назвать цель и границу, фактически: " + decision.notes());
    }
}
