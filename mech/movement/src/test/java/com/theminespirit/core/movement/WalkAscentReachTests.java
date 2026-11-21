package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.movement.WalkAscent.Body;
import com.theminespirit.core.movement.WalkAscent.Command;
import com.theminespirit.core.movement.WalkAscent.Pose;
import com.theminespirit.core.movement.WalkAscent.Step;
import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class WalkAscentReachTests {
    private static final Pose FEET = new Pose(new BlockCoord(0, 0, 0), 0.5, 0.0, 0.5);
    private static final BlockCoord TWO_ABOVE = new BlockCoord(1, 2, 0);

    @Test
    void next_StepTwoAboveOnGround_IsImpossible() {
        Command command = WalkAscent.next(TWO_ABOVE, FEET, new Body(true, false, false));

        assertEquals(
                Step.IMPOSSIBLE,
                command.step(),
                "Маршрут стоит на клетке на 4 блока выше (дух сорвался в яму, маршрут устарел): прыжок "
                        + "на +1 не берёт перепад, дух 60 тиков прыгает на месте и крутится — иначе маршрут "
                        + "повиснет на устаревшей цели, а шаг обязан сразу отвергаться. При смене правила выбора "
                        + "Step в WalkAscent.next проверь WalkAscentStepTests и живой случай срыва со ступени "
                        + "20.11: вместо отказа шаг не должен снова становиться JUMP");
    }

    @Test
    void next_StepOneAboveOnGround_Jumps() {
        Command command = WalkAscent.next(new BlockCoord(0, 1, 0), FEET, new Body(true, false, false));

        assertEquals(
                Step.JUMP,
                command.step(),
                "Обычная ступень на один блок вверх берётся прыжком: иначе дух перестанет подниматься по "
                        + "ступеням, застрянет у каждого бордюра и обойдёт сад кругом. При смене правила выбора "
                        + "Step проверь WalkAscentReachTests.next_StepTwoAboveOnGround_IsImpossible: +1 — JUMP, "
                        + "+2 без воды и лестницы — IMPOSSIBLE");
    }

    @Test
    void next_StepTwoAboveInWater_Climbs() {
        Command command = WalkAscent.next(TWO_ABOVE, FEET, new Body(true, true, false));

        assertEquals(
                Step.CLIMB,
                command.step(),
                "В воде подъём на несколько блоков берётся всплытием: иначе дух прыжком не поднимется на два "
                        + "блока и застрянет под кромкой воды. При смене lift() проверь WaterStepLiveTests и "
                        + "живые всплытия из ям с водой: JUMP там не работает, нужен CLIMB");
    }

    @Test
    void next_StepTwoAboveOnLadder_Climbs() {
        Command command = WalkAscent.next(TWO_ABOVE, FEET, new Body(true, false, true));

        assertEquals(
                Step.CLIMB,
                command.step(),
                "По лестнице подъём на несколько блоков берётся карабканьем: иначе дух прыжком не поднимется "
                        + "на два блока по лестнице и зависнет на нижней ступени. При смене lift() проверь "
                        + "ClimbRouteTests и живые подъёмы по лестнице из шахты: там нужен CLIMB, а не JUMP");
    }

    @Test
    void next_StepTwoBelowOnGround_Dives() {
        Command command = WalkAscent.next(new BlockCoord(0, -2, 0), FEET, new Body(true, false, false));

        assertEquals(
                Step.DIVE,
                command.step(),
                "Спуск на два блока на суше — прыжок вниз-вперёд: иначе дух толкается в бортик уступа "
                        + "60 тиков без движения и маршрут сходит с него (окно 4, spirit-4 у -167,72,-490). "
                        + "При смене lift() или Step.MOVE проверь спуск по уступам шахты и безопасность падения "
                        + "в DropProfile: там урон на 2 блока не смертелен");
    }

    @Test
    void next_AirborneOverCell_Arrives() {
        Pose over = new Pose(new BlockCoord(0, 0, 0), 0.27, 0.0, 0.5);
        Command command = WalkAscent.next(new BlockCoord(0, 0, 0), over, new Body(false, false, false));

        assertEquals(
                Step.ARRIVED,
                command.step(),
                "Дух над центром клетки без опоры уже пришёл: иначе на завершающем шаге у края он висит "
                        + "60 тиков с дистанцией 0.23 и маршрут сходит с него (окно 30, spirit-6 у -174,71,-489). "
                        + "При смене arrived проверь живой сход с уступа: дух не ждёт опоры под центром клетки");
    }

    @Test
    void next_AirborneBesidesCell_Moves() {
        Pose beside = new Pose(new BlockCoord(0, 0, 0), 0.9, 0.0, 0.5);
        Command command = WalkAscent.next(new BlockCoord(0, 0, 0), beside, new Body(false, false, false));

        assertEquals(
                Step.MOVE,
                command.step(),
                "Дух в воздухе в стороне от центра той же клетки ещё не пришёл: иначе шаг засчитался бы "
                        + "раньше времени и путь пошёл бы от чужой точки. При смене arrived проверь пролёт "
                        + "над клеткой: приход только у центра");
    }

    @Test
    void next_StepTwoBelowWithBlockedLane_IsDescentBlocked() {
        Command command = WalkAscent.next(new BlockCoord(0, -2, 0), FEET, new Body(true, false, false, false, true));

        assertEquals(
                Step.DESCENT_BLOCKED,
                command.step(),
                "Спуск на два блока с блоком на линии сразу отвергается: иначе дух толкается в бортик уступа "
                        + "60 тиков, а маршрут сходит с него (окно 31, spirit у -171,73,-489, между=блок). "
                        + "При смене правила спуска проверь живой сход с уступа: отказ сразу, обход с копанием");
    }

    @Test
    void next_StepTwoBelowWithClearLane_Dives() {
        Command command = WalkAscent.next(new BlockCoord(0, -2, 0), FEET, new Body(true, false, false, false, false));

        assertEquals(
                Step.DIVE,
                command.step(),
                "Чистая линия спуска сохраняет прыжок вниз: иначе дух перестал бы спускаться с уступов, где "
                        + "ничего не мешает, и застрял бы наверху. При смене правила спуска проверь свободный "
                        + "сход с уступа: DIVE без отказа");
    }

    @Test
    void next_StepOneBelowOnGround_Moves() {
        Command command = WalkAscent.next(new BlockCoord(0, -1, 0), FEET, new Body(true, false, false));

        assertEquals(
                Step.MOVE,
                command.step(),
                "Спуск на один блок не требует прыжка: иначе дух будет прыгать с каждого бордюра и терять "
                        + "равновесие на ступенях. При смене lift() проверь спуск в яму dig_out и сход со "
                        + "ступени: шаг на -1 остаётся MOVE");
    }
}
