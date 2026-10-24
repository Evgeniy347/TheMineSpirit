package com.theminespirit.neoforge.board;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.Nullable;

/** Якорь участка поселения в мире (INC-55, INC-56): центр и высота фиксируются один раз и переживают рестарт. */
public final class SettlementPlotStore extends SavedData {
    private static final String DATA_NAME = ModIdentity.MOD_ID + "_settlement_plot";
    private static final String X = "X";
    private static final String Y = "Y";
    private static final String Z = "Z";
    private static final String MIN = "Min";
    private static final String MAX = "Max";
    private static final String COLUMNS = "Columns";
    private static final SavedData.Factory<SettlementPlotStore> FACTORY =
            new SavedData.Factory<>(SettlementPlotStore::new, SettlementPlotStore::load);

    private @Nullable SettlementPlot plot;

    private SettlementPlotStore() {}

    public static SettlementPlotStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Optional<SettlementPlot> plot() {
        return Optional.ofNullable(plot);
    }

    public void fix(SettlementPlot next) {
        plot = Objects.requireNonNull(next, "Участок не может быть null: фиксируется обмеренный участок");
        setDirty();
    }

    private static SettlementPlotStore load(CompoundTag tag, HolderLookup.Provider registries) {
        SettlementPlotStore store = new SettlementPlotStore();
        if (tag.contains(COLUMNS)) {
            store.plot = new SettlementPlot(
                    new BlockCoord(tag.getInt(X), tag.getInt(Y), tag.getInt(Z)),
                    tag.getInt(MIN),
                    tag.getInt(MAX),
                    tag.getInt(COLUMNS));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        SettlementPlot current = plot;
        if (current != null) {
            tag.putInt(X, current.anchor().x());
            tag.putInt(Y, current.anchor().y());
            tag.putInt(Z, current.anchor().z());
            tag.putInt(MIN, current.min());
            tag.putInt(MAX, current.max());
            tag.putInt(COLUMNS, current.columns());
        }
        return tag;
    }
}
