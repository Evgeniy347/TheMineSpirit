package com.theminespirit.core.coordinator;

import java.util.Optional;

public final class SpiritRoles {
    private SpiritRoles() {}

    public static SpiritRole classify(Optional<String> currentScript, boolean hasActivePlan) {
        return classify(currentScript, hasActivePlan, false);
    }

    public static SpiritRole classify(Optional<String> currentScript, boolean hasActivePlan, boolean busyActivity) {
        if (currentScript == null) {
            throw new IllegalArgumentException(
                    "currentScript не может быть null: отсутствие скрипта — это Optional.empty()");
        }
        if (currentScript.isEmpty()) {
            return hasActivePlan || busyActivity ? SpiritRole.IDLE : SpiritRole.DEFENDER;
        }
        String name = currentScript.get();
        if (isBuilding(name)) {
            return SpiritRole.BUILDING;
        }
        if (isWorker(name)) {
            return SpiritRole.WORKER;
        }
        return SpiritRole.IDLE;
    }

    private static boolean isBuilding(String name) {
        return switch (name) {
            case "build_schematic", "fill", "clear_area", "dig_area", "place_block" -> true;
            default -> false;
        };
    }

    private static boolean isWorker(String name) {
        return switch (name) {
            case "chop_tree",
                    "mine_vein",
                    "gather",
                    "harvest_crop",
                    "plant_crop",
                    "till",
                    "bone_meal",
                    "collect_drops",
                    "deliver",
                    "craft",
                    "smelt",
                    "withdraw",
                    "deposit",
                    "sort_chest",
                    "unload",
                    "equip",
                    "collect_eggs",
                    "breed",
                    "feed",
                    "shear",
                    "milk",
                    "cull",
                    "herd_check",
                    "hunt",
                    "capture" -> true;
            default -> false;
        };
    }
}
