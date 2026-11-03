package com.theminespirit.neoforge.board;

import com.theminespirit.core.mining.LeftOreRules;
import com.theminespirit.core.mining.MineOres;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.MineOreLedger;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class LeftOreWatch {
    static final String PREFIX = "left_ore:";

    private LeftOreWatch() {}

    public static Set<String> tick(ServerLevel level) {
        MineOreLedger ledger = MineOreLedger.get(level);
        Set<String> live = new HashSet<>();
        for (Map.Entry<BlockCoord, MineOreLedger.Left> ore : ledger.ores().entrySet()) {
            if (live(level, ledger, ore.getKey())) {
                live.add(id(ore.getKey()));
            }
        }
        return live;
    }

    static Map<BlockCoord, MineOreLedger.Left> find(ServerLevel level, String item) {
        MineOreLedger ledger = MineOreLedger.get(level);
        Map<BlockCoord, MineOreLedger.Left> found = new LinkedHashMap<>();
        for (Map.Entry<BlockCoord, MineOreLedger.Left> ore : ledger.ores().entrySet()) {
            if (!live(level, ledger, ore.getKey())) {
                continue;
            }
            if (item.equals(LeftOreRules.itemOf(ore.getValue().block()))) {
                found.put(ore.getKey(), ore.getValue());
            }
        }
        return found;
    }

    private static boolean live(ServerLevel level, MineOreLedger ledger, BlockCoord pos) {
        BlockPos block = new BlockPos(pos.x(), pos.y(), pos.z());
        if (!level.isLoaded(block)) {
            return true;
        }
        if (MineOres.kindOf(BlockStates.idOf(level.getBlockState(block))) == MineOres.Kind.NONE) {
            ledger.forget(pos);
            return false;
        }
        return true;
    }

    public static String id(BlockCoord pos) {
        return PREFIX + pos.x() + "," + pos.y() + "," + pos.z();
    }
}
