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
    void open_DeferredPart_StopsBeforeIt() {
        List<StageBuilds.FencePart> parts = List.of(part(true, true), part(true, false), part(true, true));

        assertEquals(
                List.of(1),
                FenceParts.open(parts),
                "Часть с кусками забора, но без освобождённых клеток, ещё не готова: открывать её нельзя — "
                        + "иначе fence:2 получает «в плане 0 блоков» и падает, хотя забор строится");
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
