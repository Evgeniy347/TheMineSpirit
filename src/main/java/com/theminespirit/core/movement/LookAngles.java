package com.theminespirit.core.movement;

/**
 * Углы взгляда на точку в соглашении Minecraft: рысканье {@code yaw} 0° — на юг (+Z), 90° — на запад (−X); тангаж
 * {@code pitch} положителен вниз. Дух поворачивает голову к блоку, который ставит или ломает: игрок видит, над чем
 * дух работает.
 */
public record LookAngles(float yaw, float pitch) {
    public static LookAngles toward(double dx, double dy, double dz) {
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = horizontal < 1.0E-7 ? 0.0F : (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new LookAngles(wrap(yaw), pitch);
    }

    private static float wrap(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }
}
