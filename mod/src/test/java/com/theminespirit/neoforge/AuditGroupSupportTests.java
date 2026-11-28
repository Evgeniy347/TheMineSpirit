package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.audit.Access;
import com.theminespirit.core.board.audit.DiffItem;
import com.theminespirit.core.board.audit.SourceRef;
import com.theminespirit.core.board.audit.Wanted;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class AuditGroupSupportTests {
    @Test
    void auditGroupSupport_FloorWanted_MapsToPlacementBlock() {
        DiffItem item = DiffItem.place(
                new BlockCoord(0, -58, 0),
                "minecraft:air",
                Wanted.block(SchematicBlocks.FLOOR),
                SourceRef.mine("mine_descent"),
                Access.SPIRIT);

        assertEquals(
                "minecraft:cobblestone",
                AuditGroupSupport.wantedText(item),
                "Абстрактный пол схемы превращается в булыжник: иначе PlaceBlockScript попытается поставить"
                        + " несуществующий id floor. При смене правила перепроверь SchematicBlocks.placementBlock.");
    }
}
