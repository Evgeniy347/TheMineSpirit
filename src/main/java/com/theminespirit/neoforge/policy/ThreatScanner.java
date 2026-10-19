package com.theminespirit.neoforge.policy;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.ExplosionAvoidance;
import com.theminespirit.core.policy.ExplosionAvoidance.ThreatFact;
import com.theminespirit.core.policy.ExplosionAvoidance.ThreatType;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.AABB;

final class ThreatScanner {
    static final double RADIUS = ExplosionAvoidance.SCAN_RADIUS;

    private ThreatScanner() {}

    static List<ThreatFact> scan(SpiritEntity spirit) {
        AABB area = spirit.getBoundingBox().inflate(RADIUS);
        List<Entity> found = spirit.level()
                .getEntities(spirit, area, entity -> entity instanceof Creeper || entity instanceof PrimedTnt);
        List<ThreatFact> facts = new ArrayList<>(found.size());
        for (Entity entity : found) {
            BlockPos at = entity.blockPosition();
            BlockCoord pos = new BlockCoord(at.getX(), at.getY(), at.getZ());
            if (entity instanceof Creeper creeper) {
                ThreatType type = creeper.isPowered() ? ThreatType.CHARGED_CREEPER : ThreatType.CREEPER;
                facts.add(new ThreatFact(type, pos, creeper.getSwelling(1.0F) > 0.0F));
            } else {
                facts.add(new ThreatFact(ThreatType.PRIMED_TNT, pos, false));
            }
        }
        return facts;
    }
}
