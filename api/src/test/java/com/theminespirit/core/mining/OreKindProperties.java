package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.api.OreKind;
import java.util.Optional;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

class OreKindProperties {
    @Property
    void ofBlock_AnyBlockOfSpecificKind_ReturnsThatKind(@ForAll OreKind kind) {
        if (kind == OreKind.ANY) {
            return;
        }
        for (String block : OreKind.blocks(kind)) {
            assertEquals(
                    Optional.of(kind),
                    OreKind.ofBlock(block),
                    "Обратное сопоставление обязано держаться для каждого блока вида " + kind
                            + ": иначе добыча и распознавание руды расходятся"
                            + " При смене ofBlock_AnyBlockOfSpecificKind_ReturnsThatKind проверь это утверждение.");
        }
    }

    @Property
    void blocks_AnyKind_ContainsEverySpecificBlock(@ForAll OreKind kind) {
        if (kind == OreKind.ANY) {
            return;
        }
        for (String block : OreKind.blocks(kind)) {
            assertTrue(
                    OreKind.blocks(OreKind.ANY).contains(block),
                    "any обязан включать блок " + block + " из вида " + kind
                            + ": иначе mine_vein(ore=any) пропускает эту руду"
                            + " При смене blocks_AnyKind_ContainsEverySpecificBlock проверь это утверждение.");
        }
    }
}
