package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BuildingWoods;
import com.theminespirit.core.board.SettlementLevel;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageSites;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownPlanCodec;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class SettlementPlotStore extends SavedData {
    private static final String DATA_NAME = ModIdentity.MOD_ID + "_settlement_plot";
    private static final String X = "X";
    private static final String Y = "Y";
    private static final String Z = "Z";
    private static final String MIN = "Min";
    private static final String MAX = "Max";
    private static final String COLUMNS = "Columns";
    private static final String WOOD = "Wood";
    private static final String LEVELED = "Leveled";
    private static final String LEVEL_RULE = "LevelRule";
    private static final String TOWN = "Town";
    private static final String BUILDING_WOOD = "BuildingWood";
    private static final String HOME_X = "HomeX";
    private static final String HOME_Y = "HomeY";
    private static final String HOME_Z = "HomeZ";
    private static final int FIRST_STAGE = 1;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SavedData.Factory<SettlementPlotStore> FACTORY =
            new SavedData.Factory<>(SettlementPlotStore::new, SettlementPlotStore::load);

    private @Nullable SettlementPlot plot;
    private @Nullable TownPlan town;
    private @Nullable BlockCoord home;
    private final Map<Integer, StageSite> sites = new HashMap<>();
    private boolean leveled;
    private String levelRule = "";
    private BuildingWoods woods = BuildingWoods.NONE;

    private SettlementPlotStore() {}

    public static SettlementPlotStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Optional<SettlementPlot> plot() {
        return Optional.ofNullable(plot);
    }

    public Optional<TownPlan> town() {
        return Optional.ofNullable(town);
    }

    public Optional<BlockCoord> home() {
        return Optional.ofNullable(home);
    }

    public void rememberHome(BlockCoord stand) {
        Objects.requireNonNull(stand, "Домашняя стоянка не может быть null: запоминать нечего");
        if (!stand.equals(home)) {
            home = stand;
            setDirty();
        }
    }

    public void forgetHome() {
        if (home != null) {
            home = null;
            setDirty();
        }
    }

    public void fixTown(TownPlan next) {
        town = Objects.requireNonNull(next, "План поселения не может быть null: фиксируется размеченный план");
        plot = null;
        forgetHome();
        sites.clear();
        leveled = false;
        levelRule = "";
        woods = BuildingWoods.NONE;
        setDirty();
    }

    public void refitTown(TownPlan next) {
        town = Objects.requireNonNull(next, "План поселения не может быть null: фиксируется расширенный план");
        forgetHome();
        sites.clear();
        setDirty();
    }

    public Optional<StageSite> site() {
        return site(FIRST_STAGE);
    }

    static int currentStage(BoardService service) {
        int stages = DataTables.INSTANCE.stages().current().stages().size();
        return Math.max(FIRST_STAGE, Math.min(service.board().ledger().stage(), stages));
    }

    public static Optional<StageSite> liveSite(ServerLevel level) {
        return get(level).site(currentStage(BoardRegistry.get(level)));
    }

    public Optional<StageSite> site(int stage) {
        if (stage < 1) {
            throw new IllegalArgumentException("Этап " + stage + ": этапы считаются с первого");
        }
        SettlementPlot current = plot;
        TownPlan plan = town;
        if (current == null || plan == null) {
            return Optional.empty();
        }
        StageSite cached = sites.get(stage);
        if (cached == null || cached.anchor().y() != current.anchor().y()) {
            Optional<StageSite> found = SettlementLevel.site(plan, stage, current);
            if (found.isEmpty()) {
                return Optional.empty();
            }
            cached = found.orElseThrow();
            if (!StageSites.insideEnclosure(cached)) {
                LOGGER.warn("[plot] место этапа вышло за ограду: участок обрезан контуром поселения — "
                        + "проверьте план и ограду");
            }
            sites.put(stage, cached);
        }
        return Optional.of(cached);
    }

    public void fix(SettlementPlot next) {
        Objects.requireNonNull(next, "Участок не может быть null: фиксируется обмеренный участок");
        SettlementPlot before = plot;
        if (before == null
                || before.anchor().x() != next.anchor().x()
                || before.anchor().z() != next.anchor().z()) {
            leveled = false;
            levelRule = "";
            woods = BuildingWoods.NONE;
        }
        plot = next;
        forgetHome();
        sites.clear();
        setDirty();
    }

    public boolean levelMarked() {
        return SettlementLevel.marked(levelRule);
    }

    public void settle(SettlementPlot next) {
        fix(next);
        TownPlan plan = town;
        if (levelMarked() || plan == null) {
            return;
        }
        town = plan.atLevel(SettlementLevel.of(next));
        levelRule = SettlementLevel.RULE;
        sites.clear();
        setDirty();
    }

    public BuildingWoods woods() {
        return woods;
    }

    public void fixWoods(BuildingWoods next) {
        woods = Objects.requireNonNull(next, "Породы построек не могут быть null: фиксируется выбор построек");
        setDirty();
    }

    public boolean leveled() {
        return leveled;
    }

    public void markLeveled() {
        if (!leveled) {
            leveled = true;
            setDirty();
        }
    }

    public void clear() {
        plot = null;
        town = null;
        home = null;
        sites.clear();
        leveled = false;
        levelRule = "";
        woods = BuildingWoods.NONE;
        setDirty();
    }

    public static SettlementPlotStore load(CompoundTag tag, HolderLookup.Provider ignoredRegistries) {
        SettlementPlotStore store = new SettlementPlotStore();
        store.leveled = tag.getBoolean(LEVELED);
        store.levelRule = tag.getString(LEVEL_RULE);
        if (tag.contains(HOME_X)) {
            store.home = new BlockCoord(tag.getInt(HOME_X), tag.getInt(HOME_Y), tag.getInt(HOME_Z));
        }
        CompoundTag chosen = tag.getCompound(BUILDING_WOOD);
        Map<String, String> woods = new HashMap<>();
        chosen.getAllKeys().forEach(key -> woods.put(key, chosen.getString(key)));
        store.woods = new BuildingWoods(woods);
        if (tag.contains(COLUMNS)) {
            store.plot = new SettlementPlot(
                    new BlockCoord(tag.getInt(X), tag.getInt(Y), tag.getInt(Z)),
                    tag.getInt(MIN),
                    tag.getInt(MAX),
                    tag.getInt(COLUMNS),
                    tag.getString(WOOD));
        }
        if (tag.contains(TOWN)) {
            try {
                store.town = TownPlanCodec.decode(tag.getString(TOWN));
            } catch (IllegalArgumentException broken) {
                LOGGER.error(
                        "[plot] сохранённый план поселения не читается, план будет размечен заново: {}",
                        broken.getMessage());
            }
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        SettlementPlot current = plot;
        tag.putBoolean(LEVELED, leveled);
        tag.putString(LEVEL_RULE, levelRule);
        BlockCoord stand = home;
        if (stand != null) {
            tag.putInt(HOME_X, stand.x());
            tag.putInt(HOME_Y, stand.y());
            tag.putInt(HOME_Z, stand.z());
        }
        CompoundTag chosen = new CompoundTag();
        woods.chosen().forEach(chosen::putString);
        tag.put(BUILDING_WOOD, chosen);
        if (current != null) {
            tag.putInt(X, current.anchor().x());
            tag.putInt(Y, current.anchor().y());
            tag.putInt(Z, current.anchor().z());
            tag.putInt(MIN, current.min());
            tag.putInt(MAX, current.max());
            tag.putInt(COLUMNS, current.columns());
            tag.putString(WOOD, current.wood());
        }
        TownPlan plan = town;
        if (plan != null) {
            tag.putString(TOWN, TownPlanCodec.encode(plan));
        }
        return tag;
    }
}
