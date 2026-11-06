package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public final class PlanBuildings {
    private static final String FENCE = "fence";
    private static final String WALL = "wall_3";
    private static final String GATE = "gate_3";
    private static final String PALISADE = "palisade_section";
    private static final String ORCHARD = "orchard";
    private static final String MINE = "mine";
    private static final String[] HOUSE = {"hut_1", "stone_house", "family_house", "player_house"};

    private PlanBuildings() {}

    public static PlanBuilding of(StageSite site, BlockCoord cell) {
        Objects.requireNonNull(site, "Места этапа нет: по нему не определить владельца клетки");
        Objects.requireNonNull(cell, "Клетка не задана: по ней ищется постройка плана");
        return site.planBuilding(cell.x(), cell.y(), cell.z());
    }

    public static PlanBuilding ofScheme(String file) {
        Objects.requireNonNull(file, "Имя схемы не может быть null: по нему видно, чью постройку строят");
        if (file.startsWith(FENCE) || file.startsWith(WALL) || file.startsWith(GATE) || PALISADE.equals(file)) {
            return PlanBuilding.FENCE;
        }
        if (file.startsWith(ORCHARD)) {
            return PlanBuilding.ORCHARD;
        }
        if (file.startsWith(MINE)) {
            return PlanBuilding.MINE;
        }
        for (String house : HOUSE) {
            if (house.equals(file) || file.startsWith(house + "_")) {
                return PlanBuilding.HOUSE;
            }
        }
        return PlanBuilding.NONE;
    }

    public static PlanBuilding ofBuild(StageSite site, String file, BlockCoord origin) {
        PlanBuilding byScheme = ofScheme(file);
        return byScheme == PlanBuilding.NONE ? of(site, origin) : byScheme;
    }

    public static boolean foreign(PlanBuilding own, PlanBuilding cell) {
        Objects.requireNonNull(own, "Своя постройка не названа: без неё не отличить чужую клетку");
        Objects.requireNonNull(cell, "Постройка клетки не названа: без неё не отличить чужую клетку");
        return cell != PlanBuilding.NONE && cell != own;
    }
}
