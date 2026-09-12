package de.tore.bookenchanter.logic;

/**
 * One of the three enchanting table offers, reduced to the primitives the decision logic needs.
 *
 * <p>Deliberately free of Minecraft types so {@link OfferEvaluator} stays unit-testable.
 *
 * @param cost      level requirement shown for the slot; 0 means the slot shows no offer.
 *                  Note this is the requirement, not what gets deducted - that is {@code slot + 1}
 *                  (docs/PLAN.md fact F1).
 * @param enchantId registry path of the clue enchantment, e.g. {@code "sharpness"};
 *                  {@code null} when the slot has no clue or the clue is not a vanilla enchantment
 * @param level     level of the clue enchantment
 */
public record Offer(int cost, String enchantId, int level) {
}
