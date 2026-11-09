package com.theminespirit.neoforge.inventory;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.ChangeSummary;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.inventory.BaseKit;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.inventory.DepositSelection;
import com.theminespirit.core.inventory.DepositSource;
import com.theminespirit.core.inventory.DepositSpread;
import com.theminespirit.core.inventory.KitFor;
import com.theminespirit.core.inventory.Slice;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.script.TransferParams;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.log.SpiritWorldLog;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.Container;
import org.slf4j.Logger;

public final class DepositScript extends ContainerTask {
    private static final int FALLBACK_MAX_STACK = 64;
    private static final String PREFIX = "[deposit] ";
    private static final Logger LOGGER = LogUtils.getLogger();

    private final TransferParams params;
    private final ChangeSummary log = new ChangeSummary(BoardTuning.logSummaryTicks());
    private List<DepositSpread.Step> steps;
    private int stepIndex;
    private String autoRefusal;
    private boolean nothingToDeposit;

    public DepositScript(SpiritEntity spirit, TransferParams params) {
        super(spirit, params.container(), params.radius());
        this.params = params;
    }

    @Override
    protected int requestedCount() {
        return params.count();
    }

    @Override
    protected boolean persistsProgress() {
        return true;
    }

    @Override
    protected TransferRunner beginTransfer(Container container) {
        if (transferRemaining() <= 0) {
            return null;
        }
        note(
                "put|" + at() + "|" + freeSlots(container) + "|" + params.item() + "|" + transferRemaining(),
                PREFIX + spirit.displayName() + ": контейнер" + at() + " — свободно " + freeSlots(container)
                        + " из " + container.getContainerSize() + " слотов, кладу '" + params.item() + "' ×"
                        + params.count() + ", осталось " + transferRemaining());
        return TransferRunner.filtered(
                spirit.getInventory(), container, IdFilter.of(List.of(params.item())), transferRemaining());
    }

    @Override
    protected ScriptStatus finish(int moved, TransferRunner.Shortage shortage) {
        Container current = container;
        SpiritWorldLog.itemMove(spirit, "deposit", params.item(), moved, containerPos);
        String verdict = switch (shortage) {
            case NONE -> "предметы приняты";
            case SOURCE_LACK ->
                params.leftover()
                        ? "излишек '" + params.item() + "' сдан весь"
                        : "у духа нет остальных '" + params.item() + "'";
            case DEST_FULL -> "нужен второй контейнер или сундук";
        };
        note(
                "finish|" + at() + "|" + (current == null ? -1 : freeSlots(current)) + "|" + moved + "|" + verdict,
                PREFIX + spirit.displayName() + ": контейнер" + at() + " — свободно "
                        + (current == null ? -1 : freeSlots(current)) + " из "
                        + (current == null ? -1 : current.getContainerSize()) + " слотов, перенесено " + moved
                        + ", решение: " + verdict);
        return switch (shortage) {
            case NONE -> ScriptStatus.SUCCEEDED;
            case SOURCE_LACK ->
                params.leftover()
                        ? ScriptStatus.SUCCEEDED
                        : failed("в инвентаре только " + moved + " из " + params.count() + " '" + params.item()
                                + "': перенесено " + moved + ", остального у духа нет");
            case DEST_FULL ->
                failed("контейнер полон" + at() + ": перенесено " + moved + " из " + params.count() + " '"
                        + params.item() + "', остальное осталось у духа; нужен второй контейнер или сундук");
        };
    }

    @Override
    protected AutoMove nextAutoMove(AutoContainerScan scan) {
        if (steps == null) {
            DepositSource.Decision decision = DepositSource.of(available(scan), params.leftover(), params.item());
            if (!decision.transfer()) {
                nothingToDeposit = decision.success();
                if (nothingToDeposit) {
                    note(
                            "nothing|" + params.item(),
                            PREFIX + spirit.displayName() + ": '" + params.item()
                                    + "' в инвентаре нет — сдавать нечего");
                } else {
                    autoRefusal = decision.reason();
                    note("decision|" + autoRefusal, PREFIX + spirit.displayName() + ": " + autoRefusal);
                }
                return null;
            }
            DepositSpread.Plan plan = DepositSpread.plan(scan.snapshots(), blockCoord(), kind(scan), params.count());
            steps = plan.steps();
            autoRefusal = plan.refusal();
            if (steps.isEmpty() && autoRefusal != null) {
                note(
                        "refusal|" + autoRefusal,
                        PREFIX + spirit.displayName() + ": " + autoRefusal
                                + " — решение: заказать сундук или освободить место");
                return null;
            }
        }
        if (stepIndex >= steps.size() || transferRemaining() <= 0) {
            return params.leftover() ? surplusMove(scan) : null;
        }
        DepositSpread.Step step = steps.get(stepIndex++);
        int index = step.index();
        ContainerSnapshot chosen = scan.snapshots().get(index);
        note(
                "auto|" + chosen.pos() + "|" + chosen.freeSlots() + "|" + params.item() + "|" + step.count(),
                PREFIX + spirit.displayName() + ": контейнер " + chosen.pos() + " — свободно "
                        + chosen.freeSlots() + " из " + chosen.slotCount() + " слотов, кладу '" + params.item()
                        + "' ×" + step.count());
        TransferRunner runner = TransferRunner.filtered(
                spirit.getInventory(),
                scan.containers().get(index),
                IdFilter.of(List.of(params.item())),
                Math.min(step.count(), transferRemaining()));
        return new AutoMove(scan.containers().get(index), chosen.pos(), index, runner);
    }

