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
                    "Пока дух вращается, эпизод ещё открыт: событие пишется только по завершении"
                            + " Иначе тест turningInPlace_FullTurn_ReportsTurnEpisode перестанет ловить эту поломку. При смене turningInPlace_FullTurn_ReportsTurnEpisode проверь это утверждение.");
        }
        SpinDetector.Spin spin = null;
        for (int tick = 40; tick < 90 && spin == null; tick++) {
            spin = detector.feed(tick, 0.0, 0.0, 39 * 24.0f);
        }
        assertEquals(
                SpinDetector.Kind.TURN,
                spin == null ? null : spin.kind(),
                "Вращение на месте больше 270° обязано дать эпизод вращения, а не пусто"
                        + " Иначе тест turningInPlace_FullTurn_ReportsTurnEpisode перестанет ловить эту поломку. При смене turningInPlace_FullTurn_ReportsTurnEpisode проверь это утверждение.");
        assertTrue(
                spin != null && spin.degrees() >= 270.0f,
                "Угол эпизода не меньше порога: " + (spin == null ? "эпизода нет" : spin.degrees())
                        + " Иначе тест turningInPlace_FullTurn_ReportsTurnEpisode перестанет ловить эту поломку. При смене turningInPlace_FullTurn_ReportsTurnEpisode проверь это утверждение.");
    }

    @Test
    void tremblingBetweenTwoPoints_ZeroNetTurn_IsNotSpin() {
        SpinDetector detector = new SpinDetector();
        for (int tick = 0; tick < 120; tick++) {
            double x = tick % 2 == 0 ? 0.0 : 0.1;
            float yaw = tick % 2 == 0 ? 85.0f : 95.0f;
            assertNull(
                    detector.feed(tick, x, 0.0, yaw),
                    "Дёрганье туда-сюда на месте не кружение: нетто-поворот близок к нулю"
                            + " Иначе тест tremblingBetweenTwoPoints_ZeroNetTurn_IsNotSpin перестанет ловить эту поломку. При смене tremblingBetweenTwoPoints_ZeroNetTurn_IsNotSpin проверь это утверждение.");
        }
    }

    @Test
    void walkingStraight_ConstantCourse_ReportsNothing() {
        SpinDetector detector = new SpinDetector();
        for (int tick = 0; tick < 120; tick++) {
            assertNull(
                    detector.feed(tick, tick * 0.2, 0.0, 90.0f),
                    "Ровный шаг с постоянным курсом — не кружение"
                            + " Иначе тест walkingStraight_ConstantCourse_ReportsNothing перестанет ловить эту поломку. При смене walkingStraight_ConstantCourse_ReportsNothing проверь это утверждение.");
        }
    }

    @Test
    void walkingAroundOneBlock_SingleLoop_IsNotSpin() {
        SpinDetector detector = new SpinDetector();
        for (int tick = 0; tick < 90; tick++) {
            double angle = Math.toRadians(tick * 6.0);
            assertNull(
                    detector.feed(tick, 0.5 * Math.cos(angle), 68.0, 0.5 * Math.sin(angle), tick * 6.0f),
                    "Один оборот вокруг препятствия — рабочий обход, а не кружение: дух проходит мимо столба "
                            + "или ствола и идёт дальше (окно 52, spirit-3: 364° за 47 тиков, путь 3.21, "
                            + "shift 0.97, nav=идёт). Иначе каждый обход даёт ложный эпизод. При смене порога "
                            + "проверь многократный витк: два оборота обязаны остаться эпизодом");
        }
    }

    @Test
    void spiralStairs_RisingCircle_IsNotSpin() {
        SpinDetector detector = new SpinDetector();
        for (int tick = 0; tick < 200; tick++) {
            double angle = Math.toRadians(tick * 24.0);
            assertNull(
                    detector.feed(tick, 0.3 * Math.cos(angle), tick * 0.05, 0.3 * Math.sin(angle), tick * 24.0f),
                    "Подъём по винтовой лестнице — это движение с набором высоты, а не кружение на ровном месте: "
                            + "иначе каждый подъём по спирали (spirit-6, окно 50: шаги y 70→73 у -164,73,-492) "
                            + "даёт эпизод CIRCLE, хотя дух идёт по маршруту dig_out. При смене порога высоты "
                            + "проверь ровный круг: он обязан остаться эпизодом");
        }
    }

    @Test
    void flatCircle_OnLevelGround_ReportsCircleEpisode() {
        SpinDetector detector = new SpinDetector();
        for (int tick = 0; tick < 90; tick++) {
            double angle = Math.toRadians(tick * 15.0);
            detector.feed(tick, 0.3 * Math.cos(angle), 68.0, 0.3 * Math.sin(angle), tick * 15.0f);
        }
        SpinDetector.Spin spin = null;
        for (int tick = 90; tick < 200 && spin == null; tick++) {
            spin = detector.feed(tick, 0.3, 68.0, 0.0, 1080.0f);
        }
        assertEquals(
                SpinDetector.Kind.CIRCLE,
                spin == null ? null : spin.kind(),
                "Круг по ровной площадке обязан остаться эпизодом CIRCLE: иначе учёт высоты скроет настоящее "
                        + "кружение (окно 48, spirit-4: путь 15.85 при сдвиге 0.97 на одной высоте). При смене "
                        + "порога высоты проверь спираль лестницы: её обрывать нельзя");
    }
}
