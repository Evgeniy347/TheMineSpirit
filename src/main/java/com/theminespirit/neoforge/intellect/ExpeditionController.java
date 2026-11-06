package com.theminespirit.neoforge.intellect;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.intellect.DescentKit;
import com.theminespirit.core.intellect.Expedition;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.Invitation;
import com.theminespirit.core.intellect.InvitationAnswer;
import com.theminespirit.core.intellect.InvitationState;
import com.theminespirit.core.intellect.OutsideRules;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.InventoryTables;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public final class ExpeditionController {
    public static final double AGREE_RADIUS = 4.0;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FOLLOW = "follow";
    private static final String RETURN_HOME = "return_home";
    private static final int PARTNER_RADIUS = 3;
    private static final Map<String, Object> MINE_PARAMS = Map.of("ore", "any", "count", 8, "radius", 16);

    private final Invitation invitation = new Invitation();
    private StockView stock = new StockView(Map.of());
    private Launcher launcher;
    private Dispatch lastDispatch;
    private Expedition active;
    private UUID expeditionId;
    private long lastRepeatTick = Long.MIN_VALUE;
    private long closedAt = Long.MIN_VALUE;
    private boolean dispatched;
    private List<UUID> missionTeam = List.of();
    private UUID missionLeader;
    private String refusal;

    @FunctionalInterface
    public interface Launcher {
        boolean launch(SpiritEntity spirit, String script, TaskNode task, long now);
    }

    public void offer(Expedition planned) {
        if (planned == null) {
            throw new IllegalArgumentException("Поход без плана не предлагается: команда и цель обязательны");
        }
        if (active != null) {
            return;
        }
        active = planned;
        expeditionId = UUID.randomUUID();
        dispatched = false;
        refusal = null;
        invitation.open(planned.startedAt());
        lastRepeatTick = planned.startedAt();
    }

    public boolean dispatchedRecently(long now) {
        return dispatched && now - closedAt < IntellectTuning.loadDefault().inviteTtlTicks();
    }

    public Optional<Expedition> active() {
        return Optional.ofNullable(active);
    }

    public UUID expeditionId() {
        return expeditionId;
    }

    public record Dispatch(String script, List<UUID> team, long tick) {}

    public Optional<Dispatch> lastDispatch() {
        return Optional.ofNullable(lastDispatch);
    }

    public Optional<String> refusal() {
        return Optional.ofNullable(refusal);
    }

    public boolean onAnswer(String expedition, InvitationAnswer answer, long now) {
        if (expedition == null || answer == null) {
            throw new IllegalArgumentException("Ответ без похода не принять: кнопки зовут конкретную команду");
        }
        if (active == null || expeditionId == null || !expeditionId.toString().equals(expedition)) {
            return false;
        }
        if (answer == InvitationAnswer.GO_WITHOUT) {
            invitation.answer(answer, now);
            return true;
        }
        invitation.answer(answer, now);
        lastRepeatTick = now;
        return true;
    }

    public void update(ServerLevel level, List<SpiritEntity> members, StockView stock, long now, Launcher tasks) {
        if (level == null || members == null || stock == null || tasks == null) {
            throw new IllegalArgumentException(
                    "Поход без уровня, духов, запасов или исполнителя не ведётся: команда идёт конкретным составом");
        }
        launcher = tasks;
        this.stock = stock;
        monitorMission(members, now);
        if (active == null) {
            return;
        }
        InvitationState state = invitation.tick(now);
        SpiritEntity inviter = inviter(members, active);
        Optional<ServerPlayer> guide = guideNear(level, inviter);
        if (guide.isPresent() && inviter != null) {
            if (dispatchFollow(members, active, guide.get(), now)) {
                close(now);
            }
            return;
        }
        if (inviter != null
                && now - lastRepeatTick >= IntellectTuning.loadDefault().inviteRepeatTicks()
                && !state.goWithout()) {
            ChatInvitation.send(level, inviter, expeditionId, active.team().size());
            lastRepeatTick = now;
        }
        if (state.goWithout() && dispatchMine(members, active, now)) {
            close(now);
        }
    }

    public void accept(ServerPlayer player, List<SpiritEntity> members, long now) {
        if (player == null || members == null) {
            throw new IllegalArgumentException("Согласие без игрока не принять: за кем-то нужно идти");
        }
        if (active == null || launcher == null) {
            return;
        }
        if (dispatchFollow(members, active, player, now)) {
            close(now);
        }
    }

    private boolean dispatchFollow(List<SpiritEntity> members, Expedition expedition, ServerPlayer guide, long now) {
        String guideName = guide.getGameProfile().getName();
        boolean launched = false;
        for (SpiritEntity spirit : team(members, expedition)) {
            launched |= launch(spirit, FOLLOW, new ScriptCall(FOLLOW, Map.of("target", guideName, "radius", 3)), now);
        }
        if (launched) {
            lastDispatch = new Dispatch(FOLLOW, List.copyOf(expedition.team()), now);
        }
        return launched;
    }

    private boolean dispatchMine(List<SpiritEntity> members, Expedition expedition, long now) {
        List<SpiritEntity> team = team(members, expedition);
        List<String> missing = missingKit(team);
        if (!missing.isEmpty()) {
            String reason = "перед спуском в шахту нужен набор: " + String.join(", ", missing);
            if (!reason.equals(refusal)) {
                LOGGER.info("[graph] экспедиция отложена: {}", reason);
            }
            refusal = reason;
            return false;
        }
        UUID leaderId = expedition.team().get(0);
        SpiritEntity leader = team.stream()
                .filter(spirit -> spirit.getUUID().equals(leaderId))
                .findFirst()
                .orElse(team.get(0));
        boolean launched = false;
        for (SpiritEntity spirit : team) {
            if (spirit.getUUID().equals(leader.getUUID())) {
                launched |= launch(
                        spirit,
                        "mine_vein",
                        new SequenceNode(List.of(
                                new ScriptCall("mine_vein", MINE_PARAMS), new ScriptCall(RETURN_HOME, Map.of()))),
                        now);
            } else {
                launched |= launch(
                        spirit,
                        FOLLOW,
                        new ScriptCall(FOLLOW, Map.of("target", leader.getStringUUID(), "radius", PARTNER_RADIUS)),
                        now);
            }
        }
        if (launched) {
            refusal = null;
            lastDispatch = new Dispatch("mine_vein", List.copyOf(expedition.team()), now);
            missionTeam = List.copyOf(expedition.team());
            missionLeader = leader.getUUID();
        }
        return launched;
    }

    private List<String> missingKit(List<SpiritEntity> team) {
        StackMapper mapper = new StackMapper();
        List<Stack> inventory = new ArrayList<>();
        for (SpiritEntity spirit : team) {
            inventory.addAll(mapper.map(spirit.getInventory()));
        }
        stock.available().forEach((item, count) -> {
            for (int left = count; left > 0; left -= 64) {
                inventory.add(new Stack(item, 0, Math.min(left, 64), 64));
            }
        });
        return DescentKit.missing(OutsideRules.loadDefault().mineKit(), inventory, InventoryTables.values());
    }

    private void monitorMission(List<SpiritEntity> members, long now) {
        if (missionLeader == null) {
            return;
        }
        Map<UUID, SpiritEntity> alive = new HashMap<>();
        for (SpiritEntity spirit : members) {
            if (!spirit.isRemoved()) {
                alive.put(spirit.getUUID(), spirit);
            }
        }
        int present = 0;
        for (UUID id : missionTeam) {
            if (alive.containsKey(id)) {
                present++;
            }
        }
        if (present == missionTeam.size()) {
            return;
        }
        boolean allReturning = present > 0;
        for (UUID id : missionTeam) {
            SpiritEntity spirit = alive.get(id);
            if (spirit == null) {
                continue;
            }
            if (spirit.currentTaskScript().map(RETURN_HOME::equals).orElse(false)) {
                continue;
            }
            allReturning &= launch(spirit, RETURN_HOME, new ScriptCall(RETURN_HOME, Map.of()), now);
        }
        if (allReturning) {
            missionTeam = List.of();
            missionLeader = null;
        }
    }

    private List<SpiritEntity> team(List<SpiritEntity> members, Expedition expedition) {
        List<SpiritEntity> team = new ArrayList<>();
        for (SpiritEntity spirit : members) {
            if (!spirit.isRemoved() && expedition.team().contains(spirit.getUUID())) {
                team.add(spirit);
            }
        }
        return team;
    }

    private SpiritEntity inviter(List<SpiritEntity> members, Expedition expedition) {
        for (SpiritEntity spirit : team(members, expedition)) {
            return spirit;
        }
        return null;
    }

    private Optional<ServerPlayer> guideNear(ServerLevel level, SpiritEntity inviter) {
        if (inviter == null) {
            return Optional.empty();
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceTo(inviter) <= AGREE_RADIUS) {
                return Optional.of(player);
            }
        }
        return Optional.empty();
    }

    private void close(long now) {
        active = null;
        expeditionId = null;
        dispatched = true;
        closedAt = now;
    }

    private boolean launch(SpiritEntity spirit, String script, TaskNode task, long now) {
        return launcher != null && launcher.launch(spirit, script, task, now);
    }
}
