package com.theminespirit.core.coordinator;

import java.util.Optional;

public final class SpiritRoles {
    private SpiritRoles() {
    }

    public static SpiritRole classify(Optional<String> currentScript, boolean hasActivePlan) {
        if (currentScript == null) {
            throw new IllegalArgumentException("currentScript не может быть null: отсутствие скрипта — это Optional.empty()");
        }
        if (currentScript.isEmpty()) {
            return hasActivePlan ? SpiritRole.IDLE : SpiritRole.DEFENDER;
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
        switch (name) {
            case "build_schematic":
            case "fill":
            case "clear_area":
            case "dig_area":
            case "place_block":
                return true;
            default:
                return false;
        }
    }

    private static boolean isWorker(String name) {
        switch (name) {
            case "chop_tree":
            case "mine_vein":
            case "gather":
            case "harvest_crop":
            case "plant_crop":
            case "till":
            case "bone_meal":
            case "collect_drops":
            case "deliver":
            case "craft":
            case "smelt":
            case "withdraw":
            case "deposit":
            case "sort_chest":
            case "unload":
            case "equip":
            case "collect_eggs":
            case "breed":
            case "feed":
            case "shear":
            case "milk":
            case "cull":
            case "herd_check":
            case "hunt":
            case "capture":
                return true;
            default:
                return false;
        }
    }
}
