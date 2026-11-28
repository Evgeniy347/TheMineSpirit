package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.audit.AuditRegistry;
import com.theminespirit.core.board.audit.ChunkCache;
import com.theminespirit.core.board.audit.DiffItem;
import com.theminespirit.core.board.audit.Group;
import com.theminespirit.core.board.audit.GroupRegistry;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownStreets;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritScripts;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.board.TownAuditWatch;
import com.theminespirit.neoforge.lease.LeaseStates;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TownAuditScenesGameTests {
    private static final BlockPos OBSTACLE = new BlockPos(2, 2, 1);
    private static final BlockPos SPIRIT = new BlockPos(2, 1, 1);
    private static final String TICKET = "audit-gametest";

    private TownAuditScenesGameTests() {}

    @GameTest(template = "mine_arena", batch = "town_audit_street", timeoutTicks = 400)
    public static void streetObstacle_AbovePavement_CreatesBreakGroup(GameTestHelper helper) {
        prepare(helper, Blocks.STONE);
        initialize(helper.getLevel());

        helper.succeedWhen(() -> {
            GroupRegistry groups = TownAuditWatch.peekGroups(helper.getLevel());
            helper.assertTrue(groups != null, detail("Аудит ещё не собрал реестр групп", groups));
            Group group = groupAt(helper, groups, OBSTACLE);
            helper.assertTrue(group != null, detail("Клетка улицы ещё не получила группу", groups));
            helper.assertTrue(group.status() != Group.Status.BLOCKED, detail("Группа улицы ещё не заполнена", group));
            DiffItem item = itemAt(helper, group, OBSTACLE);
            helper.assertTrue(
                    item != null && item.action() == DiffItem.Action.BREAK,
                    detail("Препятствие над плиткой обязано дать BREAK в группе улицы", group));
            helper.assertTrue(
                    group.status() == Group.Status.PENDING && group.unknown() == 0,
                    detail("Заполненная группа улицы готова к выдаче", group));
            cleanup(helper);
        });
    }

    @GameTest(template = "mine_arena", batch = "town_audit_unload", timeoutTicks = 400)
    public static void auditChunk_AfterUnload_ReloadRestoresGroup(GameTestHelper helper) {
        prepare(helper, Blocks.AIR);
        initialize(helper.getLevel());

        helper.succeedWhen(() -> {
            GroupRegistry groups = TownAuditWatch.peekGroups(helper.getLevel());
            helper.assertTrue(groups != null, detail("Аудит ещё не собрал реестр групп", groups));
            Group group = groupAt(helper, groups, OBSTACLE);
            helper.assertTrue(group != null, detail("Клетка улицы ещё не получила группу", groups));
            helper.assertTrue(
                    group.status() == Group.Status.DONE && group.unknown() == 0,
                    detail("Перед выгрузкой группа улицы заполнена и завершена", group));
            AuditRegistry registry = TownAuditWatch.peek(helper.getLevel());
            registry.unloaded(group.chunk());
            groups.sync(registry, registry.planRevision(), Integer.MAX_VALUE);
            Group blocked = groupAt(helper, groups, OBSTACLE);
            helper.assertTrue(blocked != null, detail("После выгрузки группа улицы не исчезает", group.key()));
            helper.assertTrue(
                    blocked.status() == Group.Status.BLOCKED && blocked.unknown() > 0,
                    detail("Выгрузка чанка обязана заблокировать группу", blocked));
            registry.loaded(blocked.chunk());
            fillGroups(helper.getLevel());
            Group restored = groupAt(helper, groups, OBSTACLE);
            helper.assertTrue(restored != null, detail("После перезаливки группа улицы возвращается", group.key()));
            helper.assertTrue(
                    restored.status() == Group.Status.DONE && restored.unknown() == 0 && registry.waiting() == 0,
                    detail("Локальная перезаливка чанка снимает unknown", restored));
            cleanup(helper);
        });
    }

    @GameTest(template = "mine_arena", batch = "town_audit_foreign", timeoutTicks = 400)
    public static void foreignBlock_InIssuedGroup_QuarantinesAndRefills(GameTestHelper helper) {
        prepare(helper, Blocks.STONE);
        initialize(helper.getLevel());
        AtomicInteger phase = new AtomicInteger();
        SpiritEntity[] spirit = new SpiritEntity[1];
        ScriptInstance[] script = new ScriptInstance[1];

        helper.succeedWhen(() -> {
            GroupRegistry groups = TownAuditWatch.peekGroups(helper.getLevel());
            helper.assertTrue(groups != null, detail("Аудит ещё не собрал реестр групп", groups));
            Group group = groupAt(helper, groups, OBSTACLE);
            helper.assertTrue(group != null, detail("Клетка улицы ещё не получила группу", groups));
            if (phase.get() == 0) {
                DiffItem item = itemAt(helper, group, OBSTACLE);
                helper.assertTrue(
                        item != null && item.action() == DiffItem.Action.BREAK,
                        detail("Для проверки чужого блока нужен выданный BREAK", group));
                spirit[0] = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT);
                groups.issue(TICKET, group.key());
                script[0] = SpiritScripts.registryFor(spirit[0])
                        .find("audit_group")
                        .orElseThrow()
                        .create(Map.of("group", group.key(), "revision", group.revision(), "ticket", TICKET));
                script[0].restoreState(Map.of("index", 0, "approached", true));
                helper.setBlock(OBSTACLE, Blocks.OAK_PLANKS);
                phase.set(1);
            }
            if (phase.get() == 1) {
                ScriptStatus status = script[0].tick();
                helper.assertTrue(
                        status == ScriptStatus.FAILED,
                        detail(
                                "Чужой блок обязан остановить группу с инцидентом",
                                String.join("/", status.name(), script[0].failureReason())));
                helper.assertTrue(
                        script[0].failureReason().contains("AUDIT-MISS/FOREIGN_BLOCK"),
                        detail("Инцидент классифицирован как чужой блок", script[0].failureReason()));
                Group quarantined = groupAt(helper, groups, OBSTACLE);
                helper.assertTrue(
                        quarantined != null && quarantined.status() == Group.Status.QUARANTINED,
                        detail("После промаха группа quarantined", quarantined));
                ChunkCache cache = TownAuditWatch.peek(helper.getLevel()).cache(coord(helper, OBSTACLE));
                helper.assertTrue(
                        cache != null && cache.filledTick() < 0,
                        detail("Промах духа немедленно очищает только свой чанк для перезаливки", cache));
                BoardRegistry.get(helper.getLevel()).journal().flushAll();
                helper.assertTrue(
                        BoardRegistry.get(helper.getLevel()).journal().last(40).stream()
                                .anyMatch(entry -> entry.ticketId().startsWith("audit-miss:")),
                        detail(
                                "AUDIT-MISS обязан попасть в board journal",
                                BoardRegistry.get(helper.getLevel()).journal().last(5)));
                phase.set(2);
            }
            fillGroups(helper.getLevel());
            Group restored = groupAt(helper, groups, OBSTACLE);
            helper.assertTrue(restored != null, detail("После перезаливки группа возвращается", group.key()));
            helper.assertTrue(
                    restored.status() == Group.Status.PENDING && restored.unknown() == 0,
                    detail("Перезаливка снимает карантин и сохраняет реальную работу", restored));
            DiffItem restoredItem = itemAt(helper, restored, OBSTACLE);
            helper.assertTrue(
                    restoredItem != null && restoredItem.found().contains("oak_planks"),
                    detail("Пересобранная группа видит чужой блок как новый факт", restoredItem));
            spirit[0].discard();
            cleanup(helper);
        });
    }

    private static String detail(String label, Object value) {
        String fact = String.join(": ", label, String.valueOf(value));
        return String.join(
                " ",
                fact,
                "Иначе аудит потеряет клетку или выдаст неверную работу.",
                "При смене проверь TownAuditScenesGameTests и TownAuditWatch.");
    }

    private static void initialize(ServerLevel level) {
        TownAuditWatch.tick(level);
        fillGroups(level);
    }

    private static void fillGroups(ServerLevel level) {
        AuditRegistry registry = Objects.requireNonNull(
                TownAuditWatch.peek(level), "Аудит ещё не создал реестр фактов: заполнять нечего");
        int filled;
        do {
            filled = registry.fill(
                    cell -> {
                        BlockPos at = new BlockPos(cell.x(), cell.y(), cell.z());
                        return level.isLoaded(at) ? LeaseStates.text(level.getBlockState(at)) : null;
                    },
                    level.getGameTime());
        } while (registry.waiting() > 0 && filled > 0);
        GroupRegistry groups = Objects.requireNonNull(
                TownAuditWatch.peekGroups(level), "Аудит ещё не создал реестр групп: пересобирать нечего");
        groups.sync(registry, registry.planRevision(), Integer.MAX_VALUE);
    }

    private static void prepare(GameTestHelper helper, Block obstacle) {
        GameTestFixtures.reset(helper, 8);
        TownAuditWatch.forget(helper.getLevel());
        GameTestFixtures.buildFloor(helper, Blocks.DIRT);
        BlockPos min = helper.absolutePos(new BlockPos(1, 0, 1));
        int x1 = min.getX();
        int z1 = min.getZ();
        TownPlan plan = new TownPlan(
                new BlockCoord(x1 + 1, min.getY() + 1, z1 + 1),
                ColumnMask.rect(x1, z1, x1 + 2, z1 + 2),
                new TownStreets(ColumnMask.rect(x1, z1, x1 + 2, z1), new ColumnMask(x1, z1, 3, 3, new BitSet())),
                List.of(),
                new FenceLine(List.of(), List.of()),
                List.of());
        SettlementPlotStore store = SettlementPlotStore.get(helper.getLevel());
        store.fixTown(plan);
        store.settle(new SettlementPlot(plan.anchor(), min.getY() - 1, min.getY() + 5, 9, "oak"));
        if (!obstacle.equals(Blocks.AIR)) {
            helper.setBlock(OBSTACLE, obstacle);
        }
    }

    private static Group groupAt(GameTestHelper helper, GroupRegistry groups, BlockPos relative) {
        String owner = groups.owner(coord(helper, relative));
        return owner == null ? null : groups.group(owner);
    }

    private static DiffItem itemAt(GameTestHelper helper, Group group, BlockPos relative) {
        BlockCoord cell = coord(helper, relative);
        return group.items().stream()
                .filter(item -> item.cell().equals(cell))
                .findFirst()
                .orElse(null);
    }

    private static BlockCoord coord(GameTestHelper helper, BlockPos relative) {
        BlockPos at = helper.absolutePos(relative);
        return new BlockCoord(at.getX(), at.getY(), at.getZ());
    }

    private static void cleanup(GameTestHelper helper) {
        SettlementPlotStore.get(helper.getLevel()).clear();
        TownAuditWatch.forget(helper.getLevel());
        GameTestFixtures.finish(helper, 8);
    }
}
