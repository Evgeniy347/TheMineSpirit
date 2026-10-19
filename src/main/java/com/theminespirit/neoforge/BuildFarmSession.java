package com.theminespirit.neoforge;

import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.farming.FarmPlot;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class BuildFarmSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    private static final String PHASE_KEY = "phase";
    private static final String TILLED_KEY = "tilled";
    private static final String PLANTED_KEY = "planted";
    private static final String TILL = "till";
    private static final String PLANT = "plant";

    private final SpiritEntity spirit;
    private final FarmPlot plot;
    private final CropKind crop;

    private FarmSession till;
    private FarmSession plant;
    private String phase = TILL;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;
    private int tilled;
    private int planted;

    public BuildFarmSession(SpiritEntity spirit, FarmPlot plot, CropKind crop) {
        this.spirit = Objects.requireNonNull(spirit, "Дух не может быть null: без него некому работать на грядке");
        this.plot = Objects.requireNonNull(
                plot, "Прямоугольник грядки не может быть null: без него неизвестно, где копать");
        this.crop = Objects.requireNonNull(crop, "Культура не может быть null: без неё неизвестно, что сажать");
        this.till = FarmSession.tillPlot(spirit, plot);
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время закладки грядки");
        }
        if (TILL.equals(phase)) {
            tickTill();
        }
        if (outcome == Outcome.RUNNING && PLANT.equals(phase)) {
            tickPlant();
        }
        return outcome;
    }

    public void cancel() {
        if (till != null) {
            till.cancel();
        }
        if (plant != null) {
            plant.cancel();
        }
    }

    public Outcome outcome() {
        return outcome;
    }

    public String failureReason() {
        return failureReason;
    }

    public int tilled() {
        return tilled;
    }

    public int planted() {
        return planted;
    }

    public int target() {
        return plot.cells() * 2;
    }

    public int progressDone() {
        return Math.min(target(), tilled + planted);
    }

    public Map<String, Object> resultData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tilled", tilled);
        data.put("planted", planted);
        data.put("crop", crop.name().toLowerCase(Locale.ROOT));
        data.put("cells", plot.cells());
        return Map.copyOf(data);
    }

    public Map<String, Object> saveState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(PHASE_KEY, phase);
        state.put(TILLED_KEY, tilled);
        state.put(PLANTED_KEY, planted);
        return Map.copyOf(state);
    }

    public void restoreState(Map<String, Object> saved) {
        if (saved.get(TILLED_KEY) instanceof Number number) {
            tilled = Math.max(tilled, number.intValue());
        }
        if (saved.get(PLANTED_KEY) instanceof Number number) {
            planted = Math.max(planted, number.intValue());
        }
        if (PLANT.equals(saved.get(PHASE_KEY))) {
            startPlant();
        }
    }

    private void tickTill() {
        FarmSession.Outcome tillOutcome = till.tick();
        tilled = Math.max(tilled, till.done());
        if (tillOutcome == FarmSession.Outcome.FAILED) {
            fail(till.failureReason());
            return;
        }
        if (tillOutcome == FarmSession.Outcome.SUCCEEDED) {
            startPlant();
        }
    }

    private void tickPlant() {
        FarmSession.Outcome plantOutcome = plant.tick();
        planted = Math.max(planted, plant.done());
        if (plantOutcome == FarmSession.Outcome.FAILED) {
            fail(plant.failureReason());
            return;
        }
        if (plantOutcome == FarmSession.Outcome.SUCCEEDED) {
            outcome = Outcome.SUCCEEDED;
        }
    }

    private void startPlant() {
        phase = PLANT;
        plant = FarmSession.plantPlot(spirit, crop, plot);
    }

    private Outcome fail(String reason) {
        failureReason = reason;
        outcome = Outcome.FAILED;
        cancel();
        return outcome;
    }
}
