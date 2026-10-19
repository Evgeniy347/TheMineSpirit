package com.theminespirit.core.mining;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class GatherPolicy {
    private static final Set<String> FORBIDDEN_EXACT = Set.of(
            "bedrock", "barrier",
            "command_block", "chain_command_block", "repeating_command_block",
            "structure_block", "jigsaw",
            "spawner", "trial_spawner", "vault",
            "end_portal", "end_portal_frame", "nether_portal",
            "hopper", "dispenser", "dropper",
            "furnace", "blast_furnace", "smoker",
            "brewing_stand", "enchanting_table",
            "chest", "trapped_chest", "ender_chest", "barrel", "shulker_box");
    private static final List<String> FORBIDDEN_SUFFIXES = List.of("_chest", "_shulker_box", "_bed");

    private GatherPolicy() {
    }

    public static Set<String> forbiddenExactIds() {
        return FORBIDDEN_EXACT;
    }

    public static Decision check(String blockId) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: тогда непонятно, что запрещать");
        String path = namespacePath(blockId);
        if (FORBIDDEN_EXACT.contains(path)) {
            return new Refused("блок '" + blockId + "' запрещён для gather: это контейнер, механизм, "
                    + "спавнер, портал или неразрушимый блок");
        }
        for (String suffix : FORBIDDEN_SUFFIXES) {
            if (path.endsWith(suffix)) {
                return new Refused("блок '" + blockId + "' запрещён для gather: семейство '" + suffix
                        + "' — контейнеры, кровати или их варианты");
            }
        }
        return new Allowed();
    }

    private static String namespacePath(String blockId) {
        int colon = blockId.lastIndexOf(':');
        return colon < 0 ? blockId : blockId.substring(colon + 1);
    }

    public sealed interface Decision permits Allowed, Refused {
    }

    public record Allowed() implements Decision {
    }

    public record Refused(String reason) implements Decision {
        public Refused {
            Objects.requireNonNull(reason, "Причина отказа не может быть null: по ней строится отчёт игроку");
        }
    }
}
