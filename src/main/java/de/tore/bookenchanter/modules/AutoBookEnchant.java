package de.tore.bookenchanter.modules;

import de.tore.bookenchanter.BookEnchanterAddon;
import de.tore.bookenchanter.data.TableEnchant;
import de.tore.bookenchanter.data.Target;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Automatically enchants books at an enchanting table until a chosen target enchantment is offered
 * at or above its minimum level, rerolling through the top slot otherwise.
 *
 * <p>This milestone (M3) covers the settings and the target lookup only. The tick-driven state
 * machine follows in M5.
 */
public class AutoBookEnchant extends Module {

    /** What to do with a finished book that hit none of the targets. */
    public enum JunkBooks {
        KEEP,
        DROP
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks zwischen zwei Aktionen. Niedriger ist schneller, aber auffälliger.")
        .defaultValue(4)
        .range(1, 40)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Boolean> useTop = sgGeneral.add(new BoolSetting.Builder()
        .name("use-top")
        .description("Obersten Slot als Treffer zulassen. Standardmäßig aus, weil dieser Slot zum Rerollen dient.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> useMiddle = sgGeneral.add(new BoolSetting.Builder()
        .name("use-middle")
        .description("Mittleren Slot als Treffer zulassen.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useBottom = sgGeneral.add(new BoolSetting.Builder()
        .name("use-bottom")
        .description("Untersten Slot als Treffer zulassen.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxRerolls = sgGeneral.add(new IntSetting.Builder()
        .name("max-rerolls")
        .description("Maximale Anzahl Rerolls, danach Stopp. 0 = unbegrenzt.")
        .defaultValue(0)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Integer> targetCount = sgGeneral.add(new IntSetting.Builder()
        .name("target-count")
        .description("Stopp nach so vielen Treffern. 0 = unbegrenzt.")
        .defaultValue(0)
        .range(0, 1000)
        .sliderRange(0, 1000)
        .build()
    );

    private final Setting<Integer> minLevels = sgGeneral.add(new IntSetting.Builder()
        .name("min-levels")
        .description("Stopp, sobald der Spieler weniger Level als diesen Wert hat.")
        .defaultValue(30)
        .range(1, 100)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<JunkBooks> junkBooks = sgGeneral.add(new EnumSetting.Builder<JunkBooks>()
        .name("junk-books")
        .description("Was mit Büchern passiert, die kein Ziel getroffen haben.")
        .defaultValue(JunkBooks.KEEP)
        .build()
    );

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("notify")
        .description("Bei jedem Treffer eine Chat-Meldung ausgeben.")
        .defaultValue(true)
        .build()
    );

    /** One target per table enchantment, in the order of {@link TableEnchant}. */
    private final Map<ResourceKey<Enchantment>, Target> targets = new LinkedHashMap<>();

    public AutoBookEnchant() {
        super(BookEnchanterAddon.CATEGORY, "auto-book-enchant",
            "Verzaubert am Tisch automatisch Bücher, bis eine Ziel-Verzauberung mit dem gewünschten Mindestlevel angeboten wird.");

        Map<TableEnchant.Category, SettingGroup> groups = new EnumMap<>(TableEnchant.Category.class);
        for (TableEnchant.Category category : TableEnchant.Category.values()) {
            groups.put(category, settings.createGroup(category.title(), false));
        }

        for (TableEnchant enchant : TableEnchant.values()) {
            SettingGroup group = groups.get(enchant.category());
            String name = enchant.id().replace('_', '-');

            Setting<Boolean> on = group.add(new BoolSetting.Builder()
                .name(name)
                .description("Ziel: " + enchant.id() + " · ca. " + enchant.rerollsAt30() + " Rerolls bei 30 Leveln")
                .defaultValue(false)
                .build()
            );

            Setting<Integer> minLevel = group.add(new IntSetting.Builder()
                .name(name + "-level")
                .description("Mindestlevel für " + enchant.id() + ". Tisch-Maximum: " + enchant.maxTable() + ".")
                .defaultValue(enchant.maxTable())
                .range(1, enchant.maxTable())
                .sliderRange(1, enchant.maxTable())
                .visible(on::get)
                .build()
            );

            targets.put(enchant.key(), new Target(on, minLevel));
        }
    }

    /**
     * The enabled targets as registry path to minimum level, e.g. {@code {"sharpness": 4}}.
     * This is the shape the decision logic in M4 consumes.
     */
    public Map<String, Integer> activeTargets() {
        Map<String, Integer> active = new LinkedHashMap<>();

        for (TableEnchant enchant : TableEnchant.values()) {
            Target target = targets.get(enchant.key());
            if (target != null && target.on().get()) active.put(enchant.id(), target.minLevel().get());
        }

        return active;
    }

    /** Immutable view of every target, keyed by enchantment registry key. */
    public Map<ResourceKey<Enchantment>, Target> targets() {
        return Collections.unmodifiableMap(targets);
    }

    /** Which of the three offer slots may count as a hit, indexed slot 0 (top) to 2 (bottom). */
    public boolean[] allowedSlots() {
        return new boolean[] {useTop.get(), useMiddle.get(), useBottom.get()};
    }

    public int delay() {
        return delay.get();
    }

    public int maxRerolls() {
        return maxRerolls.get();
    }

    public int targetCount() {
        return targetCount.get();
    }

    public int minLevels() {
        return minLevels.get();
    }

    public JunkBooks junkBooks() {
        return junkBooks.get();
    }

    public boolean notifyOnHit() {
        return notify.get();
    }
}
