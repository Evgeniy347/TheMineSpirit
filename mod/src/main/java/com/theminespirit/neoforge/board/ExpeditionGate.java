package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.StockView;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.night.NightLeash;
import net.minecraft.server.level.ServerLevel;

final class ExpeditionGate {
    private ExpeditionGate() {}

    static boolean ready(StockView stock, ServerLevel level) {
        IntellectTuning tuning = IntellectTuning.loadDefault();
        double day = Math.floorMod(level.getDayTime(), 24000L) / 24000.0;
        if (level.isNight() || NightLeash.withDefaultRules().isEvening(day)) {
            return false;
        }
        int ore = stock.available().getOrDefault("cobblestone", 0)
                + stock.available().getOrDefault("raw_iron", 0);
        int iron = stock.available().getOrDefault("iron_ingot", 0);
        return ore < tuning.oreTarget() || iron < tuning.ironTarget();
    }
}
