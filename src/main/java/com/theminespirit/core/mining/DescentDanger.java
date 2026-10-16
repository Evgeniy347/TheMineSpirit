package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;

final class DescentDanger {
    private DescentDanger() {
    }

    static String label(Cell cell) {
        return switch (cell) {
            case SOLID -> "твёрдый блок";
            case PASSABLE -> "пустота";
            case WATER -> "вода";
            case LAVA -> "лава";
            case FIRE -> "огонь";
            case HAZARD -> "опасный блок";
            case FALLING -> "сыпучий блок";
            case PORTAL -> "портал";
        };
    }

    static String coords(BlockCoord cell) {
        return "(" + cell.x() + ", " + cell.y() + ", " + cell.z() + ")";
    }

    static String solidFloor(SafetyView view, BlockCoord cell, String role, int stepIndex) {
        Cell value = view.cellAt(cell);
        if (value == Cell.SOLID || value == Cell.FALLING) {
            return null;
        }
        return "опасность: " + label(value) + " вместо " + role + "а в клетке " + coords(cell)
                + " шага " + stepIndex;
    }

    static String fluidPassage(SafetyView view, BlockCoord cell, String role, BlockCoord passageCell) {
        Cell value = view.cellAt(cell);
        if (value != Cell.LAVA && value != Cell.WATER) {
            return null;
        }
        return "опасность: " + label(value) + " в клетке " + coords(cell) + " (" + role + " прохода "
                + coords(passageCell) + ")";
    }

    static String fluidCell(SafetyView view, BlockCoord cell, String role) {
        Cell value = view.cellAt(cell);
        if (value != Cell.LAVA && value != Cell.WATER) {
            return null;
        }
        return "опасность: " + label(value) + " в клетке " + coords(cell) + " (" + role + ")";
    }
}
