package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class FencePartsTests {
    private static final StageSite SITE = TownFixtures.flatSite();

    @Test
    void open_DeferredPart_DoesNotBlockLaterReadyParts() {
        List<StageBuilds.FencePart> parts = List.of(part(true, true), part(true, false), part(true, true));

        assertEquals(
                List.of(1, 3),
                FenceParts.open(parts),
                "Часть, чьи клетки ещё заняты, пропускается, а готовые части за ней открываются: иначе ограда "
                        + "строится только до первой занятой клетки и не дорастает до 100% (наблюдение 6, аномалия 3)");
    }

    @Test
    void required_DeferredPart_DoesNotHoldFenceDone() {
        List<StageBuilds.FencePart> parts = List.of(part(true, true), part(true, false), part(true, true));

        assertEquals(
                List.of(1, 3),
                FenceParts.required(parts),
                "Часть, у которой нет готовых к постройке блоков, не держит ограду незакрытой: иначе DONE всех "
                        + "построенных частей навсегда остаётся ложью, и задача переоткрывается по кругу");
    }

    @Test
    void open_EmptyPartWithoutPieces_SkipsItAndKeepsLater() {
        List<StageBuilds.FencePart> parts = List.of(part(true, true), part(false, false), part(true, true));

        assertEquals(
                List.of(1, 3),
                FenceParts.open(parts),
                "Часть без кусков забора пуста по плану, а не из-за занятых клеток: её пропускают, "
                        + "а следующие части открывают");
    }

    private static StageBuilds.FencePart part(boolean hasPieces, boolean withWork) {
        StageWork work = withWork
                ? new StageWork(SITE, List.of(new PlannedBlock(new BlockCoord(1, 70, 1), "minecraft:dirt")), List.of())
                : new StageWork(SITE, List.of());
        return new StageBuilds.FencePart(work, hasPieces);
    }
}
