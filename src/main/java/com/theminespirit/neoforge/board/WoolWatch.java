package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.WoolRules;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.intellect.OutsideGate;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.husbandry.HerdSurvey;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.phys.Vec3;

/**
 * Источник доски «шерсть и кровати». Работает только при живом гейте «загон ≥ 4 овец»:
 * до него овец разводят, а шерсть и кровати не трогают. Дикие овцы — вне домашнего круга
 * и не из пойманных, не чаще одного выхода в игровые сутки; число голов за выход —
 * {@code wild_sheep_per_day}. Кроватей не больше духов: спрос — духи без кровати.
 */
public final class WoolWatch {
    public static final String CAUSE = "wool:pen";

    private static final String SHEARS_ID = "wool_shears";
    private static final String SHEAR_ID = "wool_shear";
    private static final String HUNT_ID = "wool_hunt";
    private static final String STRING_ID = "wool_string";
    private static final String BED_ID = "wool_bed";
    private static final long DAY_TICKS = 24_000L;

    private long lastScan = -1;
    private long lastHuntDay = Long.MIN_VALUE;
    private boolean gate;

    public boolean live() {
        return gate;
    }

    public void tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits, StockView stock,
            long now) {
        gate = SpiritConfig.boardEnabled() && !spirits.isEmpty()
                && HerdWatch.penBuilt(service, Species.SHEEP)
                && WoolRules.penReady(service.capturedCount(Species.SHEEP));
        if (!gate) {
            return;
        }
        if (now - lastScan < BoardTuning.herdScanIntervalTicks()) {
            return;
        }
        lastScan = now;
        BlockCoord home = homeOf(spirits);
        ensureShears(service, stock, home, now);
        ensureShear(level, service, stock, home, now);
        ensureHunt(level, service, spirits, home, now);
        ensureString(service, stock, home, now);
        ensureBed(service, stock, spirits.size(), home, now);
    }

    private static void ensureShears(BoardService service, StockView stock, BlockCoord home, long now) {
        if (active(service, SHEARS_ID)) {
            return;
        }
        boolean hasShears = stock.available().getOrDefault(WoolRules.SHEARS, 0) > 0;
        int iron = stock.available().getOrDefault(WoolRules.IRON_INGOT, 0);
        if (!WoolRules.shearsCraftable(iron, hasShears)) {
            return;
        }
        submit(service, SHEARS_ID, BoardCase.CRAFT_SHEARS, now, home,
                Map.of(WoolRules.IRON_INGOT, WoolRules.shearsIron()), TicketTarget.at(home));
    }

    private static void ensureShear(ServerLevel level, BoardService service, StockView stock, BlockCoord home,
            long now) {
        if (active(service, SHEAR_ID) || stock.available().getOrDefault(WoolRules.SHEARS, 0) < 1) {
            return;
        }
        int sheep = unshornInPen(level, service, home);
        if (sheep <= 0) {
            return;
        }
        submit(service, SHEAR_ID, BoardCase.SHEAR_SHEEP, now, home, Map.of(WoolRules.SHEARS, 1),
                TicketTarget.at(home).withCount(sheep));
    }

    private void ensureHunt(ServerLevel level, BoardService service, List<SpiritEntity> spirits, BlockCoord home,
            long now) {
        long day = now / DAY_TICKS;
        if (active(service, HUNT_ID) || !outingAllowed(level, spirits, home)) {
            return;
        }
        if (lastHuntDay == day) {
            return;
        }
        long wild = wildSheepNear(level, service, home);
        if (wild <= 0) {
            return;
        }
        lastHuntDay = day;
        int count = (int) Math.min(wild, WoolRules.wildSheepPerDay());
        submit(service, HUNT_ID, BoardCase.HUNT_WILD_SHEEP, now, home, Map.of(),
                TicketTarget.at(home).withCount(count));
    }

    private static void ensureString(BoardService service, StockView stock, BlockCoord home, long now) {
        if (active(service, STRING_ID)) {
            return;
        }
        int strings = stock.available().getOrDefault(WoolRules.STRING, 0);
        int wool = WoolRules.woolFromStrings(strings);
        if (wool <= 0) {
            return;
        }
        int count = Math.min(wool, CatalogLimits.COUNT_MAX);
        submit(service, STRING_ID, BoardCase.WEAVE_WOOL, now, home,
                Map.of(WoolRules.STRING, count * WoolRules.STRINGS_PER_WOOL),
                TicketTarget.at(home).withCraft(WoolRules.CRAFTED_WOOL, count));
    }

    private static void ensureBed(BoardService service, StockView stock, int spirits, BlockCoord home, long now) {
        if (active(service, BED_ID) || WoolRules.bedsNeeded(WoolRules.bedCount(stock), spirits) <= 0) {
            return;
        }
        Optional<String> color = WoolRules.bedColor(stock);
        Optional<String> planks = WoolRules.planksType(stock);
        if (color.isEmpty() || planks.isEmpty()) {
            return;
        }
        String woolId = WoolRules.woolId(color.orElseThrow());
        submit(service, BED_ID, BoardCase.CRAFT_BED, now, home,
                Map.of(woolId, WoolRules.bedWool(), planks.orElseThrow(), WoolRules.bedPlanks()),
                TicketTarget.at(home).withCraft(WoolRules.bedId(color.orElseThrow()), 1));
    }

    private static void submit(BoardService service, String id, BoardCase boardCase, long now, BlockCoord home,
            Map<String, Integer> required, TicketTarget target) {
        service.submit(new BoardTicket(id, boardCase, Optional.empty(), BoardRules.priorityOf(boardCase),
                now, 1, CAUSE, List.of(), required), target);
    }

    private static boolean active(BoardService service, String id) {
        return service.board().statusOf(id)
                .map(status -> status == BoardStatus.PENDING || status == BoardStatus.WAITING_RESOURCES
                        || status == BoardStatus.ASSIGNED)
                .orElse(false);
    }

    private static int unshornInPen(ServerLevel level, BoardService service, BlockCoord home) {
        Set<UUID> own = service.capturedAnimals(Species.SHEEP);
        Vec3 center = new Vec3(home.x() + 0.5, home.y(), home.z() + 0.5);
        int radius = WoolRules.shearRadius();
        int count = 0;
        for (Animal animal : HerdSurvey.query(level, center, radius)) {
            if (!(animal instanceof Sheep sheep) || animal.isBaby() || sheep.isSheared()
                    || !animal.isAlive() || !own.contains(animal.getUUID())) {
                continue;
            }
            if (distanceSquared(home, animal) <= (long) radius * radius) {
                count++;
            }
        }
        return count;
    }

    private static boolean outingAllowed(ServerLevel level, List<SpiritEntity> spirits, BlockCoord home) {
        OutsideGate gate = OutsideGate.withDefaults();
        int party = 0;
        for (SpiritEntity spirit : spirits) {
            if (!spirit.isRemoved() && spirit.isAlive()) {
                party++;
            }
        }
        boolean playerAccompanied = false;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - home.x();
            double dy = player.getY() - home.y();
            double dz = player.getZ() - home.z();
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= gate.escortRadius()) {
                playerAccompanied = true;
                break;
            }
        }
        return gate.admitExternal(party, playerAccompanied) instanceof OutsideGate.Allowed;
    }

    private static long wildSheepNear(ServerLevel level, BoardService service, BlockCoord home) {
        Set<UUID> own = service.capturedAnimals(Species.SHEEP);
        long radius = WoolRules.wildSheepRadius();
        Vec3 center = new Vec3(home.x() + 0.5, home.y(), home.z() + 0.5);
        long count = 0;
        for (Animal animal : HerdSurvey.query(level, center, radius)) {
            if (!(animal instanceof Sheep) || !animal.isAlive() || own.contains(animal.getUUID())) {
                continue;
            }
            long distance = distanceSquared(home, animal);
            if (distance <= (long) CatalogLimits.HERD_HOME_RADIUS * CatalogLimits.HERD_HOME_RADIUS) {
                continue;
            }
            if (WoolRules.wildTarget(false, distance, (int) radius)) {
                count++;
            }
        }
        return count;
    }

    private static long distanceSquared(BlockCoord home, Animal animal) {
        long dx = (long) animal.blockPosition().getX() - home.x();
        long dy = (long) animal.blockPosition().getY() - home.y();
        long dz = (long) animal.blockPosition().getZ() - home.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static BlockCoord homeOf(List<SpiritEntity> spirits) {
        for (SpiritEntity spirit : spirits) {
            Optional<BlockCoord> home = spirit.home();
            if (home.isPresent()) {
                return home.orElseThrow();
            }
        }
        return new BlockCoord(0, 64, 0);
    }
}