    @Override
    protected ScriptStatus finishAuto() {
        if (nothingToDeposit) {
            return ScriptStatus.SUCCEEDED;
        }
        if (autoRefusal != null) {
            return failed(autoRefusal);
        }
        if (transferRemaining() > 0 && lastShortage() != TransferRunner.Shortage.SOURCE_LACK) {
            return failed("контейнеры склада полны: перенесено " + movedTotal() + " из " + params.count() + " '"
                    + params.item() + "', остальное осталось у духа; нужен ещё сундук");
        }
        return finish(movedTotal(), lastShortage());
    }

    private AutoMove surplusMove(AutoContainerScan scan) {
        for (KindGroup group : surplusGroups(scan)) {
            DepositSpread.Plan plan = DepositSpread.plan(scan.snapshots(), blockCoord(), group.kind, group.total());
            if (plan.steps().isEmpty()) {
                continue;
            }
            DepositSpread.Step step = plan.steps().get(0);
            List<Slice> capped = capped(group.slices, step.count());
            if (capped.isEmpty()) {
                continue;
            }
            TransferRunner runner = TransferRunner.slices(
                    spirit.getInventory(), scan.containers().get(step.index()), capped);
            return new AutoMove(
                    scan.containers().get(step.index()),
                    scan.snapshots().get(step.index()).pos(),
                    step.index(),
                    runner);
        }
        return null;
    }

    private List<KindGroup> surplusGroups(AutoContainerScan scan) {
        List<Stack> inventory = scan.mapper().map(spirit.getInventory());
        BaseKit kit = KitFor.kitFor(spirit.currentActivity().orElse(null), spirit.isChild());
        List<Slice> surplus = DepositSelection.surplus(inventory, kit, InventoryTables.values(), -1);
        List<KindGroup> groups = new ArrayList<>();
        for (Slice slice : surplus) {
            Stack kind = inventory.get(slice.slot());
            if (kind == null) {
                continue;
            }
            KindGroup group = null;
            for (KindGroup candidate : groups) {
                if (candidate.kind.sameKindAs(kind)) {
                    group = candidate;
                    break;
                }
            }
            if (group == null) {
                group = new KindGroup(kind);
                groups.add(group);
            }
            group.slices.add(slice);
        }
        return groups;
    }

    private static List<Slice> capped(List<Slice> slices, int limit) {
        List<Slice> capped = new ArrayList<>();
        int left = limit;
        for (Slice slice : slices) {
            if (left <= 0) {
                break;
            }
            int count = Math.min(slice.count(), left);
            capped.add(new Slice(slice.slot(), count));
            left -= count;
        }
        return capped;
    }

    private static final class KindGroup {
        private final Stack kind;
        private final List<Slice> slices = new ArrayList<>();

        private KindGroup(Stack kind) {
            this.kind = kind;
        }

        private int total() {
            int total = 0;
            for (Slice slice : slices) {
                total += slice.count();
            }
            return total;
        }
    }

    private int available(AutoContainerScan scan) {
        IdFilter filter = IdFilter.of(List.of(params.item()));
        int total = 0;
        for (Stack stack : scan.mapper().map(spirit.getInventory())) {
            if (stack != null && filter.matches(stack.item())) {
                total += stack.count();
            }
        }
        return total;
    }

    private Stack kind(AutoContainerScan scan) {
        IdFilter filter = IdFilter.of(List.of(params.item()));
        for (Stack stack : scan.mapper().map(spirit.getInventory())) {
            if (stack != null && filter.matches(stack.item())) {
                return stack;
            }
        }
        return new Stack(exactItem(), 0, 1, FALLBACK_MAX_STACK);
    }

    private String at() {
        return containerPos == null ? "" : " " + containerPos.toShortString();
    }

    private static int freeSlots(Container container) {
        int free = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    private String exactItem() {
        return params.item().indexOf(':') < 0 ? "minecraft:" + params.item() : params.item();
    }

    private BlockCoord blockCoord() {
        return new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ());
    }

    private void note(String state, String text) {
        long now = spirit.level().getGameTime();
        log.note(spirit.getUUID().toString(), state, 1, now).ifPresent(summary -> {
            if (summary.changed()) {
                LOGGER.info(text);
                return;
            }
            LOGGER.info(PREFIX + "сводка: {} повторов за окно, по одному разу на состояние: {}", summary.count(), text);
        });
    }
}
