package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphFacts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivityLaunchTests {
    private static final UUID SPIRIT = UUID.fromString("c48663b4-da59-43d9-9804-81a680580300");

    @Test
    void dangerAvoidance_AssignedSpirit_UsesGuardedLaunch() throws IOException {
        String source = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/More3Post.java"));
        String avoidance = source.substring(
                source.indexOf("private static boolean avoidDanger"),
                source.indexOf("private static boolean recoverDrops"));

        assertTrue(
                avoidance.contains("ActivityLaunch.launchOn(post, spirit"),
                "Избегание прошлой опасности обязано проверять занятость духа: иначе launchNow перезапишет "
                        + "wheat_field и оставит битет ASSIGNED без исполнения. При смене "
                        + "dangerAvoidance_AssignedSpirit_UsesGuardedLaunch проверь More3Post.avoidDanger и "
                        + "ActivityLaunch.busy.");
        assertFalse(
                avoidance.contains("ActivityLaunch.launchNow"),
                "Прошлая danger_spot не является немедленной угрозой жизни и не может обходить гейт билета: "
                        + "иначе поле снова вытесняется goto. При смене "
                        + "dangerAvoidance_AssignedSpirit_UsesGuardedLaunch проверь More3Post.avoidDanger и "
                        + "DisplacedClaims.");
    }

    @Test
    void stuck_StuckTrappedOrInTrap_IsNotSentToActivity() {
        for (GraphFacts.Spirit fact :
                List.of(spirit(true, false, null), spirit(false, true, null), spirit(false, false, "яма"))) {
            assertTrue(
                    ActivityLaunch.stuck(facts(fact), SPIRIT),
                    "Застрявшего духа не отправляют в поход/патруль: он вернёт NOT_REACHED на каждой точке "
                            + "(observe25 B12): stuck=" + fact.stuck() + " trapped=" + fact.trapped() + " trap="
                            + fact.trap()
                            + " Иначе тест stuck_StuckTrappedOrInTrap_IsNotSentToActivity перестанет ловить эту поломку. При смене stuck_StuckTrappedOrInTrap_IsNotSentToActivity проверь это утверждение.");
        }
    }

    @Test
    void stuck_HealthySpirit_GoesToActivity() {
        assertFalse(
                ActivityLaunch.stuck(facts(spirit(false, false, null)), SPIRIT),
                "Свободный дух идёт в поход: запрет только для застрявших"
                        + " Иначе тест stuck_HealthySpirit_GoesToActivity перестанет ловить эту поломку. При смене stuck_HealthySpirit_GoesToActivity проверь это утверждение.");
        assertFalse(
                ActivityLaunch.stuck(GraphFacts.empty(), SPIRIT),
                "Дух без факта в графе не считается застрявшим: сведения просто не собраны"
                        + " Иначе тест stuck_HealthySpirit_GoesToActivity перестанет ловить эту поломку. При смене stuck_HealthySpirit_GoesToActivity проверь это утверждение.");
    }

    private static GraphFacts facts(GraphFacts.Spirit spirit) {
        return GraphFacts.empty().withSpirits(Map.of(spirit.id(), spirit));
    }

    private static GraphFacts.Spirit spirit(boolean stuck, boolean trapped, String trap) {
        return new GraphFacts.Spirit(
                SPIRIT.toString(),
                new GraphFacts.Position(0, 64, 0),
                20,
                20,
                null,
                20,
                300,
                false,
                trapped,
                stuck,
                null,
                Map.of(),
                List.of(),
                List.of(),
                trap);
    }
}
