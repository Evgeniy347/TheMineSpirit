package com.theminespirit.core.town;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.ui.MineGeometry;
import com.theminespirit.core.ui.TownMapView;
import java.util.Objects;

public record MinePlot(BlockCoord origin, BlockCoord entrance, int x1, int z1, int x2, int z2) {
    public MinePlot {
        Objects.requireNonNull(origin, "Начало спуска не может быть null: от него копают шахту");
        Objects.requireNonNull(entrance, "Вход спуска не может быть null: по нему дух заходит в шахту");
        if (x2 < x1 || z2 < z1) {
            throw new IllegalArgumentException("Прямоугольник шахты " + x1 + "," + z1 + "–" + x2 + "," + z2
                    + ": углы по возрастанию, иначе карта не покажет спуск");
        }
    }

    public static MinePlot of(Placement mine, int levelY, Schematic descent, BlockCoord entry, BuildRotation rotation) {
        Objects.requireNonNull(mine, "Место шахты не может быть null: спуск берёт его из плана");
        Objects.requireNonNull(descent, "Схема спуска не может быть null: по ней считается прямоугольник");
        Objects.requireNonNull(entry, "Вход схемы не может быть null: он задаёт верх спуска");
        Objects.requireNonNull(rotation, "Поворот спуска не может быть null: без него прямоугольник неверен");
        BlockCoord origin = origin(mine, levelY);
        MineLayout layout = MineLayout.place(descent, entry, origin, rotation, MineLayout.Extent.NONE);
        TownMapView.Bounds bounds = MineGeometry.airBounds(layout.air(), origin);
        return new MinePlot(origin, layout.entrance(), bounds.x1(), bounds.z1(), bounds.x2(), bounds.z2());
    }

    public static BlockCoord origin(Placement mine, int levelY) {
        Objects.requireNonNull(mine, "Место шахты не может быть null: спуск берёт его из плана");
        return new BlockCoord(mine.x() + StageSite.MINE_SHIFT, levelY - 1, mine.z() + 1);
    }

    public int width() {
        return x2 - x1 + 1;
    }

    public int depth() {
        return z2 - z1 + 1;
    }

    public boolean covers(int x, int z) {
        return x >= x1 && x <= x2 && z >= z1 && z <= z2;
    }
}
