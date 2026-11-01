package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.movement.SelfEscape;
import com.theminespirit.core.movement.StuckSituation;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoofRescueLiveTests {
    private static final Path LIVE = Path.of("src/test/resources/live/roofstuck/roof.json");

    @Test
    void selfEscape_LiveRoofWithLowerHome_Descends() throws IOException {
        JsonNode live = new ObjectMapper().readTree(Files.readString(LIVE, StandardCharsets.UTF_8));
        BlockCoord feet = coord(live.get("feet"));
        BlockCoord home = coord(live.get("home"));
        StuckSituation.Grid free = new StuckSituation.Grid() {
            @Override
            public boolean passable(BlockCoord cell) {
                return cell.y() >= feet.y() && cell.y() <= feet.y() + 1;
            }

            @Override
            public int floorY(int x, int z) {
                return feet.y() - 1;
            }
        };

        SelfEscape.Plan plan = SelfEscape.plan(feet, home, free, false, 0)
                .orElseThrow(() -> new AssertionError("spirit-3 стоял на крыше " + feet + " и не получал спасения: "
                        + live.get("stuck_ticks") + " тиков, home ниже " + home));

        assertEquals(SelfEscape.Method.DESCEND, plan.method(), "с крыши нужен спуск, а не подъём");
        assertEquals(home, plan.target(), "спуск ведёт к дому на земле");
    }

    @Test
    void rescuePlan_LiveRoof_DigsDownThenWalksHome() throws IOException {
        JsonNode live = new ObjectMapper().readTree(Files.readString(LIVE, StandardCharsets.UTF_8));
        BlockCoord feet = coord(live.get("feet"));
        BlockCoord home = coord(live.get("home"));
        SelfEscape.Plan self = new SelfEscape.Plan(SelfEscape.Method.DESCEND, home, 0, List.of());

        TaskNode plan = RescuePlans.plan(
                        BoardCase.RESCUE_SPIRIT,
                        TicketTarget.at(feet).withRescue(self).withRelocate(feet, home))
                .orElseThrow();

        List<ScriptCall> calls = new ArrayList<>();
        collect(plan, calls);
        assertEquals(
                List.of("dig_out", "goto"),
                calls.stream().map(ScriptCall::script).toList(),
                "план спасения с крыши: сначала спуск, потом дорога к поселению");
        assertTrue(plan instanceof SequenceNode, "спуск и дорога — последовательность, а не одиночный goto");
    }

    private static void collect(TaskNode plan, List<ScriptCall> calls) {
        if (plan instanceof ScriptCall call) {
            calls.add(call);
        } else if (plan instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, calls));
        }
    }

    private static BlockCoord coord(JsonNode node) {
        return new BlockCoord(
                node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
    }
}
