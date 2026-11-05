package com.theminespirit.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NearestTrackerTests {
    private static final BlockCoord ORIGIN = new BlockCoord(10, 64, 10);

    static Stream<Arguments> scenes() {
        BlockCoord near = new BlockCoord(12, 64, 10);
        BlockCoord far = new BlockCoord(20, 64, 10);
        BlockCoord alsoNear = new BlockCoord(8, 64, 10);
        return Stream.of(
                Arguments.of("нет кандидатов", List.of(), Optional.empty()),
                Arguments.of("один кандидат", List.of(far), Optional.of(far)),
                Arguments.of("ближний предложен первым", List.of(near, far), Optional.of(near)),
                Arguments.of("ближний предложен последним", List.of(far, near), Optional.of(near)),
                Arguments.of("равное расстояние: меньшая координата", List.of(near, alsoNear), Optional.of(alsoNear)),
                Arguments.of("равное расстояние в другом порядке", List.of(alsoNear, near), Optional.of(alsoNear)),
                Arguments.of("клетка под духом", List.of(far, ORIGIN, near), Optional.of(ORIGIN)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenes")
    void best_OfferedCandidates_IsNearestWithStableTieBreak(
            String scene, List<BlockCoord> offered, Optional<BlockCoord> expected) {
        NearestTracker tracker = new NearestTracker(ORIGIN);

        offered.forEach(tracker::offer);

        assertEquals(
                expected,
                tracker.best(),
                scene + ": ближайший блок обязан выбираться по расстоянию, а при равенстве — по координате, иначе "
                        + "locate_* отдаёт разные ответы при разном порядке срезов");
    }
}
