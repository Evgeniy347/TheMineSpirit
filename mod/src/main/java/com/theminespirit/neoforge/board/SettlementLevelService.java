package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.LevelingRules;
import com.theminespirit.core.board.PlotHeight;
import com.theminespirit.core.board.SettlementLevel;
import com.theminespirit.core.board.api.ConfigStamp;
import com.theminespirit.core.building.EarthCover;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.rest.ApiException;
import com.theminespirit.core.rest.ErrorCode;
import com.theminespirit.core.rest.SettlementLevelChange;
import com.theminespirit.core.rest.SettlementLevelChangeResult;
import com.theminespirit.core.rest.SettlementLevelPreview;
import com.theminespirit.core.rest.SettlementLevelView;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.core.town.TownPlan;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntBinaryOperator;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

public final class SettlementLevelService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ServerLevel level;
    private final BoardService service;
    private final SettlementPlotStore store;

    private SettlementLevelService(ServerLevel level) {
        this.level = Objects.requireNonNull(level, "Уровень не может быть null: поселение живёт в мире");
        this.service = BoardRegistry.get(level);
        this.store = SettlementPlotStore.get(level);
    }

    public static SettlementLevelService of(ServerLevel level) {
        return new SettlementLevelService(level);
    }

    public Optional<SettlementLevelView> view() {
        return store.plot()
                .map(plot -> new SettlementLevelView(
                        plot.anchor().y(),
                        plot.anchor().y() - 1,
                        plot.anchor().x(),
                        plot.anchor().z(),
                        store.levelRule().isBlank() ? SettlementLevel.RULE : store.levelRule()));
    }

    public SettlementLevelPreview preview() {
        Survey survey = survey();
        LevelingRules rules = rules();
        int stock = earthStock(survey.stock());
        SettlementGround.WaterScan water = SettlementGround.waterScan(level, survey.base(), rules);
        PlotHeight height = SettlementLevel.height(survey.area(), survey.feet(), rules, water.floor(), stock);
        SettlementPlot plot =
                SettlementLevel.plot(survey.center(), survey.area(), survey.feet(), survey.wood(), height.level());
        return new SettlementLevelPreview(
                height.level(),
                height.level() - 1,
                height.cut(),
                height.fill(),
                rules.surplusPercent(),
                stock,
                rules.enough(height.cut(), height.fill(), stock),
                water.blocks(),
                median(water),
                floor(water),
                plot.columns(),
                plot.min(),
                plot.max(),
                survey.center().x(),
                survey.center().z());
    }

    public SettlementLevelChangeResult set(SettlementLevelChange change) {
        Survey survey = survey();
        LevelingRules rules = rules();
        SettlementGround.WaterScan water = SettlementGround.waterScan(level, survey.base(), rules);
        if (water.limited() && change.level() < water.floor()) {
            throw new ApiException(
                    ErrorCode.INVALID_PLAN,
                    "Уровень " + change.level() + " ниже воды: медиана воды " + water.median()
                            + ", минимальные ноги поселения " + water.floor()
                            + ". Поднимите level или измените выбор; ниже воды поселение не ставится",
                    Map.of("level", change.level(), "waterMedian", water.median(), "waterFloor", water.floor()));
        }
        if (change.level() <= level.getMinBuildHeight() || change.level() >= level.getMaxBuildHeight()) {
            throw new ApiException(
                    ErrorCode.INVALID_PLAN,
                    "Уровень " + change.level() + " вне мира " + level.getMinBuildHeight() + ".."
                            + level.getMaxBuildHeight() + ": проверьте координату level",
                    Map.of("level", change.level()));
        }
        Integer previous = store.plot().map(plot -> plot.anchor().y()).orElse(null);
        SettlementPlot next =
                SettlementLevel.plot(survey.center(), survey.area(), survey.feet(), survey.wood(), change.level());
        store.settle(next);
        long now = level.getGameTime();
        String note = "уровень через REST: " + (previous == null ? "не задан" : previous) + " → " + change.level()
                + ", " + change.by() + ": " + change.reason();
        service.journal().recordEntry(SettlementLevel.journalEntry(next, now, ConfigStamp.current(), List.of(note)));
        LOGGER.info(
                "[plot] {}: вода {} (медиана {}, минимум ног {}), запас земли {}",
                note,
                water.blocks(),
                median(water) == null ? "нет" : median(water),
                floor(water) == null ? "нет" : floor(water),
                earthStock(survey.stock()));
        return new SettlementLevelChangeResult(
                previous, change.level(), !Integer.valueOf(change.level()).equals(previous));
    }

    private Survey survey() {
        TownPlan town = store.town().orElseGet(this::plan);
        ColumnMask area = SettlementLevel.finalArea(town)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.INVALID_PLAN,
                        "В плане поселения нет площадки финального этапа: уровню не по чему считаться",
                        Map.of()));
        BlockCoord center = town.anchor();
        Map<String, Integer> stock = service.stock().available();
        String wood =
                store.plot().map(SettlementPlot::wood).orElseGet(() -> SettlementWood.survey(level, center, stock));
        return new Survey(area, center, SettlementGround.feet(level, center.y()), stock, wood);
    }

    private TownPlan plan() {
        List<BlockCoord> homes = SettlementHome.homes(SpiritCensus.all(level));
        if (homes.isEmpty()) {
            throw new ApiException(
                    ErrorCode.INVALID_PLAN, "У духов нет домов: центр поселения не задан, план не размечен", Map.of());
        }
        BlockCoord center = SettlementPlot.center(homes);
        return TownSurvey.town(level, service, store, center, level.getGameTime());
    }

    private static LevelingRules rules() {
        return DataTables.INSTANCE.leveling().current();
    }

    private static Integer median(SettlementGround.WaterScan water) {
        return water.limited() ? water.median() : null;
    }

    private static Integer floor(SettlementGround.WaterScan water) {
        return water.limited() ? water.floor() : null;
    }

    private static int earthStock(Map<String, Integer> available) {
        long sum = 0;
        for (Map.Entry<String, Integer> entry : available.entrySet()) {
            if (EarthCover.earth(entry.getKey())) {
                sum += entry.getValue();
            }
        }
        return (int) Math.min(sum, Integer.MAX_VALUE);
    }

    private record Survey(
            ColumnMask area, BlockCoord center, IntBinaryOperator feet, Map<String, Integer> stock, String wood) {
        BlockCoord base() {
            return center;
        }
    }
}
