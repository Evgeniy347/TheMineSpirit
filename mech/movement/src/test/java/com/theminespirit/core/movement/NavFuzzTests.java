package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class NavFuzzTests {
    private static final int WORLDS = 2_000;

    @Test
    void navFuzz_GeneratedWorlds_EveryPlanExecutableByBody() {
        List<String> failures = new ArrayList<>();
        for (long seed = 0; seed < WORLDS; seed++) {
            FuzzWorld world = FuzzWorld.generate(seed);
            NavFuzzChecks.checkSurfaceEscape(seed, world, failures);
            NavFuzzChecks.checkPitExit(seed, world, failures);
            NavFuzzChecks.checkRoutePlanner(seed, world, failures);
            NavFuzzChecks.checkSelfEscape(seed, world, failures);
            NavEscapeChecks.checkSealedSpace(seed, world, failures);
            NavEscapeChecks.checkPitWalkOut(seed, world, failures);
            NavEscapeChecks.checkNavPillar(seed, world, failures);
        }
        if (!failures.isEmpty()) {
            fail(report(failures));
        }
    }

    private static String report(List<String> failures) {
        StringBuilder message =
                new StringBuilder("Навигационный фаззинг: миров " + WORLDS + ", расхождений " + failures.size()
                        + ". План, который тело не исполняет, — сломанный план: чиню планировщик, а не тест.");
        int shown = 0;
        Set<Long> reportedSeeds = new HashSet<>();
        for (String failure : failures) {
            long seed = Long.parseLong(failure.substring(0, failure.indexOf(' ')));
            if (!reportedSeeds.add(seed)) {
                continue;
            }
            if (shown++ < 3) {
                message.append("\n\n")
                        .append(failure)
                        .append('\n')
                        .append(FuzzWorld.generate(seed).render());
            } else {
                message.append("\nсид ").append(seed).append(": ").append(failure);
            }
        }
        return message.toString();
    }
}
