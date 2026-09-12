package de.tore.bookenchanter.modules;

import de.tore.bookenchanter.BookEnchanterAddon;
import de.tore.bookenchanter.data.TableEnchant;
import de.tore.bookenchanter.data.Target;
import de.tore.bookenchanter.logic.Offer;
import de.tore.bookenchanter.logic.OfferEvaluator;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Automatically enchants books at an enchanting table until a chosen target enchantment is offered
 * at or above its minimum level, rerolling through the top slot otherwise.
 *
 * <p>The decision itself lives in {@link OfferEvaluator}; this class only talks to the game and
 * drives the state machine described in docs/PLAN.md M5.
 */
public class AutoBookEnchant extends Module {

    /** What to do with a finished book that hit none of the targets. */
    public enum JunkBooks {
        KEEP,
        DROP
    }

    /** Cycle described in docs/PLAN.md M5. */
    private enum State {
        WAIT_MENU,
        TAKE_RESULT,
        ENSURE_LAPIS,
        INSERT_BOOK,
        WAIT_OFFERS,
        DECIDE,
        WAIT_RESULT
    }

    /** Menu slot holding the item being enchanted (docs/PLAN.md fact F7). */
    private static final int ITEM_SLOT = 0;

    /** Ticks a waiting state may spend before it gives up (docs/PLAN.md M5). */
    private static final int WAIT_TIMEOUT_TICKS = 40;

    /** How often WAIT_OFFERS may time out before the module stops. */
    private static final int MAX_OFFER_TIMEOUTS = 3;

    /** The table keeps up to three lapis; top it up below this. */
    private static final int LAPIS_TARGET = 3;

    /** Safety net so a lapis insert that never arrives cannot spin forever. */
    private static final int MAX_LAPIS_ATTEMPTS = 3;

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

    private State state = State.WAIT_MENU;
    private int actionCooldown;
    private int waitTicks;
    private int offerTimeouts;
    private int lapisAttempts;
    private boolean resultTakeAttempted;

    private int rerolls;
    private int hits;
    private int booksUsed;
    private int levelsSpent;
    private int lapisSpent;

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

    @Override
    public void onActivate() {
        state = State.WAIT_MENU;
        actionCooldown = 0;
        waitTicks = 0;
        offerTimeouts = 0;
        lapisAttempts = 0;
        resultTakeAttempted = false;

        rerolls = 0;
        hits = 0;
        booksUsed = 0;
        levelsSpent = 0;
        lapisSpent = 0;

        if (activeTargets().isEmpty()) {
            warning("Keine Ziel-Verzauberung aktiv - bitte erst eine Kategorie aufklappen und ein Ziel anhaken.");
            toggle();
            return;
        }

        warnAboutTargetsMissingFromTable();
    }

    /**
     * Warns about active targets the server does not list in {@code #minecraft:in_enchanting_table}.
     * A datapack or server change can remove entries, and those targets would never be offered.
     */
    private void warnAboutTargetsMissingFromTable() {
        if (mc.level == null) return;

        Registry<Enchantment> registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Set<String> offeredByTable = new HashSet<>();
        for (Holder<Enchantment> holder : registry.getTagOrEmpty(EnchantmentTags.IN_ENCHANTING_TABLE)) {
            String id = idOf(holder);
            if (id != null) offeredByTable.add(id);
        }

        // An empty tag means it has not been synced yet - that is not a datapack change.
        if (offeredByTable.isEmpty()) return;

        List<String> missing = new ArrayList<>();
        for (String id : activeTargets().keySet()) {
            if (!offeredByTable.contains(id)) missing.add(id);
        }

        if (!missing.isEmpty()) {
            warning("Dieser Server bietet folgende Ziele nicht am Tisch an: " + String.join(", ", missing));
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) {
            state = State.WAIT_MENU;
            return;
        }

        if (!(mc.player.containerMenu instanceof EnchantmentMenu menu)) {
            // GUI closed mid-cycle: idle until it is open again, keeping the statistics.
            state = State.WAIT_MENU;
            return;
        }

        if (state == State.WAIT_MENU) transitionTo(State.TAKE_RESULT);

        // Timeouts count real ticks, not delayed actions, so the delay setting cannot stretch them.
        if (state == State.WAIT_OFFERS || state == State.WAIT_RESULT) {
            if (++waitTicks > WAIT_TIMEOUT_TICKS) {
                onWaitTimeout();
                return;
            }
        }

        if (actionCooldown > 0) {
            actionCooldown--;
            return;
        }
        actionCooldown = delay.get() - 1;

        switch (state) {
            case TAKE_RESULT -> takeResult(menu);
            case ENSURE_LAPIS -> ensureLapis(menu);
            case INSERT_BOOK -> insertBook(menu);
            case WAIT_OFFERS -> waitForOffers(menu);
            case DECIDE -> decide(menu);
            case WAIT_RESULT -> waitForResult(menu);
            case WAIT_MENU -> {
                // Unreachable: handled above.
            }
        }
    }

