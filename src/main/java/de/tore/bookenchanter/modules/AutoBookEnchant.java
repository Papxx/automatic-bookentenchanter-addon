package de.tore.bookenchanter.modules;

import de.tore.bookenchanter.BookEnchanterAddon;
import de.tore.bookenchanter.data.TableEnchant;
import de.tore.bookenchanter.data.Target;
import de.tore.bookenchanter.game.OfferSimulator;
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
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
        .description("Ticks between two actions. Lower is faster but more obvious.")
        .defaultValue(4)
        .range(1, 40)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Boolean> useTop = sgGeneral.add(new BoolSetting.Builder()
        .name("use-top")
        .description("Let the top slot count as a hit. Off by default because this slot is used for rerolling.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> useMiddle = sgGeneral.add(new BoolSetting.Builder()
        .name("use-middle")
        .description("Let the middle slot count as a hit.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useBottom = sgGeneral.add(new BoolSetting.Builder()
        .name("use-bottom")
        .description("Let the bottom slot count as a hit.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxRerolls = sgGeneral.add(new IntSetting.Builder()
        .name("max-rerolls")
        .description("Stop after this many rerolls. 0 means unlimited.")
        .defaultValue(0)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Integer> targetCount = sgGeneral.add(new IntSetting.Builder()
        .name("target-count")
        .description("Stop after this many hits. 0 means unlimited.")
        .defaultValue(0)
        .range(0, 1000)
        .sliderRange(0, 1000)
        .build()
    );

    private final Setting<Integer> minLevels = sgGeneral.add(new IntSetting.Builder()
        .name("min-levels")
        .description("Stop once you have fewer experience levels than this.")
        .defaultValue(30)
        .range(1, 100)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<JunkBooks> junkBooks = sgGeneral.add(new EnumSetting.Builder<JunkBooks>()
        .name("junk-books")
        .description("What to do with books that hit none of your targets.")
        .defaultValue(JunkBooks.KEEP)
        .build()
    );

    private final Setting<Boolean> predict = sgGeneral.add(new BoolSetting.Builder()
        .name("predict")
        .description("Work out which bookshelf count would offer one of your targets, and print it once per seed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoOpen = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-open")
        .description("Open the nearest enchanting table in reach by yourself.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("notify")
        .description("Print a chat message on every hit.")
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
    private int lastPredictedSeed;
    private boolean predictionUnavailable;

    private int rerolls;
    private int hits;
    private int booksUsed;
    private int levelsSpent;
    private int lapisSpent;

    public AutoBookEnchant() {
        super(BookEnchanterAddon.CATEGORY, "auto-book-enchant",
            "Enchants books at an enchanting table until a target enchantment is offered at your chosen minimum level.");

        Map<TableEnchant.Category, SettingGroup> groups = new EnumMap<>(TableEnchant.Category.class);
        for (TableEnchant.Category category : TableEnchant.Category.values()) {
            groups.put(category, settings.createGroup(category.title(), false));
        }

        for (TableEnchant enchant : TableEnchant.values()) {
            SettingGroup group = groups.get(enchant.category());
            String name = enchant.id().replace('_', '-');

            Setting<Boolean> on = group.add(new BoolSetting.Builder()
                .name(name)
                .description("Target " + enchant.id() + " - about " + enchant.rerollsAt30() + " rerolls at 30 levels.")
                .defaultValue(false)
                .build()
            );

            Setting<Integer> minLevel = group.add(new IntSetting.Builder()
                .name(name + "-level")
                .description("Minimum level for " + enchant.id() + ". Table maximum is " + enchant.maxTable() + ".")
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
        lastPredictedSeed = 0;
        predictionUnavailable = false;

        rerolls = 0;
        hits = 0;
        booksUsed = 0;
        levelsSpent = 0;
        lapisSpent = 0;

        if (activeTargets().isEmpty()) {
            warning("No target enchantment selected. Open a category below and tick at least one enchantment.");
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
            warning("This server does not offer these targets at the table: " + String.join(", ", missing));
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

            // Only reach for a table when no other container is in the way.
            if (autoOpen.get() && mc.player.containerMenu == mc.player.inventoryMenu) {
                if (actionCooldown > 0) actionCooldown--;
                else {
                    actionCooldown = delay.get() - 1;
                    openNearestTable();
                }
            }

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
                stop("The server sent no offers " + MAX_OFFER_TIMEOUTS + " times in a row.");
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
            stop("Cannot clear the table slot - your inventory is probably full.");
            return;
        }

        boolean hit = handleResult(result);

        // DROP throws the junk book straight out of the table slot, which also keeps it from
        // filling the inventory - the reason the setting exists.
        if (!hit && junkBooks.get() == JunkBooks.DROP) InvUtils.drop().slotId(ITEM_SLOT);
        else InvUtils.shiftClick().slotId(ITEM_SLOT);

        resultTakeAttempted = true;

        if (hit && targetCount.get() > 0 && hits >= targetCount.get()) {
            stop("Reached the target count of " + targetCount.get() + ".");
        }
    }

    private void ensureLapis(EnchantmentMenu menu) {
        if (menu.getGoldCount() >= LAPIS_TARGET) {
            transitionTo(State.INSERT_BOOK);
            return;
        }

        if (lapisAttempts >= MAX_LAPIS_ATTEMPTS) {
            stop("Lapis is not reaching the table.");
            return;
        }

        FindItemResult lapis = InvUtils.find(Items.LAPIS_LAZULI);
        if (!lapis.found()) {
            stop("No lapis lazuli in your inventory.");
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
            stop("No books left in your inventory.");
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
            stop("Only " + playerLevel + " levels left, the limit is " + minLevels.get() + ".");
            return;
        }

        Offer[] offers = readOffers(menu);
        int slot = OfferEvaluator.pick(offers, activeTargets(), allowedSlots(), playerLevel, creative);

        if (slot < 0) {
            if (predict.get()) reportBookshelfHint(menu, offers);

            if (!OfferEvaluator.canReroll(offers, playerLevel, menu.getGoldCount(), creative)) {
                stop("Cannot reroll - not enough levels or lapis.");
                return;
            }

            if (maxRerolls.get() > 0 && rerolls >= maxRerolls.get()) {
                stop("Reached the reroll limit of " + maxRerolls.get() + ".");
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
        if (notify.get()) info("Hit: " + describe(stored, active));

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

    /**
     * Tells the player which bookshelf count would offer one of their targets for the current seed.
     *
     * <p>Printed once per seed so it does not spam. The simulation is only trusted after it
     * reproduces the offers the server actually sent; otherwise the player is told once that
     * prediction is unavailable rather than being given a wrong number.
     */
    private void reportBookshelfHint(EnchantmentMenu menu, Offer[] actual) {
        int seed = menu.getEnchantmentSeed();
        if (seed == lastPredictedSeed || predictionUnavailable || mc.level == null) return;
        lastPredictedSeed = seed;

        ItemStack stack = menu.getSlot(ITEM_SLOT).getItem();
        if (stack.isEmpty()) return;

        int currentPower = OfferSimulator.findMatchingPower(mc.level.registryAccess(), stack, seed, actual);
        if (currentPower < 0) {
            predictionUnavailable = true;
            warning("Offer prediction does not match this server, so it stays off for this run.");
            return;
        }

        Map<String, Integer> active = activeTargets();
        boolean[] allowed = allowedSlots();
        List<String> hints = new ArrayList<>();

        for (int power = 0; power <= OfferSimulator.MAX_POWER; power++) {
            if (power == currentPower) continue;

            Offer[] simulated = OfferSimulator.simulate(mc.level.registryAccess(), stack, seed, power);
            int hit = OfferEvaluator.pick(simulated, active, allowed, mc.player.experienceLevel,
                mc.player.hasInfiniteMaterials());

            if (hit >= 0) {
                hints.add(power + " shelves -> " + simulated[hit].enchantId() + " " + simulated[hit].level());
            }
        }

        if (hints.isEmpty()) info("No bookshelf count hits a target with this seed - rerolling.");
        else info("Target reachable without a reroll: " + String.join(", ", hints)
            + " (you have " + currentPower + ").");
    }

    /** Right-clicks the closest enchanting table within reach. */
    private void openNearestTable() {
        BlockPos playerPos = mc.player.blockPosition();
        double reach = mc.player.blockInteractionRange();
        int range = (int) Math.ceil(reach);

        BlockPos closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    if (mc.level.getBlockState(pos).getBlock() != Blocks.ENCHANTING_TABLE) continue;

                    double distance = mc.player.position().distanceToSqr(Vec3.atCenterOf(pos));
                    if (distance < closestDistance && distance <= reach * reach) {
                        closestDistance = distance;
                        closest = pos;
                    }
                }
            }
        }

        if (closest == null) return;

        BlockUtils.interact(new BlockHitResult(Vec3.atCenterOf(closest), Direction.UP, closest, false),
            InteractionHand.MAIN_HAND, true);
    }

    private void stop(String reason) {
        error("Stopped: " + reason);
        info("Stats: " + booksUsed + " books, " + rerolls + " rerolls, " + hits + " hits, "
            + levelsSpent + " levels and " + lapisSpent + " lapis spent.");
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
        return hits + " hits / " + rerolls + " rerolls";
    }
}
