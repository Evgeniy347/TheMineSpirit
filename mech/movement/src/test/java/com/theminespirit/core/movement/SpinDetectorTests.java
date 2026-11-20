package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpinDetectorTests {
    @Test
    void turningInPlace_FullTurn_ReportsTurnEpisode() {
        SpinDetector detector = new SpinDetector();
        for (int tick = 0; tick < 40; tick++) {
            assertNull(
                    detector.feed(tick, 0.0, 0.0, tick * 24.0f),
                    "Пока дух вращается, эпизод ещё открыт: событие пишется только по завершении");
        }
        SpinDetector.Spin spin = null;
        for (int tick = 40; tick < 90 && spin == null; tick++) {
            spin = detector.feed(tick, 0.0, 0.0, 39 * 24.0f);
        }
        assertEquals(
                SpinDetector.Kind.TURN,
                spin == null ? null : spin.kind(),
                "Вращение на месте больше 270° обязано дать эпизод вращения, а не пусто");
        assertTrue(
                spin != null && spin.degrees() >= 270.0f,
                "Угол эпизода не меньше порога: " + (spin == null ? "эпизода нет" : spin.degrees()));
    }

    @Test
    void tremblingBetweenTwoPoints_ZeroNetTurn_IsNotSpin() {
        SpinDetector detector = new SpinDetector();
        for (int tick = 0; tick < 120; tick++) {
            double x = tick % 2 == 0 ? 0.0 : 0.1;
            float yaw = tick % 2 == 0 ? 85.0f : 95.0f;
            assertNull(
                    detector.feed(tick, x, 0.0, yaw),
                    "Дёрганье туда-сюда на месте не кружение: нетто-поворот близок к нулю");
        }
    }

    @Test
    void walkingStraight_ConstantCourse_ReportsNothing() {
        SpinDetector detector = new SpinDetector();
        for (int tick = 0; tick < 120; tick++) {
            assertNull(detector.feed(tick, tick * 0.2, 0.0, 90.0f), "Ровный шаг с постоянным курсом — не кружение");
        }
    }
}