    private void transitionTo(State next) {
        state = next;
        waitTicks = 0;
        resultTakeAttempted = false;
        lapisAttempts = 0;
    }

    private void onWaitTimeout() {
        if (state == State.WAIT_OFFERS) {
            offerTimeouts++;
            if (offerTimeouts >= MAX_OFFER_TIMEOUTS) {
                stop("Der Server hat " + MAX_OFFER_TIMEOUTS + " mal keine Angebote geschickt.");
                return;
            }
        }

        // Both waiting states fall back to the start of the cycle and try again.
        transitionTo(State.TAKE_RESULT);
    }

    private void takeResult(EnchantmentMenu menu) {
        ItemStack result = menu.getSlot(ITEM_SLOT).getItem();

        if (result.isEmpty()) {
            transitionTo(State.ENSURE_LAPIS);
            return;
        }

        if (resultTakeAttempted) {
            stop("Der Tisch lässt sich nicht leeren - vermutlich ist das Inventar voll.");
            return;
        }

        boolean hit = handleResult(result);

        // DROP throws the junk book straight out of the table slot, which also keeps it from
        // filling the inventory - the reason the setting exists.
        if (!hit && junkBooks.get() == JunkBooks.DROP) InvUtils.drop().slotId(ITEM_SLOT);
        else InvUtils.shiftClick().slotId(ITEM_SLOT);

        resultTakeAttempted = true;

        if (hit && targetCount.get() > 0 && hits >= targetCount.get()) {
            stop("Ziel-Anzahl von " + targetCount.get() + " erreicht.");
        }
    }

    private void ensureLapis(EnchantmentMenu menu) {
        if (menu.getGoldCount() >= LAPIS_TARGET) {
            transitionTo(State.INSERT_BOOK);
            return;
        }

        if (lapisAttempts >= MAX_LAPIS_ATTEMPTS) {
            stop("Lapis landet nicht im Tisch.");
            return;
        }

        FindItemResult lapis = InvUtils.find(Items.LAPIS_LAZULI);
        if (!lapis.found()) {
            stop("Kein Lapislazuli im Inventar.");
            return;
        }

        InvUtils.shiftClick().slot(lapis.slot());
        lapisAttempts++;
    }

    private void insertBook(EnchantmentMenu menu) {
        if (!menu.getSlot(ITEM_SLOT).getItem().isEmpty()) {
            transitionTo(State.WAIT_OFFERS);
            return;
        }

        FindItemResult book = InvUtils.find(Items.BOOK);
        if (!book.found()) {
            stop("Keine Bücher mehr im Inventar.");
            return;
        }

        InvUtils.shiftClick().slot(book.slot());
        booksUsed++;
        transitionTo(State.WAIT_OFFERS);
    }

    private void waitForOffers(EnchantmentMenu menu) {
        for (int cost : menu.costs) {
            if (cost > 0) {
                offerTimeouts = 0;
                transitionTo(State.DECIDE);
                return;
            }
        }
        // Still nothing; the tick-level timeout above will eventually retry.
    }

