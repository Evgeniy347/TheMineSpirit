package com.theminespirit.neoforge;

import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.farming.FarmJob;
import com.theminespirit.core.farming.FarmPlot;

final class FarmSessions {
    private static final int UNLIMITED = Integer.MAX_VALUE;

    private FarmSessions() {}

    static FarmSession till(SpiritEntity spirit, int radius, int count) {
        return new FarmSession(
                spirit,
                FarmJob.TILL,
                CropKind.ANY,
                count,
                radius,
                false,
                new FarmTargets(spirit.level(), spirit.blockPosition(), radius, FarmJob.TILL, CropKind.ANY));
    }

    static FarmSession plantCrop(SpiritEntity spirit, CropKind crop, int radius, int count) {
        return new FarmSession(
                spirit,
                FarmJob.PLANT,
                crop,
                count,
                radius,
                false,
                new FarmTargets(spirit.level(), spirit.blockPosition(), radius, FarmJob.PLANT, crop));
    }

    static FarmSession harvestCrop(SpiritEntity spirit, CropKind crop, int radius, boolean replant) {
        return new FarmSession(
                spirit,
                FarmJob.HARVEST,
                crop,
                UNLIMITED,
                radius,
                replant,
                new FarmTargets(spirit.level(), spirit.blockPosition(), radius, FarmJob.HARVEST, crop));
    }

    static FarmSession boneMeal(SpiritEntity spirit, CropKind crop, int radius, int count) {
        return new FarmSession(
                spirit,
                FarmJob.BONE_MEAL,
                crop,
                count,
                radius,
                false,
                new FarmTargets(spirit.level(), spirit.blockPosition(), radius, FarmJob.BONE_MEAL, crop));
    }

    static FarmSession tillPlot(SpiritEntity spirit, FarmPlot plot) {
        int groundY = FarmSearch.plotGroundY(spirit);
        return new FarmSession(
                spirit,
                FarmJob.TILL,
                CropKind.ANY,
                plot.cells(),
                plot.searchRadius(groundY),
                false,
                new FarmTargets(
                        spirit.level(),
                        FarmSearch.plotPos(plot, groundY),
                        plot.searchRadius(groundY),
                        plot.grounds(groundY),
                        FarmJob.TILL));
    }

    static FarmSession plantPlot(SpiritEntity spirit, CropKind crop, FarmPlot plot) {
        int groundY = FarmSearch.plotGroundY(spirit);
        return new FarmSession(
                spirit,
                FarmJob.PLANT,
                crop,
                plot.cells(),
                plot.searchRadius(groundY),
                false,
                new FarmTargets(
                        spirit.level(),
                        FarmSearch.plotPos(plot, groundY),
                        plot.searchRadius(groundY),
                        plot.grounds(groundY),
                        FarmJob.PLANT));
    }
}
