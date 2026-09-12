package de.tore.bookenchanter.game;

import de.tore.bookenchanter.logic.Offer;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * Reproduces the offers an enchanting table would show, for any bookshelf count.
 *
 * <p>This needs no seed cracking: the server sends the player's enchantment seed to the client as a
 * plain menu data slot ({@code EnchantmentMenu.getEnchantmentSeed()}), and the generation itself is
 * public API ({@link EnchantmentHelper#getEnchantmentCost} and
 * {@link EnchantmentHelper#selectEnchantment}). The sequence below mirrors
 * {@code EnchantmentMenu.slotsChanged} exactly, including the per-slot reseed to {@code seed + slot}
 * and the random removal for books (docs/PLAN.md fact F4).
 *
 * <p>Because a wrong prediction is worse than none, callers should confirm the simulation against
 * the offers the server actually sent - see {@link #findMatchingPower}.
 */
public final class OfferSimulator {

    /** Vanilla counts at most 15 bookshelves around a table. */
    public static final int MAX_POWER = 15;

    private OfferSimulator() {
    }

    /**
     * Simulates the three offers for one bookshelf count.
     *
     * @param access the client registry access, i.e. {@code mc.level.registryAccess()}
     * @param stack  the item in the table slot
     * @param seed   the enchantment seed from {@code EnchantmentMenu.getEnchantmentSeed()}
     * @param power  bookshelf count, 0 to {@link #MAX_POWER}
     */
    public static Offer[] simulate(RegistryAccess access, ItemStack stack, int seed, int power) {
        Registry<Enchantment> registry = access.lookupOrThrow(Registries.ENCHANTMENT);
        RandomSource random = RandomSource.create();

        int[] costs = new int[3];
        random.setSeed(seed);
        for (int slot = 0; slot < 3; slot++) {
            costs[slot] = EnchantmentHelper.getEnchantmentCost(random, slot, power, stack);
            if (costs[slot] < slot + 1) costs[slot] = 0;
        }

        Offer[] offers = new Offer[3];
        for (int slot = 0; slot < 3; slot++) {
            String enchantId = null;
            int level = 0;

            if (costs[slot] > 0) {
                List<EnchantmentInstance> candidates = candidates(registry, random, stack, seed, slot, costs[slot]);
                if (!candidates.isEmpty()) {
                    EnchantmentInstance chosen = candidates.get(random.nextInt(candidates.size()));
                    enchantId = idOf(chosen.enchantment());
                    level = chosen.level();
                }
            }

            offers[slot] = new Offer(costs[slot], enchantId, level);
        }

        return offers;
    }

    /**
     * Finds the bookshelf count whose simulation reproduces the offers the server actually sent.
     *
     * <p>This doubles as a self-check: a match proves the simulation matches this server right now,
     * and it saves reimplementing vanilla bookshelf detection. Returns {@code -1} when no count
     * matches, which means the prediction must not be trusted - for example on a server with
     * modified enchanting.
     *
     * @param actual the offers read from the open menu
     */
    public static int findMatchingPower(RegistryAccess access, ItemStack stack, int seed, Offer[] actual) {
        for (int power = 0; power <= MAX_POWER; power++) {
            if (sameOffers(simulate(access, stack, seed, power), actual)) return power;
        }

        return -1;
    }

    private static boolean sameOffers(Offer[] simulated, Offer[] actual) {
        if (simulated.length != actual.length) return false;

        for (int slot = 0; slot < simulated.length; slot++) {
            Offer a = simulated[slot];
            Offer b = actual[slot];

            if (a.cost() != b.cost() || a.level() != b.level()) return false;
            if (a.enchantId() == null ? b.enchantId() != null : !a.enchantId().equals(b.enchantId())) return false;
        }

        return true;
    }

    /** Mirrors the private {@code EnchantmentMenu.getEnchantmentList}. */
    private static List<EnchantmentInstance> candidates(Registry<Enchantment> registry, RandomSource random,
                                                        ItemStack stack, int seed, int slot, int cost) {
        random.setSeed((long) seed + slot);

        List<Holder<Enchantment>> inTable = new ArrayList<>();
        for (Holder<Enchantment> holder : registry.getTagOrEmpty(EnchantmentTags.IN_ENCHANTING_TABLE)) {
            inTable.add(holder);
        }
        if (inTable.isEmpty()) return List.of();

        List<EnchantmentInstance> candidates =
            new ArrayList<>(EnchantmentHelper.selectEnchantment(random, stack, cost, inTable.stream()));

        // For books one rolled enchantment is dropped again (docs/PLAN.md fact F4).
        if (stack.getItem() == Items.BOOK && candidates.size() > 1) {
            candidates.remove(random.nextInt(candidates.size()));
        }

        return candidates;
    }

    private static String idOf(Holder<Enchantment> holder) {
        if (holder == null) return null;

        return holder.unwrapKey()
            .map(ResourceKey::identifier)
            .filter(id -> Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace()))
            .map(Identifier::getPath)
            .orElse(null);
    }
}
