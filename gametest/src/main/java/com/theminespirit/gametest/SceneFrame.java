package com.theminespirit.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.world.level.block.Blocks;

final class SceneFrame {
    private SceneFrame() {}

    static void restore(GameTestHelper helper) {
        StructureUtils.encaseStructure(helper.getBounds(), helper.getLevel(), true);
    }

    static void assertContained(GameTestHelper helper, int y) {
        int sizeX = (int) Math.round(helper.getBounds().getXsize());
        int sizeZ = (int) Math.round(helper.getBounds().getZsize());
        for (BlockPos cell : new BlockPos[] {
            new BlockPos(-1, y, sizeZ / 2),
            new BlockPos(sizeX, y, sizeZ / 2),
            new BlockPos(sizeX / 2, y, -1),
            new BlockPos(sizeX / 2, y, sizeZ)
        }) {
            helper.assertTrue(
                    helper.getBlockState(cell).is(Blocks.BARRIER),
                    "Рама GameTest вокруг арены обязана пережить зачистку сцены: в " + cell + " сейчас "
                            + helper.getBlockState(cell) + ". Снесённая стена — дыра из арены в пустоту: дух уходит "
                            + "через неё, падает и шаг краснеет по чужой причине (INC-45). Верни раму "
                            + "SceneFrame.restore после зачистки");
        }
    }
}
