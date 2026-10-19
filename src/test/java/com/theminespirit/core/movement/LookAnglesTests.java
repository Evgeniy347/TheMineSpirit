package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LookAnglesTests {
    private static final float EPS = 1.0E-3F;

    @Test
    void toward_South_YawZeroPitchZero() {
        LookAngles angles = LookAngles.toward(0, 0, 5);

        assertEquals(0.0F, angles.yaw(), EPS, "Юг (+Z) в Minecraft — рысканье 0°: иначе дух смотрит мимо блока");
        assertEquals(0.0F, angles.pitch(), EPS, "Блок на уровне глаз — тангаж 0°");
    }

    @Test
    void toward_West_YawNinety() {
        assertEquals(
                90.0F,
                LookAngles.toward(-3, 0, 0).yaw(),
                EPS,
                "Запад (−X) в Minecraft — рысканье 90°: перепутанный знак разворачивает голову от блока");
    }

    @Test
    void toward_East_YawMinusNinety() {
        assertEquals(-90.0F, LookAngles.toward(4, 0, 0).yaw(), EPS, "Восток (+X) — рысканье −90°");
    }

    @Test
    void toward_BlockBelowFeet_PitchPositiveDown() {
        LookAngles angles = LookAngles.toward(1, -1, 0);

        assertEquals(
                45.0F,
                angles.pitch(),
                EPS,
                "Блок ниже глаз — тангаж положителен (взгляд вниз), под 45° при равных смещениях: иначе дух глядит в небо");
    }

    @Test
    void toward_StraightUp_PitchMinusNinety() {
        assertEquals(-90.0F, LookAngles.toward(0, 2, 0).pitch(), EPS, "Блок прямо над головой — тангаж −90°");
    }
}
