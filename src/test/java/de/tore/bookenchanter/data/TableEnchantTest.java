package de.tore.bookenchanter.data;

import de.tore.bookenchanter.data.TableEnchant.Category;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the enchantment table data against accidental edits. The values are binding per
 * docs/PLAN.md section 3 - a failure here means the data was changed, not that the test is wrong.
 */
class TableEnchantTest {

    @Test
    @DisplayName("exactly 36 entries")
    void hasExactly36Entries() {
        assertEquals(36, TableEnchant.values().length);
    }

    @Test
    @DisplayName("ids are unique")
    void idsAreUnique() {
        Set<String> ids = Arrays.stream(TableEnchant.values())
            .map(TableEnchant::id)
            .collect(Collectors.toSet());

        assertEquals(TableEnchant.values().length, ids.size(), "duplicate id in TableEnchant");
    }

    @ParameterizedTest
    @EnumSource(TableEnchant.class)
    @DisplayName("maxTable is within 1..5")
    void maxTableIsWithinRange(TableEnchant enchant) {
        int max = enchant.maxTable();
        assertTrue(max >= 1 && max <= 5, enchant + " has maxTable " + max + ", expected 1..5");
    }

    @ParameterizedTest
    @DisplayName("sample entries keep their specified maxTable")
    @CsvSource({
        "sharpness, 4",
        "impaling, 5",
        "thorns, 2",
        "quick_charge, 2",
        "breach, 3",
        "protection, 4"
    })
    void sampleEntriesMatchSpec(String id, int expectedMaxTable) {
        TableEnchant enchant = byId(id);
        assertEquals(expectedMaxTable, enchant.maxTable(), id + " has the wrong maxTable");
    }

    @ParameterizedTest
    @DisplayName("treasure enchantments are absent (docs/PLAN.md fact F6)")
    @ValueSource(strings = {
        "mending",
        "frost_walker",
        "soul_speed",
        "swift_sneak",
        "wind_burst",
        "binding_curse",
        "vanishing_curse"
    })
    void treasureEnchantmentsAreAbsent(String treasureId) {
        boolean present = Arrays.stream(TableEnchant.values())
            .anyMatch(enchant -> enchant.id().equals(treasureId));

        assertFalse(present, treasureId + " is not obtainable from an enchanting table");
    }

    @Test
    @DisplayName("category counts match the Kontrolle line in docs/PLAN.md section 3")
    void categoryCountsMatchSpec() {
        Map<Category, Integer> expected = new EnumMap<>(Category.class);
        expected.put(Category.ARMOR, 9);
        expected.put(Category.MELEE, 8);
        expected.put(Category.MACE, 2);
        expected.put(Category.TOOLS, 4);
        expected.put(Category.BOW, 4);
        expected.put(Category.CROSSBOW, 3);
        expected.put(Category.TRIDENT, 4);
        expected.put(Category.FISHING, 2);

        Map<Category, Long> actual = Arrays.stream(TableEnchant.values())
            .collect(Collectors.groupingBy(TableEnchant::category, Collectors.counting()));

        for (Category category : Category.values()) {
            assertEquals(
                expected.get(category).longValue(),
                actual.getOrDefault(category, 0L).longValue(),
                "wrong number of entries in category " + category
            );
        }
    }

    @ParameterizedTest
    @EnumSource(TableEnchant.class)
    @DisplayName("ids are lowercase registry paths")
    void idsAreLowercaseRegistryPaths(TableEnchant enchant) {
        assertTrue(
            enchant.id().matches("[a-z_]+"),
            enchant + " has id '" + enchant.id() + "', expected a lowercase registry path"
        );
    }

    @Test
    @DisplayName("enum constant names follow the ids")
    void constantNamesFollowIds() {
        Set<String> mismatches = new HashSet<>();
        for (TableEnchant enchant : TableEnchant.values()) {
            if (!enchant.name().equals(enchant.id().toUpperCase())) mismatches.add(enchant.name());
        }

        assertTrue(mismatches.isEmpty(), "constant name does not match id: " + mismatches);
    }

    @ParameterizedTest
    @EnumSource(TableEnchant.class)
    @DisplayName("key() resolves to minecraft:<id>")
    void keyResolvesToVanillaNamespace(TableEnchant enchant) {
        ResourceKey<Enchantment> key = enchant.key();

        assertEquals("minecraft", key.identifier().getNamespace(), enchant + " left the vanilla namespace");
        assertEquals(enchant.id(), key.identifier().getPath(), enchant + " resolved to the wrong path");
    }

    @Test
    @DisplayName("key() is interned, so it works as a map key across calls")
    void keyIsUsableAsMapKey() {
        // AutoBookEnchant stores targets in a Map<ResourceKey<Enchantment>, ...> and looks them up
        // with a freshly built key(). ResourceKey does not override equals, so this only works
        // because ResourceKey.create interns. Guard it - a regression would silently empty
        // activeTargets().
        Map<ResourceKey<Enchantment>, String> byKey = new HashMap<>();
        for (TableEnchant enchant : TableEnchant.values()) byKey.put(enchant.key(), enchant.id());

        assertEquals(TableEnchant.values().length, byKey.size(), "keys collided or duplicated");

        for (TableEnchant enchant : TableEnchant.values()) {
            assertEquals(enchant.id(), byKey.get(enchant.key()), "lookup failed for " + enchant);
        }
    }

    private static TableEnchant byId(String id) {
        return Arrays.stream(TableEnchant.values())
            .filter(enchant -> enchant.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no TableEnchant with id " + id));
    }
}
