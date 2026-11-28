package com.theminespirit.neoforge;

import com.theminespirit.core.board.audit.AuditFactCheck;
import com.theminespirit.core.board.audit.AuditMiss;
import com.theminespirit.core.board.audit.DiffItem;
import com.theminespirit.core.board.audit.Group;
import com.theminespirit.core.board.audit.GroupRef;
import com.theminespirit.core.board.audit.GroupRegistry;
import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.FailureFact;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.engine.TaskRunner;
import com.theminespirit.core.mining.StandSpots;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.script.api.ScriptArgs;
import com.theminespirit.neoforge.board.AuditGroupLookup;
import com.theminespirit.neoforge.board.AuditIncidents;
import com.theminespirit.neoforge.board.AuditMisses;
import com.theminespirit.neoforge.board.TownAuditWatch;
import com.theminespirit.neoforge.lease.LeaseStates;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class AuditGroupScript implements ScriptInstance {
    private static final int MAX_ITEMS = 64;
    private final SpiritEntity spirit;
    private final Level level;
    private final GroupRef ref;
    private final String ticket;
    private final GroupRegistry groups;
    private final List<DiffItem> items;
    private final Approach approach;
    private final List<BlockCoord> stands = new ArrayList<>();
    private int index;
    private int standIndex;
    private boolean approached;
    private @Nullable TaskRunner action;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private @Nullable String reason;
    private @Nullable FailureFact fact;

    AuditGroupScript(SpiritEntity spirit, Map<String, Object> params) {
        this.spirit = spirit;
        this.approach = new Approach(spirit);
        this.level = spirit.level();
        this.ref = new GroupRef(ScriptArgs.requireString(params, "group"), ScriptArgs.requireInt(params, "revision"));
        String suppliedTicket = ScriptArgs.optionalString(params, "ticket", "");
        this.ticket = suppliedTicket.isBlank() ? AuditGroupLookup.ticketOf(spirit) : suppliedTicket;
        if (ticket.isBlank()) {
            throw new IllegalStateException("Билет группы " + ref.text() + " отсутствует: группу нельзя исполнять");
        }
        this.groups = Objects.requireNonNull(
                TownAuditWatch.peekGroups((ServerLevel) level), "Группы аудита ещё не собраны: билет не исполняется");
        Group snapshot = Objects.requireNonNull(
                groups.snapshot(ref), "Снимок группы " + ref.text() + " устарел: билет исполняется заново");
        if (snapshot.status() == Group.Status.BLOCKED || snapshot.status() == Group.Status.QUARANTINED) {
            throw new IllegalStateException("Группа " + ref.text() + " не исполняется: статус " + snapshot.status());
        }
        if (snapshot.status() == Group.Status.ISSUED && !ticket.equals(groups.ticketFor(ref.id()))) {
            throw new IllegalStateException("Группа " + ref.text() + " уже выдана другому билету");
        }
        this.items = snapshot.ordered().stream().limit(MAX_ITEMS).toList();
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время группы " + ref.text(), null);
        }
        if (action != null) {
            return actionTick();
        }
        if (index >= items.size()) {
            groups.release(ticket);
            outcome = ScriptStatus.SUCCEEDED;
            return outcome;
        }
        DiffItem item = items.get(index);
        if (!approached && approachTick(item)) {
            return outcome;
        }
        BlockPos pos = AuditGroupSupport.pos(item.cell());
        String actual = LeaseStates.text(level.getBlockState(pos));
        AuditFactCheck.Decision decision = AuditFactCheck.of(item, actual);
        if (decision == AuditFactCheck.Decision.ALREADY_DONE) {
            return miss(item, actual, AuditMiss.ALREADY_DONE);
        }
        if (decision == AuditFactCheck.Decision.FOREIGN_BLOCK) {
            return miss(item, actual, AuditMiss.FOREIGN_BLOCK);
        }
        if (item.action() == DiffItem.Action.FIX_STATE) {
            BlockState wanted = LeaseStates.state(AuditGroupSupport.wantedText(item));
            if (!SpiritBlocks.place((ServerLevel) level, pos, wanted, spirit, "AUDIT_GROUP")) {
                groups.block(ref.id(), "состояние " + item.cell() + " не изменилось: блок защищён");
                return fail("состояние " + item.cell() + " не изменилось: блок защищён", null);
            }
            BlockState updated = Block.updateFromNeighbourShapes(wanted, level, pos);
            if (!updated.equals(wanted)) {
                SpiritBlocks.place((ServerLevel) level, pos, updated, spirit, "AUDIT_GROUP");
            }
            advance();
            return ScriptStatus.RUNNING;
        }
        action = TaskRunner.start(script(item), SpiritScripts.registryFor(spirit), ConditionRegistry.of(Map.of()));
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus actionTick() {
        TaskRunner running = Objects.requireNonNull(action, "Действие группы не может быть null");
        RunStatus status = running.tick();
        if (status == RunStatus.RUNNING) {
            return ScriptStatus.RUNNING;
        }
        action = null;
        if (status == RunStatus.SUCCEEDED) {
            advance();
            return ScriptStatus.RUNNING;
        }
        if (status == RunStatus.CANCELLED) {
            outcome = ScriptStatus.CANCELLED;
            groups.release(ticket);
            return outcome;
        }
        String failure = running.failureReason().orElse("действие группы завершилось без причины");
        FailureFact failureFact = running.failureFact().orElse(null);
        if (failureFact == FailureFact.NO_MATERIAL || failureFact == FailureFact.NO_TOOL) {
            groups.release(ticket);
        } else {
            groups.block(ref.id(), failure);
        }
        return fail(failure, failureFact);
    }

    private boolean approachTick(DiffItem item) {
        BlockPos pos = AuditGroupSupport.pos(item.cell());
        if (spirit.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= BlockBreaker.REACH) {
            approached = true;
            return false;
        }
        if (stands.isEmpty()) {
            stands.addAll(
                    StandSpots.of(item.cell(), new LevelSafetyView(level), AuditGroupSupport.spiritCoord(spirit)));
            if (stands.isEmpty()) {
                StandSpots.above(item.cell(), new LevelSafetyView(level)).ifPresent(stands::add);
            }
        }
        if (standIndex >= stands.size()) {
            groups.block(ref.id(), "к клетке " + pos.toShortString() + " не подойти");
            fail("к клетке " + pos.toShortString() + " не подойти: стоянки недостижимы", FailureFact.NO_PATH);
            return true;
        }
        if (!approach.isActive()) {
            BlockCoord stand = stands.get(standIndex);
            approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), 0.7);
        }
        switch (approach.tick()) {
            case MOVING -> {
                return false;
            }
            case ARRIVED -> approached = true;
            case STUCK, TIMEOUT -> {
                approach.cancel();
                standIndex++;
            }
        }
        return false;
    }

    private ScriptStatus miss(DiffItem item, String actual, String kind) {
        AuditMiss miss = AuditMisses.of((ServerLevel) level, ref, item, actual, kind);
        TownAuditWatch.quarantine((ServerLevel) level, ref.id(), item.cell());
        AuditIncidents.report((ServerLevel) level, miss);
        return fail(miss.text(), null);
    }

    private ScriptCall script(DiffItem item) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", item.cell().x());
        params.put("y", item.cell().y());
        params.put("z", item.cell().z());
        if (item.action() == DiffItem.Action.BREAK) {
            return new ScriptCall("break_block", params);
        }
        params.put("block", AuditGroupSupport.wantedText(item));
        return new ScriptCall("place_block", params);
    }

    private void advance() {
        index++;
        approached = false;
        standIndex = 0;
        stands.clear();
        approach.cancel();
    }

    private ScriptStatus fail(String reason, @Nullable FailureFact fact) {
        this.reason = reason;
        this.fact = fact;
        outcome = ScriptStatus.FAILED;
        groups.release(ticket);
        return outcome;
    }

    @Override
    public Optional<Progress> progress() {
        return items.isEmpty() ? Optional.empty() : Optional.of(new Progress(index, items.size()));
    }

    @Override
    public Optional<FailureFact> failureFact() {
        return Optional.ofNullable(fact);
    }

    @Override
    public String failureReason() {
        return reason == null ? "" : reason;
    }

    @Override
    public Optional<ScriptResult> result() {
        return outcome == ScriptStatus.SUCCEEDED
                ? Optional.of(new ScriptResult.Data(Map.of("group", ref.id(), "done", index)))
                : Optional.empty();
    }

    @Override
    public void cancel() {
        if (action != null) {
            action.cancel();
            action = null;
        }
        approach.cancel();
        groups.release(ticket);
        outcome = ScriptStatus.CANCELLED;
    }

    @Override
    public void suspend() {
        if (action != null) {
            action.suspend();
        }
        approach.suspend();
    }

    @Override
    public void resume() {
        if (action != null) {
            action.resume();
        }
        approached = false;
        approach.cancel();
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of("index", index, "approached", approached);
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        index = ScriptArgs.optionalInt(state, "index").orElse(0);
        approached = ScriptArgs.optionalBoolean(state, "approached", false);
        standIndex = 0;
        stands.clear();
        approach.cancel();
    }
}
