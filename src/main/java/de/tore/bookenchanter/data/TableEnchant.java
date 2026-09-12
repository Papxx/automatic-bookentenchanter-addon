package de.tore.bookenchanter.data;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * The 36 enchantments obtainable from an enchanting table (tag {@code #minecraft:in_enchanting_table}),
 * exactly as specified in docs/PLAN.md section 3.
 *
 * <p>{@code maxTable} is the highest level the table can produce for a book. It doubles as the
 * slider maximum and default. For some entries it is deliberately lower than the game maximum
 * (noted inline) because a book has enchantability 1 - see docs/PLAN.md fact F5. These values are
 * binding; do not "correct" them against the game maximum.
 *
 * <p>{@code rerollsAt30} is a rough expected number of rerolls at 30 levels (Monte Carlo estimate)
 * and is only used for setting descriptions.
 */
public enum TableEnchant {
    PROTECTION(Category.ARMOR, "protection", 4, 15),
    FIRE_PROTECTION(Category.ARMOR, "fire_protection", 4, 245),
    BLAST_PROTECTION(Category.ARMOR, "blast_protection", 4, 75),
    PROJECTILE_PROTECTION(Category.ARMOR, "projectile_protection", 4, 900),   // much better at xpCost ~23
    FEATHER_FALLING(Category.ARMOR, "feather_falling", 4, 115),   // much better at xpCost ~25
    RESPIRATION(Category.ARMOR, "respiration", 3, 80),
    AQUA_AFFINITY(Category.ARMOR, "aqua_affinity", 1, 60),   // better at low levels
    THORNS(Category.ARMOR, "thorns", 2, 160),   // game max 3 - III not obtainable from the table
    DEPTH_STRIDER(Category.ARMOR, "depth_strider", 3, 80),
    SHARPNESS(Category.MELEE, "sharpness", 4, 135),   // game max 5 - V not obtainable from the table
    SMITE(Category.MELEE, "smite", 4, 32),   // game max 5
    BANE_OF_ARTHROPODS(Category.MELEE, "bane_of_arthropods", 4, 32),   // game max 5
    KNOCKBACK(Category.MELEE, "knockback", 2, 26),
    FIRE_ASPECT(Category.MELEE, "fire_aspect", 2, 62),
    LOOTING(Category.MELEE, "looting", 3, 265),
    SWEEPING_EDGE(Category.MELEE, "sweeping_edge", 3, 63),
    LUNGE(Category.MELEE, "lunge", 3, 26),   // spear
    DENSITY(Category.MACE, "density", 4, 32),   // game max 5
    BREACH(Category.MACE, "breach", 3, 300),   // game max 4
    EFFICIENCY(Category.TOOLS, "efficiency", 4, 22),   // game max 5
    FORTUNE(Category.TOOLS, "fortune", 3, 270),
    SILK_TOUCH(Category.TOOLS, "silk_touch", 1, 127),
    UNBREAKING(Category.TOOLS, "unbreaking", 3, 26),
    POWER(Category.BOW, "power", 4, 21),   // game max 5
    PUNCH(Category.BOW, "punch", 2, 156),
    FLAME(Category.BOW, "flame", 1, 64),
    INFINITY(Category.BOW, "infinity", 1, 126),
    MULTISHOT(Category.CROSSBOW, "multishot", 1, 65),
    PIERCING(Category.CROSSBOW, "piercing", 4, 22),
    QUICK_CHARGE(Category.CROSSBOW, "quick_charge", 2, 63),   // game max 3 - III not obtainable from the table
    IMPALING(Category.TRIDENT, "impaling", 5, 320),
    LOYALTY(Category.TRIDENT, "loyalty", 3, 25),
    RIPTIDE(Category.TRIDENT, "riptide", 3, 107),
    CHANNELING(Category.TRIDENT, "channeling", 1, 130),
    LUCK_OF_THE_SEA(Category.FISHING, "luck_of_the_sea", 3, 270),
    LURE(Category.FISHING, "lure", 3, 278),
    ;

    /** Grouping used for the collapsed setting groups in the module UI. */
    public enum Category {
        ARMOR("Armor"),
        MELEE("Melee"),
        MACE("Mace"),
        TOOLS("Tools"),
        BOW("Bow"),
        CROSSBOW("Crossbow"),
        TRIDENT("Trident"),
        FISHING("Fishing");

        private final String title;

        Category(String title) {
            this.title = title;
        }

        /** Group title shown in the module settings. */
        public String title() {
            return title;
        }
    }

    private final Category category;
    private final String id;
    private final int maxTable;
    private final int rerollsAt30;

    TableEnchant(Category category, String id, int maxTable, int rerollsAt30) {
        this.category = category;
        this.id = id;
        this.maxTable = maxTable;
        this.rerollsAt30 = rerollsAt30;
    }

    public Category category() {
        return category;
    }

    /** Registry path without namespace, e.g. {@code "sharpness"}. */
    public String id() {
        return id;
    }

    /** Highest level this enchantment can reach on a book from the table. */
    public int maxTable() {
        return maxTable;
    }

    /** Rough expected reroll count at 30 levels, for setting descriptions only. */
    public int rerollsAt30() {
        return rerollsAt30;
    }

    /** Registry key of this enchantment in the vanilla {@code minecraft} namespace. */
    public ResourceKey<Enchantment> key() {
        return ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace(id));
    }
}
