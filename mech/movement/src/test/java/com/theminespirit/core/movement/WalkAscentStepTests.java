package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.movement.api.CellCentering;
import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class WalkAscentStepTests {
    private static final BlockCoord STEP_UP = new BlockCoord(1, 1, 0);
    private static final double HALF_WIDTH = 0.3;
    private static final double MOVE = 0.2;
    private static final double GRAVITY = 0.08;
    private static final double JUMP_SPEED = 0.42;
    private static final int JUMP_DELAY = 10;
    private static final int TICKS = 40;

    @Test
    void next_JumpTwoCellsAway_IsImpossible() {
        WalkAscent.Command command = WalkAscent.next(
                new BlockCoord(0, 1, 2),
                new WalkAscent.Pose(new BlockCoord(0, 0, 0), 0.5, 0.0, 0.5),
                new WalkAscent.Body(true, false, false));
        assertEquals(
                WalkAscent.Step.IMPOSSIBLE,
                command.step(),
                "Прыжок через две клетки не берётся: дух прыгает у края на месте и обрывается по StallWatch "
                        + "(окно 54, spirit-2: шаг WALK в -163,71,-491 из -163,70,-489 — два блока по z). Иначе "
                        + "маршрут ведёт его в невозможный шаг и он топчется. При смене шага проверь прыжок на "
                        + "соседнюю клетку выше: он обязан остаться JUMP");
    }

    @Test
    void next_JumpNeighbourAbove_StaysJump() {
        WalkAscent.Command command = WalkAscent.next(
                new BlockCoord(0, 1, 1),
                new WalkAscent.Pose(new BlockCoord(0, 0, 0), 0.5, 0.0, 0.5),
                new WalkAscent.Body(true, false, false));
        assertEquals(
                WalkAscent.Step.JUMP,
                command.step(),
                "Соседняя клетка на блок выше берётся прыжком: иначе дух перестанет подниматься по ступеням "
                        + "поселения и шахты. При смене порога дальности проверь живой подъём на ступень: JUMP "
                        + "выполняется");
    }

    @Test
    void next_StepUpOffCenterBesideWall_AlignsAndArrives() {
        assertTrue(
                arrives(0.9),
                "Шаг вверх с края клетки у стены: старый RouteWalk прыгает сразу и бьётся углом о ступень "
                        + "(лог N1: воздух, onGround=false, дистанция 0.68, код JUMP_STALLED). Дух обязан сначала "
                        + "выровняться к центру клетки, затем прыгнуть — иначе задание падает через 60 тиков"
                        + " При смене next_StepUpOffCenterBesideWall_AlignsAndArrives проверь это утверждение.");
    }

    @Test
    void next_StepUpFromCellCenter_ArrivesWithoutDelay() {
        assertTrue(
                arrives(0.5),
                "Шаг вверх из центра клетки обязан проходить без выравнивания: дух не тратит тики на возврат "
                        + "к центру, когда уже стоит ровно перед ступенью"
                        + " Иначе тест next_StepUpFromCellCenter_ArrivesWithoutDelay перестанет ловить эту поломку. При смене next_StepUpFromCellCenter_ArrivesWithoutDelay проверь это утверждение.");
    }

    private static boolean arrives(double startZ) {
        double x = 0.5;
        double y = 0.0;
        double z = startZ;
        double vy = 0.0;
        boolean onGround = true;
        boolean airborneAligned = false;
        int jumpDelay = 0;
        for (int tick = 0; tick < TICKS; tick++) {
            WalkAscent.Command command = WalkAscent.next(
                    STEP_UP, new WalkAscent.Pose(feet(x, y, z), x, y, z), new WalkAscent.Body(onGround, false, false));
            if (command.step() == WalkAscent.Step.ARRIVED) {
                return true;
            }
            boolean aligning = toCellCenter(command, x, z);
            if (aligning || onGround || airborneAligned) {
                double dx = command.x() - x;
                double dz = command.z() - z;
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > 1e-9) {
                    double scale = Math.min(MOVE, distance) / distance;
                    x += dx * scale;
                    z += dz * scale;
                }
                if (y < STEP_UP.y() && x > STEP_UP.x() - HALF_WIDTH) {
                    x = STEP_UP.x() - HALF_WIDTH;
                }
            }
            if (jumpDelay > 0) {
                jumpDelay--;
            }
            if (command.step() == WalkAscent.Step.JUMP && onGround && jumpDelay == 0) {
                airborneAligned = Math.abs(z - 0.5) <= CellCentering.TOLERANCE;
                vy = JUMP_SPEED;
                onGround = false;
                jumpDelay = JUMP_DELAY;
            }
            if (!onGround) {
                y += vy;
                vy -= GRAVITY;
                double floor = floorAt(x, z);
                if (y <= floor) {
                    y = floor;
                    vy = 0.0;
                    onGround = true;
                    airborneAligned = false;
                }
            }
        }
        return false;
    }

    private static boolean toCellCenter(WalkAscent.Command command, double x, double z) {
        return Math.abs(command.x() - (floor(x) + 0.5)) < 1e-9 && Math.abs(command.z() - (floor(z) + 0.5)) < 1e-9;
    }

    private static BlockCoord feet(double x, double y, double z) {
        return new BlockCoord(floor(x), floor(y), floor(z));
    }

    private static double floorAt(double x, double z) {
        return floor(x) == 1 && floor(z) == 0 ? 1.0 : 0.0;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