    private void decide(EnchantmentMenu menu) {
        int playerLevel = mc.player.experienceLevel;
        boolean creative = mc.player.hasInfiniteMaterials();

        if (!creative && playerLevel < minLevels.get()) {
            stop("Nur noch " + playerLevel + " Level, Untergrenze ist " + minLevels.get() + ".");
            return;
        }

        Offer[] offers = readOffers(menu);
        int slot = OfferEvaluator.pick(offers, activeTargets(), allowedSlots(), playerLevel, creative);

        if (slot < 0) {
            if (!OfferEvaluator.canReroll(offers, playerLevel, menu.getGoldCount(), creative)) {
                stop("Reroll nicht möglich - zu wenig Level oder Lapis.");
                return;
            }

            if (maxRerolls.get() > 0 && rerolls >= maxRerolls.get()) {
                stop("Reroll-Limit von " + maxRerolls.get() + " erreicht.");
                return;
            }

            slot = 0;
            rerolls++;
        }

        // Cost is the requirement; what actually gets deducted is slot + 1 (docs/PLAN.md fact F1).
        levelsSpent += slot + 1;
        lapisSpent += slot + 1;

        mc.gameMode.handleInventoryButtonClick(menu.containerId, slot);
        transitionTo(State.WAIT_RESULT);
    }

    private void waitForResult(EnchantmentMenu menu) {
        ItemStack result = menu.getSlot(ITEM_SLOT).getItem();
        if (!result.isEmpty() && result.getItem() == Items.ENCHANTED_BOOK) transitionTo(State.TAKE_RESULT);
    }

    /** Reads the three offers into the Minecraft-free shape the evaluator expects. */
    private Offer[] readOffers(EnchantmentMenu menu) {
        Offer[] offers = new Offer[menu.costs.length];

        for (int slot = 0; slot < offers.length; slot++) {
            offers[slot] = new Offer(menu.costs[slot], enchantIdOf(menu.enchantClue[slot]), menu.levelClue[slot]);
        }

        return offers;
    }

    /**
     * Resolves a clue to a vanilla registry path. The clue is a server-side raw registry id, so it
     * has to go through the holder map; anything outside the {@code minecraft} namespace is treated
     * as no clue (docs/PLAN.md M5).
     */
    private String enchantIdOf(int rawId) {
        if (rawId < 0 || mc.level == null) return null;

        Registry<Enchantment> registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> holder = registry.asHolderIdMap().byId(rawId);

        return idOf(holder);
    }

    /** Registry path of a holder, or {@code null} if it is unknown or not a vanilla enchantment. */
    private String idOf(Holder<Enchantment> holder) {
        if (holder == null) return null;

        return holder.unwrapKey()
            .map(ResourceKey::identifier)
            .filter(id -> Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace()))
            .map(Identifier::getPath)
            .orElse(null);
    }

    /**
     * Evaluates a finished book against the active targets and reports a hit in chat.
     *
     * @return whether the book hit at least one target
     */
    private boolean handleResult(ItemStack result) {
        ItemEnchantments stored = result.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) return false;

        Map<String, Integer> onBook = new LinkedHashMap<>();
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet()) {
            String id = idOf(entry.getKey());
            if (id != null) onBook.put(id, entry.getIntValue());
        }

        Map<String, Integer> active = activeTargets();
        if (!OfferEvaluator.matches(onBook, active)) return false;

        hits++;
        if (notify.get()) info("Treffer: " + describe(stored, active));

        return true;
    }

    /** Formats a book as "Sharpness IV (+ Unbreaking III)", targets first, extras in brackets. */
    private String describe(ItemEnchantments stored, Map<String, Integer> targets) {
        List<String> matched = new ArrayList<>();
        List<String> extra = new ArrayList<>();

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet()) {
            int level = entry.getIntValue();
            String name = Enchantment.getFullname(entry.getKey(), level).getString();
            String id = idOf(entry.getKey());
            Integer minLevel = id == null ? null : targets.get(id);

            if (minLevel != null && level >= minLevel) matched.add(name);
            else extra.add(name);
        }

        String description = String.join(", ", matched);
        if (!extra.isEmpty()) description += " (+ " + String.join(", ", extra) + ")";

        return description;
    }

    private void stop(String reason) {
        error("Gestoppt: " + reason);
        info("Statistik: " + booksUsed + " Bücher, " + rerolls + " Rerolls, " + hits + " Treffer, "
            + levelsSpent + " Level und " + lapisSpent + " Lapis verbraucht.");
        toggle();
    }

    /**
     * The enabled targets as registry path to minimum level, e.g. {@code {"sharpness": 4}}.
     * This is the shape the decision logic consumes.
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

    @Override
    public String getInfoString() {
        return hits + " Treffer / " + rerolls + " Rerolls";
    }
}
