package com.theminespirit.neoforge;

import com.theminespirit.core.script.HarvestCropParams;

final class HarvestCropScript extends FarmScript {
    HarvestCropScript(SpiritEntity spirit, HarvestCropParams params) {
        super(FarmSession.harvestCrop(spirit, params.crop(), params.radius(), params.replant()));
    }
}
