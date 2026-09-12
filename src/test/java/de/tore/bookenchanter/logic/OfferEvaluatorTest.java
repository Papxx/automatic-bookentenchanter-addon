package de.tore.bookenchanter.logic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the decision rules from docs/PLAN.md M4. Slot 0 is the top slot; the evaluator checks
 * slot 2 first so the most expensive - and therefore best - offer wins.
 */
class OfferEvaluatorTest {

    private static final boolean[] ALL_SLOTS = {true, true, true};
    private static final boolean[] WITHOUT_TOP = {false, true, true};

    /** An empty slot: the server sends cost 0 and no clue. */
    private static final Offer EMPTY = new Offer(0, null, 0);

    private static Offer[] offers(Offer top, Offer middle, Offer bottom) {
        return new Offer[] {top, middle, bottom};
    }

    @Nested
    @DisplayName("pick")
    class Pick {

        @Test
        @DisplayName("hit in the bottom slot is returned")
        void hitInBottomSlot() {
            Offer[] offers = offers(
                new Offer(5, "unbreaking", 1),
                new Offer(12, "looting", 2),
                new Offer(30, "sharpness", 4)
            );

            assertEquals(2, OfferEvaluator.pick(offers, Map.of("sharpness", 4), ALL_SLOTS, 30, false));
        }

        @Test
        @DisplayName("hit only in the top slot, but top is blocked -> -1")
        void hitOnlyInBlockedTopSlot() {
            Offer[] offers = offers(
                new Offer(5, "sharpness", 4),
                new Offer(12, "looting", 2),
                new Offer(30, "fortune", 3)
            );

            assertEquals(-1, OfferEvaluator.pick(offers, Map.of("sharpness", 4), WITHOUT_TOP, 30, false));
        }

        @Test
        @DisplayName("top slot counts when explicitly allowed")
        void topSlotCountsWhenAllowed() {
            Offer[] offers = offers(
                new Offer(5, "sharpness", 4),
                EMPTY,
                EMPTY
            );

            assertEquals(0, OfferEvaluator.pick(offers, Map.of("sharpness", 4), ALL_SLOTS, 30, false));
        }

        @Test
        @DisplayName("level too low for the bottom slot -> falls back to the next lower hit")
        void levelTooLowFallsBackToLowerSlot() {
            Offer[] offers = offers(
                EMPTY,
                new Offer(12, "sharpness", 4),
                new Offer(30, "sharpness", 4)
            );

            assertEquals(1, OfferEvaluator.pick(offers, Map.of("sharpness", 4), ALL_SLOTS, 15, false));
        }

        @Test
        @DisplayName("level too low everywhere -> -1")
        void levelTooLowEverywhere() {
            Offer[] offers = offers(
                EMPTY,
                new Offer(12, "sharpness", 4),
                new Offer(30, "sharpness", 4)
            );

            assertEquals(-1, OfferEvaluator.pick(offers, Map.of("sharpness", 4), ALL_SLOTS, 3, false));
        }

        @Test
        @DisplayName("creative ignores the level requirement")
        void creativeIgnoresLevel() {
            Offer[] offers = offers(
                EMPTY,
                new Offer(12, "sharpness", 4),
                new Offer(30, "sharpness", 4)
            );

            assertEquals(2, OfferEvaluator.pick(offers, Map.of("sharpness", 4), ALL_SLOTS, 0, true));
        }

        @Test
        @DisplayName("level exactly at the minimum is a hit")
        void levelExactlyAtMinimumIsAHit() {
            Offer[] offers = offers(EMPTY, EMPTY, new Offer(30, "protection", 4));

            assertEquals(2, OfferEvaluator.pick(offers, Map.of("protection", 4), ALL_SLOTS, 30, false));
        }

        @Test
        @DisplayName("level below the minimum is no hit")
        void levelBelowMinimumIsNoHit() {
            Offer[] offers = offers(EMPTY, EMPTY, new Offer(30, "protection", 3));

            assertEquals(-1, OfferEvaluator.pick(offers, Map.of("protection", 4), ALL_SLOTS, 30, false));
        }

        @Test
        @DisplayName("level above the minimum is a hit")
        void levelAboveMinimumIsAHit() {
            Offer[] offers = offers(EMPTY, EMPTY, new Offer(30, "protection", 4));

            assertEquals(2, OfferEvaluator.pick(offers, Map.of("protection", 2), ALL_SLOTS, 30, false));
        }

        @Test
        @DisplayName("several hits -> highest slot wins")
        void severalHitsHighestSlotWins() {
            Offer[] offers = offers(
                new Offer(5, "protection", 4),
                new Offer(12, "protection", 4),
                new Offer(30, "protection", 4)
            );

            assertEquals(2, OfferEvaluator.pick(offers, Map.of("protection", 4), ALL_SLOTS, 30, false));
        }

