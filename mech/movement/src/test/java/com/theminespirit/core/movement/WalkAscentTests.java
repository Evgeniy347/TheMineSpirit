package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WalkAscentTests {
    @Test
    void lift_LandStepUp_Jumps() {
        assertEquals(
                WalkAscent.Lift.JUMP,
                WalkAscent.lift(true, new WalkAscent.Body(true, false, false)),
                "Шаг на блок выше с земли обязан быть прыжком: без него дух упирается в ступень, как spirit-1 у лианы");
    }

    @Test
    void lift_OnClimbable_Lifts() {
        assertEquals(
                WalkAscent.Lift.CLIMB,
                WalkAscent.lift(true, new WalkAscent.Body(false, false, true)),
                "Дух уже в лиане или на лестнице: подъём идёт лазанием, а не прыжком");
    }

    @Test
    void lift_InWater_Lifts() {
        assertEquals(
                WalkAscent.Lift.CLIMB,
                WalkAscent.lift(true, new WalkAscent.Body(false, true, false)),
                "В воде шаг вверх — всплытие: вода поднимает духа без прыжка");
    }

    @Test
    void lift_AirborneOffClimbable_RetriesJump() {
        assertEquals(
                WalkAscent.Lift.JUMP,
                WalkAscent.lift(true, new WalkAscent.Body(false, false, false)),
                "В воздухе на шаге вверх прыжок запрашивается снова: Minecraft применит его в тик приземления, "
                        + "иначе дух прыгает один раз, не дотягивает и висит в воздухе до JUMP_STALLED (N1)");
    }

    @Test
    void lift_FlatStep_Holds() {
        assertEquals(
                WalkAscent.Lift.HOLD,
                WalkAscent.lift(false, new WalkAscent.Body(true, false, false)),
                "Ровный шаг не поднимает духа: прыжок на ровном месте тратит голод и сбивает маршрут");
    }
}
