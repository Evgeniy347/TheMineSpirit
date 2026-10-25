package com.theminespirit.neoforge.crater;

import com.theminespirit.core.building.Crater;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.FillCraterParams;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

public final class CraterResolver {
    private CraterResolver() {}

    public static Crater resolve(SpiritEntity spirit, FillCraterParams params) {
        ServerLevel level = (ServerLevel) spirit.level();
        CraterStore store = CraterStore.get(level);
        Optional<Crater> found = switch (params) {
            case FillCraterParams.ById byId -> store.byId(byId.id());
            case FillCraterParams.ByPosition position ->
                store.nearest(new BlockCoord(position.x(), position.y(), position.z()));
        };
        return found.orElseThrow(() -> new IllegalArgumentException(describeMiss(params, store.size())));
    }

    private static String describeMiss(FillCraterParams params, int known) {
        String where = switch (params) {
            case FillCraterParams.ById byId -> "id " + byId.id();
            case FillCraterParams.ByPosition position ->
                "позиции " + position.x() + "," + position.y() + "," + position.z();
        };
        return "Воронка по " + where + " не найдена: запомнено воронок " + known + ", список — /spirit craters";
    }
}
