# docs/API_NOTES.md – Ergebnis der API-Verifikation (M1)

> Geprüft am 12.09.2026 mit `javap` 25.0.3 gegen die echten Jars des Projekt-Classpaths.
> Zielversionen laut `gradle/libs.versions.toml`: Minecraft **26.2**, Meteor **26.2-SNAPSHOT**.
>
> Verwendete Jars (über `.\gradlew.bat printClasspath` ermittelt):
> - Minecraft: `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-83e224879c/26.2/minecraft-merged-83e224879c-26.2.jar`
> - Meteor: `~/.gradle/caches/modules-2/files-2.1/meteordevelopment/meteor-client/26.2-SNAPSHOT/.../meteor-client-26.2-SNAPSHOT.jar`
>
> **Ergebnis: alle ⚠️-Punkte aus `docs/PLAN.md` Abschnitt 5 sind ✅. Kein ❌, kein Ersatz nötig.**

## Minecraft

| Aufruf (laut PLAN) | Echte Signatur | Status | Ersatz |
|---|---|---|---|
| `EnchantmentMenu.costs` | `public final int[] costs` | ✅ | – |
| `EnchantmentMenu.enchantClue` | `public final int[] enchantClue` | ✅ | – |
| `EnchantmentMenu.levelClue` | `public final int[] levelClue` | ✅ | – |
| Länge der drei Arrays = 3 | Bytecode des Konstruktors: 3× `iconst_3; newarray int` | ✅ | – |
| `EnchantmentMenu.getEnchantmentSeed()` | `public int getEnchantmentSeed()` | ✅ | – |
| `EnchantmentMenu` Lapis-Anzahl (vermutet: `getGoldCount()`) | `public int getGoldCount()` | ✅ | – |
| `AbstractContainerMenu.containerId` | `public final int containerId` | ✅ | – |
| `AbstractContainerMenu.getSlot(int)` | `public Slot getSlot(int)` | ✅ | – |
| `MultiPlayerGameMode.handleInventoryButtonClick(int, int)` | `public void handleInventoryButtonClick(int, int)` | ✅ | – |
| `registryAccess()` | `public RegistryAccess Level.registryAccess()` | ✅ | – |
| `.lookupOrThrow(Registries.ENCHANTMENT)` | `public default <E> Registry<E> lookupOrThrow(ResourceKey<? extends Registry<? extends E>>)` | ✅ | – |
| `Registries.ENCHANTMENT` | `public static final ResourceKey<Registry<Enchantment>> ENCHANTMENT` | ✅ | – |
| `.asHolderIdMap()` | `public default IdMap<Holder<T>> asHolderIdMap()` | ✅ | – |
| `.byId(int)` | `public abstract T IdMap.byId(int)` | ✅ | – |
| `Holder#unwrapKey()` | `public abstract Optional<ResourceKey<T>> unwrapKey()` | ✅ | – |
| `ResourceKey.create(Registries.ENCHANTMENT, Identifier…)` | `public static <T> ResourceKey<T> create(ResourceKey<? extends Registry<T>>, Identifier)` | ✅ | – |
| `Identifier.withDefaultNamespace(String)` | `public static Identifier withDefaultNamespace(String)` | ✅ | – |
| `DataComponents.STORED_ENCHANTMENTS` | `public static final DataComponentType<ItemEnchantments> STORED_ENCHANTMENTS` | ✅ | – |
| `ItemEnchantments` Iteration (Holder + Level) | `public Set<Object2IntMap.Entry<Holder<Enchantment>>> entrySet()` | ✅ | – |
| `EnchantmentTags.IN_ENCHANTING_TABLE` | `public static final TagKey<Enchantment> IN_ENCHANTING_TABLE` | ✅ | – |
| `Registry#getTagOrEmpty(...)` | `public default Iterable<Holder<T>> getTagOrEmpty(TagKey<T>)` | ✅ | – |
| Creative-Check `Player#hasInfiniteMaterials()` | `public boolean hasInfiniteMaterials()` | ✅ | – |
| Creative-Check `getAbilities().instabuild` | `public Abilities getAbilities()` / `public boolean Abilities.instabuild` | ✅ | – |
| `Items.BOOK` | `public static final Item BOOK` | ✅ | – |
| `Items.LAPIS_LAZULI` | `public static final Item LAPIS_LAZULI` | ✅ | – |
| `Items.ENCHANTED_BOOK` | `public static final Item ENCHANTED_BOOK` | ✅ | – |

## Meteor

| Aufruf (laut PLAN) | Echte Signatur | Status | Ersatz |
|---|---|---|---|
| `InvUtils.drop()` | `public static InvUtils.Action drop()` | ✅ | – |
| `InvUtils.drop().slotId(int)` | `public void InvUtils.Action.slotId(int)` | ✅ | – |
| `InvUtils.shiftClick().slot(int)` | `public void InvUtils.Action.slot(int)` | ✅ | – |
| `InvUtils.find(Item...)` | `public static FindItemResult find(Item...)` | ✅ | – |
| `FindItemResult.found()` / `.slot()` | `public boolean found()` / `public int slot()` | ✅ | – |
| `Module.info(...)` | `public void info(String, Object...)` und `public void info(Component)` | ✅ | – |
| `Module.warning(...)` | `public void warning(String, Object...)` | ✅ | – |
| `Module.error(...)` | `public void error(String, Object...)` | ✅ | – |
| `Module.toggle()` | `public void toggle()` | ✅ | – |

## Wichtige Details für die Umsetzung

Beim Prüfen aufgefallen – relevant für M4/M5/M6:

1. **`IdMap.byId(int)` kann `null` liefern** (nur `byIdOrThrow(int)` wirft). Beim Auflösen von
   `enchantClue[s]` also null-prüfen, statt auf eine Exception zu bauen. Deckt sich mit der
   PLAN-Regel „`enchantId = null` bei unbekanntem Namespace".
2. **`ItemEnchantments.entrySet()` liefert fastutil-Typen** (`Object2IntMap.Entry`), nicht
   `java.util.Map.Entry`. Für M6 den Import `it.unimi.dsi.fastutil.objects.Object2IntMap` setzen.
   Alternativen ohne fastutil: `keySet()` + `getLevel(Holder)`.
3. **`costs`/`enchantClue`/`levelClue` sind `final`** – das betrifft nur die Referenz, der
   Array-Inhalt wird vom Server aktualisiert und ist normal lesbar.
4. **`InvUtils.Action.slot(int)` und `.slotId(int)` geben beide `void` zurück**, sind also
   terminal. Die Unterscheidung aus PLAN Abschnitt 8 (Inventar-Index vs. Menü-Slot-ID) gilt
   unverändert.
5. **`Module` hat `protected final Minecraft mc`** – kein eigenes `Minecraft.getInstance()` nötig.
6. **Zugriffspfade bestätigt:** `Minecraft.level` (`ClientLevel`), `Minecraft.player` (`LocalPlayer`),
   `Minecraft.gameMode` (`MultiPlayerGameMode`), `Player.containerMenu` (`AbstractContainerMenu`,
   nicht final), `Player.experienceLevel` (`public int`) für den Level-Check in M4/M5.