        @Test
        @DisplayName("null enchantId is ignored")
        void nullEnchantIdIsIgnored() {
            Offer[] offers = offers(
                EMPTY,
                new Offer(12, "sharpness", 4),
                new Offer(30, null, 4)
            );

            assertEquals(1, OfferEvaluator.pick(offers, Map.of("sharpness", 4), ALL_SLOTS, 30, false));
        }

        @Test
        @DisplayName("cost 0 is ignored even when the clue matches")
        void zeroCostIsIgnored() {
            Offer[] offers = offers(
                EMPTY,
                new Offer(12, "sharpness", 4),
                new Offer(0, "sharpness", 4)
            );

            assertEquals(1, OfferEvaluator.pick(offers, Map.of("sharpness", 4), ALL_SLOTS, 30, false));
        }

        @Test
        @DisplayName("empty target map -> always -1")
        void emptyTargetMapNeverHits() {
            Offer[] offers = offers(
                new Offer(5, "unbreaking", 3),
                new Offer(12, "looting", 3),
                new Offer(30, "sharpness", 4)
            );

            assertEquals(-1, OfferEvaluator.pick(offers, Map.of(), ALL_SLOTS, 30, false));
        }

        @Test
        @DisplayName("enchantment that is not a target -> -1")
        void nonTargetEnchantmentIsNoHit() {
            Offer[] offers = offers(EMPTY, EMPTY, new Offer(30, "fire_aspect", 2));

            assertEquals(-1, OfferEvaluator.pick(offers, Map.of("sharpness", 4), ALL_SLOTS, 30, false));
        }

        @Test
        @DisplayName("no slot allowed -> -1")
        void noSlotAllowed() {
            Offer[] offers = offers(EMPTY, EMPTY, new Offer(30, "sharpness", 4));

            assertEquals(-1, OfferEvaluator.pick(offers, Map.of("sharpness", 4),
                new boolean[] {false, false, false}, 30, false));
        }
    }

    @Nested
    @DisplayName("canReroll")
    class CanReroll {

        @Test
        @DisplayName("offer in slot 0, enough levels and lapis -> true")
        void rerollPossible() {
            Offer[] offers = offers(new Offer(5, "unbreaking", 1), EMPTY, EMPTY);

            assertTrue(OfferEvaluator.canReroll(offers, 30, 3, false));
        }

        @Test
        @DisplayName("no lapis -> false")
        void noLapis() {
            Offer[] offers = offers(new Offer(5, "unbreaking", 1), EMPTY, EMPTY);

            assertFalse(OfferEvaluator.canReroll(offers, 30, 0, false));
        }

        @Test
        @DisplayName("level below the slot 0 cost -> false")
        void levelBelowTopCost() {
            Offer[] offers = offers(new Offer(5, "unbreaking", 1), EMPTY, EMPTY);

            assertFalse(OfferEvaluator.canReroll(offers, 4, 3, false));
        }

        @Test
        @DisplayName("level exactly at the slot 0 cost -> true")
        void levelExactlyAtTopCost() {
            Offer[] offers = offers(new Offer(5, "unbreaking", 1), EMPTY, EMPTY);

            assertTrue(OfferEvaluator.canReroll(offers, 5, 1, false));
        }

        @Test
        @DisplayName("creative ignores the level requirement")
        void creativeIgnoresLevel() {
            Offer[] offers = offers(new Offer(5, "unbreaking", 1), EMPTY, EMPTY);

            assertTrue(OfferEvaluator.canReroll(offers, 0, 1, true));
        }

        @Test
        @DisplayName("no offer in slot 0 -> false")
        void noOfferInTopSlot() {
            assertFalse(OfferEvaluator.canReroll(offers(EMPTY, EMPTY, EMPTY), 30, 3, false));
        }

        @Test
        @DisplayName("a clue-less but priced slot 0 still allows a reroll")
        void pricedSlotWithoutClueStillRerolls() {
            Offer[] offers = offers(new Offer(5, null, 0), EMPTY, EMPTY);

            assertTrue(OfferEvaluator.canReroll(offers, 30, 1, false));
        }
    }

    @Test
    @DisplayName("logic package stays free of Minecraft types (docs/PLAN.md M4 acceptance)")
    void logicPackageHasNoMinecraftTypes() throws Exception {
        for (Class<?> type : List.of(OfferEvaluator.class, Offer.class)) {
            byte[] bytecode;
            try (InputStream in = type.getResourceAsStream(type.getSimpleName() + ".class")) {
                assertNotNull(in, "class file not found for " + type.getSimpleName());
                bytecode = in.readAllBytes();
            }

            // ISO-8859-1 maps bytes 1:1 to chars, so this searches the raw constant pool.
            String constantPool = new String(bytecode, StandardCharsets.ISO_8859_1);

            assertFalse(constantPool.contains("net/minecraft"),
                type.getSimpleName() + " references a Minecraft type - the logic package must stay pure");
        }
    }
}
