package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ScriptCall;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FreshSuppliesTests {
    private static final AcquireSupplies CHESTS = new AcquireSupplies() {
        @Override
        public int inInventory(String item) {
            return 0;
        }

        @Override
        public List<ScriptCall> withdraw(String item, int maxCount) {
            return List.of(new ScriptCall("withdraw", Map.of("item", item, "count", maxCount)));
        }

        @Override
        public Map<String, Integer> inventory() {
            return Map.of();
        }

        @Override
        public Set<String> nearbyStations() {
            return Set.of();
        }
    };

    @Test
    void withdraw_TargetItem_NotFromChests() {
        FreshSupplies fresh = new FreshSupplies(CHESTS, "minecraft:jungle_planks");
        assertTrue(
                fresh.withdraw("jungle_planks", 13).isEmpty(),
                "добыча «ещё 13 досок» не берёт доски из сундука, куда их же сдаёт: запас не рос (24.10)");
        assertEquals(
                1,
                fresh.withdraw("jungle_log", 4).size(),
                "ингредиенты из сундука берутся: брёвна склада на доски — новые доски");
    }
}
