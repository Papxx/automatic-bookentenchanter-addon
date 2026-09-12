package de.tore.bookenchanter.data;

import meteordevelopment.meteorclient.settings.Setting;

/**
 * The two settings that make up one enchantment target: an on/off toggle and the minimum level
 * the table offer has to reach before it counts as a hit.
 *
 * @param on       whether this enchantment is an active target
 * @param minLevel minimum level, 1 to {@link TableEnchant#maxTable()}
 */
public record Target(Setting<Boolean> on, Setting<Integer> minLevel) {
}
