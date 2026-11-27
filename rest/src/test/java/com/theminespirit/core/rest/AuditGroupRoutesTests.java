package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.board.audit.Access;
import com.theminespirit.core.board.audit.DiffItem;
import com.theminespirit.core.board.audit.Group;
import com.theminespirit.core.board.audit.SourceRef;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditGroupRoutesTests {
    @Test
    void auditGroups_Get_ReportsIdChunkPendingAndStatus() {
        Group group = Group.pending(
                Group.Kind.STREET,
                "s-7",
                new ChunkCell(-16, -32),
                List.of(DiffItem.breakAt(
                        new BlockCoord(-244, 64, -535), "minecraft:oak_fence", SourceRef.street("s-7"), Access.SPIRIT)),
                1);
        Router router = Router.of(new AuditGroupRoutes(() -> List.of(group)).routes());

        RestResponse response =
                router.handle(new RestRequest("GET", AuditGroupRoutes.PATH, Map.of(), Map.of(), new byte[0]));

        assertEquals(
                200,
                response.status(),
                "GET /v1/audit/groups отдаёт готовые группы наблюдателю: иначе"
                        + " внешний стратег не увидит координаты и статус работы. При смене маршрута перепроверь PATH.");
        assertTrue(
                response.body().contains("\"id\":\"group:street:s-7@-16,-32\"")
                        && response.body().contains("\"kind\":\"STREET\"")
                        && response.body().contains("\"pending\":1")
                        && response.body().contains("\"status\":\"PENDING\"")
                        && response.body().contains("\"chunk\":{\"x\":-16,\"z\":-32}"),
                "Ответ называет ключ, вид, чанк, остаток и статус: без них граф не сможет выбрать группу и"
                        + " сопоставить её с билетом. При смене контракта перепроверь AuditGroupsJson.");
    }
}
