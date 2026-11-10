package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.BuildGap;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.ResourceGate;
import com.theminespirit.core.board.StageAudit;
import com.theminespirit.core.board.StockNeed;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class StageStepOpener {
    private static final String GATHER_PREFIX = "gather:";
    private static final Logger LOGGER = LogUtils.getLogger();

    private StageStepOpener() {}

    static Map<String, Integer> open(GraphPost post, FirstStepsRunner.StepOrder order) {
        ServerLevel level = post.level;
        BoardCase caseValue = order.caseValue();
        StageSite stage = post.scan.stage();
        boolean blocked = post.scan.facts().blocked().contains(caseValue);
        String wood = BuildingWoodChoice.settle(
                level, post.service, caseValue, stage, post.stock.available(), post.scan.wood(), post.now);
        TicketTarget target = FirstStepsRunner.targetFor(level, caseValue, stage, wood);
        if (caseValue == BoardCase.CLEAR_GROUNDS) {
            target = target.withLeveling(SettlementGround.leveling(level, stage));
        }
        FirstStepsRunner.OpenStep step = new FirstStepsRunner.OpenStep(caseValue, target, Map.of(), wood);
        Map<String, Integer> need = StageMaterialDesk.need(level, post.stock, step, blocked);
        if (StageSettle.AUDIT_STEPS.contains(caseValue)
                && reopenDone(
                        level,
                        post.service,
                        new FirstStepsRunner.OpenStep(caseValue, target, need, wood),
                        FirstStepsRunner.partsOf(caseValue))) {
            return Map.of();
        }
        Map<String, Integer> missing = ResourceGate.missing(new StockNeed(need), post.stock);
        if (!missing.isEmpty()) {
            LOGGER.info("Постройка этапа {} ждёт: не хватает {}", caseValue, missing);
            return missing;
        }
        openStep(post, new FirstStepsRunner.OpenStep(caseValue, target, need, wood), order);
        return Map.of();
    }

    private static void openStep(GraphPost post, FirstStepsRunner.OpenStep step, FirstStepsRunner.StepOrder order) {
        MaterialSource source = FirstStepsRunner.materialSource(post.level);
        BoardCase caseValue = step.caseValue();
        TicketTarget target = step.target();
        Map<String, Integer> need = step.need();
        String wood = step.wood();
        int parts = FirstStepsRunner.partsOf(caseValue);
        StageWork work = target.work();
        List<StageBuilds.FencePart> fenceParts = caseValue == BoardCase.BUILD_FENCE && work != null
                ? FenceParts.of(post.level, work.site(), parts)
                : List.of();
        List<Integer> openParts = FenceParts.open(fenceParts);
        BoardOutcome outcome = BoardOutcome.DUPLICATE;
        for (int part = 1; part <= parts; part++) {
            TicketTarget partTarget = parts == 1 ? target : target.withCount(part);
            if (caseValue == BoardCase.BUILD_FENCE && work != null) {
                if (!openParts.contains(part)) {
                    continue;
                }
                partTarget = partTarget.withWork(fenceParts.get(part - 1).work());
            }
            Map<String, Integer> reserve = part == 1 ? need : Map.of();
            if (caseValue == BoardCase.BUILD_FENCE) {
                reserve = new HashMap<>(StageSettle.partNeed(source, partTarget, wood));
                reserve.keySet().retainAll(need.keySet());
            }
            BoardOutcome submitted = post.submit(
                    post.dated(
                            post.ticket(
                                    FirstStepsRunner.partId(caseValue, part),
                                    caseValue,
                                    Optional.empty(),
                                    order.priority(),
                                    order.cause(),
                                    Map.copyOf(reserve)),
                            post.now + part - 1),
                    partTarget);
            if (submitted == BoardOutcome.DUPLICATE) {
                post.service.board().reprioritize(FirstStepsRunner.partId(caseValue, part), order.priority());
            }
            if (outcome == BoardOutcome.DUPLICATE) {
                outcome = submitted;
            }
        }
        if (outcome == BoardOutcome.ACCEPTED) {
            LOGGER.info("[layout] постройка этапа {} открыта: материалы на месте", caseValue);
        }
    }

    static boolean reopenDone(ServerLevel level, BoardService service, FirstStepsRunner.OpenStep step, int parts) {
        BoardCase caseValue = step.caseValue();
        StageWork work = Objects.requireNonNull(
                step.target().work(), "Шаг первых шагов без места этапа: план поселения не передан в задание");
        BuildGap.Gap gap = FirstStepsRunner.gap(level, caseValue, work.site(), step.wood());
        if (!gap.confident()) {
            LOGGER.info(
                    "[layout] {}: {} клеток в незагруженных чанках — вывод о постройке отложен до загрузки",
                    caseValue,
                    gap.unknown());
            return true;
        }
        int missing = gap.shortage();
        BoardStatus base =
                service.board().statusOf(FirstStepsRunner.partId(caseValue, 1)).orElse(null);
        boolean partsReady =
                caseValue != BoardCase.BUILD_FENCE || StageWorldFacts.partsDone(service, caseValue, level, work.site());
        StageAudit.Decision decision = StageAudit.decide(base, partsReady, gap.done(), gap.total());
        if (decision == StageAudit.Decision.COMPLETE) {
            StageClose.built(service, caseValue, parts);
            return true;
        }
        if (decision == StageAudit.Decision.OPEN) {
            return false;
        }
        if (StageAudit.unbuilt(missing, gap.total(), BoardTuning.rebuildPercent())) {
            PlotTrace.reportGap(level, caseValue, step.target(), step.wood(), missing, gap.total(), true);
            StageClose.reopen(service, caseValue, parts);
            return false;
        }
        PlotTrace.reportGap(level, caseValue, step.target(), step.wood(), missing, gap.total(), false);
        return false;
    }

    static void dropOtherWood(ServerLevel level, BoardService service, Set<String> woods) {
        for (BoardTicket ticket : List.copyOf(service.board().tickets())) {
            if (!ticket.id().startsWith(GATHER_PREFIX) || !StageWorldFacts.active(service, ticket.id())) {
                continue;
            }
            String item = StageMaterialDesk.bare(ticket.id().substring(GATHER_PREFIX.length()));
            Optional<String> species = WoodSpecies.speciesOf(item);
            if (species.isPresent() && !woods.contains(species.orElseThrow()) && service.cancel(level, ticket.id())) {
                LOGGER.info("[wood] снята добыча {}: породы построек {}", ticket.id(), woods);
            }
        }
    }
}
