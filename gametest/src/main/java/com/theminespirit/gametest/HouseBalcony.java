package com.theminespirit.gametest;

import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.api.BuildRotation;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import net.minecraft.core.BlockPos;

final class HouseBalcony {
    private static final int RAIL_LAYER = 2;

    private HouseBalcony() {}

    static List<BlockPos> rails(Schematic schematic, BlockCoord origin, BuildRotation rotation) {
        int railY = origin.y() + RAIL_LAYER;
        List<BlockPos> rails = schematic.entries(origin, rotation).stream()
                .filter(entry -> entry.pos().y() == railY && entry.block().endsWith("_fence"))
                .map(entry -> new BlockPos(
                        entry.pos().x(), entry.pos().y(), entry.pos().z()))
                .toList();
        if (rails.isEmpty()) {
            throw new IllegalStateException("В схеме усадьбы нет перил балкона на y2: без этой опоры тест выберет"
                    + " обычную комнату и перестанет проверять именно балкон. При смене схемы перепроверь"
                    + " family_house_4 и HouseBalcony.rails.");
        }
        return rails;
    }

    static boolean at(List<BlockPos> rails, BlockPos cell) {
        int railY = rails.get(0).getY();
        if (cell.getY() != railY) {
            return false;
        }
        return rails.stream()
                .anyMatch(rail ->
                        Math.max(Math.abs(cell.getX() - rail.getX()), Math.abs(cell.getZ() - rail.getZ())) <= 1);
    }
}
