package com.theminespirit.neoforge;

import com.theminespirit.core.script.PlantCropParams;

final class PlantCropScript extends FarmScript {
    PlantCropScript(SpiritEntity spirit, PlantCropParams params) {
        super(FarmSession.plantCrop(spirit, params.crop(), params.radius(), params.count()));
    }
}
