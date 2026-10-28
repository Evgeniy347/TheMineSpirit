package com.theminespirit.gametest;

import com.theminespirit.core.incident.IncidentNote;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.IncidentSnapshots;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

final class IncidentScene {
    private IncidentScene() {}

    static IncidentSnapshots.Loaded place(GameTestHelper helper, Path file, BlockPos corner) {
        IncidentSnapshots.Loaded loaded = IncidentSnapshots.load(helper.getLevel(), file);
        BlockPos at = helper.absolutePos(corner);
        loaded.template()
                .placeInWorld(
                        helper.getLevel(),
                        at,
                        at,
                        new StructurePlaceSettings(),
                        helper.getLevel().getRandom(),
                        2);
        return loaded;
    }

    static SpiritEntity spirit(GameTestHelper helper, IncidentSnapshots.Loaded loaded, BlockPos corner) {
        BlockCoord offset = IncidentNote.spiritOffset();
        BlockPos at = corner.offset(offset.x(), offset.y(), offset.z());
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), at);
        CompoundTag saved = loaded.meta().getCompound("entity").copy();
        saved.remove("UUID");
        BlockPos absolute = helper.absolutePos(at);
        spirit.load(saved);
        spirit.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
        return spirit;
    }
}
