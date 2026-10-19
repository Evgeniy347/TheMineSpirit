package com.theminespirit.neoforge.defense;

import com.theminespirit.core.intellect.defense.DefenderCapability;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;

public final class DefenderReader {
    private DefenderReader() {}

    public static Optional<DefenderCapability> read(SpiritEntity spirit) {
        if (spirit == null || spirit.isRemoved()) {
            return Optional.empty();
        }
        double maxHealth = spirit.getMaxHealth();
        double hpFraction = maxHealth <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, spirit.getHealth() / maxHealth));
        int armor = Math.max(0, Math.min(DefenderCapability.MAX_ARMOR, spirit.getArmorValue()));
        return Optional.of(new DefenderCapability(
                spirit.getUUID(),
                new Point(spirit.getX(), spirit.getY(), spirit.getZ()),
                hpFraction,
                armor,
                spirit.gear().hasBow(),
                spirit.gear().meleeDamagePerSecond(),
                spirit.gear().healItems() > 0));
    }
}
