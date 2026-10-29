package com.theminespirit.neoforge.board;

import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

final class SpiritCensus {
    private SpiritCensus() {}

    static List<SpiritEntity> all(ServerLevel level) {
        List<SpiritEntity> spirits = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof SpiritEntity spirit && !spirit.isRemoved() && spirit.isAlive() && !spirit.isDemo()) {
                spirits.add(spirit);
            }
        }
        return spirits;
    }
}
