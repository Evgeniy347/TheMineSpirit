package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.HerdCycles;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.husbandry.FeedTable;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.husbandry.SpeciesIds;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

final class BoardHerdDesk {
    private final Board board;
    private final HerdCycles animals;
    private final Map<String, TicketTarget> targets;
    private final Map<String, Capture> captures = new LinkedHashMap<>();

    BoardHerdDesk(Board board, HerdCycles animals, Map<String, TicketTarget> targets) {
        this.board = Objects.requireNonNull(board, "board не может быть null: циклы стада живут на доске");
        this.animals = Objects.requireNonNull(animals, "animals не может быть null: циклы животных по видам");
        this.targets = Objects.requireNonNull(targets, "targets не может быть null: цели задания общие с доской");
    }

    void noteCapture(String captureId, Species species, UUID animal, TicketTarget penTarget) {
        Objects.requireNonNull(captureId, "captureId не может быть null: по нему идёт причина задания");
        Objects.requireNonNull(species, "Вид пойманного животного не может быть null: цикл идёт по виду");
        Objects.requireNonNull(animal, "UUID животного не может быть null: без него живость не проверить");
        captures.put(captureId, new Capture(species, animal, "animal:" + captureId, penTarget));
    }

    void captureFailed(String captureId) {
        captures.remove(captureId);
    }

    Set<String> liveDropCauses(ServerLevel level) {
        Set<String> live = new HashSet<>();
        for (BoardTicket ticket : board.all()) {
            if (ticket.boardCase() != BoardCase.COLLECT_DROPS || !ticket.cause().startsWith("drop:")) {
                continue;
            }
            BoardStatus status = board.statusOf(ticket.id()).orElse(null);
            if (status != BoardStatus.PENDING
                    && status != BoardStatus.WAITING_RESOURCES
                    && status != BoardStatus.ASSIGNED) {
                continue;
            }
            String[] parts = ticket.cause().split(":", 5);
            if (parts.length != 5) {
                continue;
            }
            int x;
            int y;
            int z;
            try {
                x = Integer.parseInt(parts[1]);
                y = Integer.parseInt(parts[2]);
                z = Integer.parseInt(parts[3]);
            } catch (NumberFormatException broken) {
                continue;
            }
            String item = parts[4];
            boolean found = level.getEntitiesOfClass(
                                    ItemEntity.class,
                                    new AABB(x - 4, y - 4, z - 4, x + 5, y + 5, z + 5),
                                    drop -> !drop.isRemoved() && item.equals(StackMapper.itemId(drop.getItem())))
                            .isEmpty()
                    == false;
            if (found) {
                live.add(ticket.cause());
            }
        }
        return live;
    }

    Set<UUID> capturedAnimals(Species species) {
        Set<UUID> animals = new HashSet<>();
        for (Capture capture : captures.values()) {
            if (capture.species() == species) {
                animals.add(capture.animal());
            }
        }
        return Set.copyOf(animals);
    }

    Set<String> liveCauses(ServerLevel level) {
        Set<String> live = new HashSet<>();
        Iterator<Map.Entry<String, Capture>> iterator = captures.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Capture> entry = iterator.next();
            Entity entity = level.getEntity(entry.getValue().animal());
            if (entity != null && entity.isAlive() && !entity.isRemoved()) {
                live.add(entry.getValue().cause());
            } else {
                iterator.remove();
                animals.cycle(entry.getValue().species()).animalDied();
            }
        }
        return live;
    }

    void completeCapture(String captureId) {
        animals.cycle(speciesOfCapture(captureId)).captured(captureId);
    }

    void completePen(String penId) {
        Species species = SpeciesIds.of(penId);
        animals.cycle(species).penBuilt(penId);
        TicketTarget pen = targets.get(penId);
        if (pen != null) {
            targets.put(
                    HerdCycles.cycleId(species, "lure"),
                    pen.withSpecies(species)
                            .withLureItem(FeedTable.feedFor(species).get(0)));
        }
    }

    void completeLure(Species species) {
        animals.cycle(species).lured();
    }

    void completeLure() {
        animals.cycle(firstCaptureSpecies()).lured();
    }

    void notifyAnimalDied(Species species) {
        animals.cycle(species).animalDied();
    }

    void notifyAnimalDied() {
        animals.cycle(firstCaptureSpecies()).animalDied();
    }

    Species speciesFor(BoardTicket ticket) {
        TicketTarget target = targets.get(ticket.id());
        if (target != null && target.species() != null) {
            return target.species();
        }
        Capture capture = captures.get(ticket.id());
        return capture == null ? SpeciesIds.of(ticket.id()) : capture.species();
    }

    private Species speciesOfCapture(String captureId) {
        Capture capture = captures.get(captureId);
        return capture == null ? SpeciesIds.of(captureId) : capture.species();
    }

    private Species firstCaptureSpecies() {
        return captures.values().stream().findFirst().map(Capture::species).orElse(Species.COW);
    }

    private record Capture(Species species, UUID animal, String cause, TicketTarget penTarget) {}
}
