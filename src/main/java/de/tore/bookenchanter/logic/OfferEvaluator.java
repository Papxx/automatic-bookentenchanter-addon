package de.tore.bookenchanter.logic;

import java.util.Map;

/**
 * Decides which enchanting table slot to click. Pure logic over primitives - this class must not
 * reference any Minecraft type, which keeps it testable without the game (docs/PLAN.md M4).
 */
public final class OfferEvaluator {

    private OfferEvaluator() {
    }

    /**
     * Picks the slot to enchant, checking the highest slot first so the best offer wins.
     *
     * @param offers       the three offers, index 0 is the top slot
     * @param minLevels    active targets as registry path to minimum level
     * @param allowedSlots which slots may count as a hit, same indexing as {@code offers}
     * @param playerLevel  the player's current experience level
     * @param creative     whether the level requirement can be ignored
     * @return the slot index to click, or {@code -1} if no offer matches a target
     */
    public static int pick(Offer[] offers, Map<String, Integer> minLevels,
                           boolean[] allowedSlots, int playerLevel, boolean creative) {
        if (offers == null || minLevels == null || allowedSlots == null) return -1;

        for (int slot = offers.length - 1; slot >= 0; slot--) {
            if (slot >= allowedSlots.length || !allowedSlots[slot]) continue;

            Offer offer = offers[slot];
            if (offer == null) continue;
            if (offer.cost() <= 0) continue;
            if (offer.enchantId() == null) continue;
            if (!creative && playerLevel < offer.cost()) continue;

            Integer minLevel = minLevels.get(offer.enchantId());
            if (minLevel == null) continue;
            if (offer.level() >= minLevel) return slot;
        }

        return -1;
    }

    /**
     * Whether a finished book hit any target. A book can carry more enchantments than the clue
     * promised, so every stored enchantment is checked, not just the one that was advertised
     * (docs/PLAN.md fact F4).
     *
     * @param enchants the enchantments on the book, registry path to level
     * @param targets  active targets, registry path to minimum level
     */
    public static boolean matches(Map<String, Integer> enchants, Map<String, Integer> targets) {
        if (enchants == null || targets == null) return false;

        for (Map.Entry<String, Integer> enchant : enchants.entrySet()) {
            Integer minLevel = targets.get(enchant.getKey());
            if (minLevel != null && enchant.getValue() >= minLevel) return true;
        }

        return false;
    }

    /**
     * Whether the top slot can be enchanted purely to reroll the offers. Rerolling needs an actual
     * offer in slot 0, one lapis, and - outside creative - enough levels for its cost.
     *
     * @param offers      the three offers, index 0 is the top slot
     * @param playerLevel the player's current experience level
     * @param lapis       lapis currently in the table
     * @param creative    whether the level requirement can be ignored
     */
    public static boolean canReroll(Offer[] offers, int playerLevel, int lapis, boolean creative) {
        if (offers == null || offers.length == 0) return false;

        Offer top = offers[0];
        if (top == null || top.cost() <= 0) return false;
        if (lapis < 1) return false;

        return creative || playerLevel >= top.cost();
    }
}
