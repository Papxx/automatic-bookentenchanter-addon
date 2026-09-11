# docs/PLAN.md – Spezifikation für Claude Code

> Stand der Recherche: 11.09.2026 (Europe/Berlin). Zielversion: Minecraft Java 26.2, Meteor 26.2-SNAPSHOT.
> Arbeitsweise: Meilensteine **der Reihe nach**. Nach jedem Meilenstein `.\gradlew.bat build` grün → Commit → auf OK warten.

---

## 1. Ziel (verbindlich)

Ein Meteor-Modul `auto-book-enchant`, das bei **geöffnetem Verzauberungstisch**:
1. ein Buch einlegt und Lapis nachfüllt,
2. die 3 Angebote (Clues) liest,
3. bei einem Treffer (Ziel-Verzauberung ≥ Slider-Level) diesen Slot verzaubert,
4. sonst über den **obersten Slot** rerollt,
5. das fertige Buch herausnimmt, auswertet und von vorne beginnt,
6. bei fehlenden Ressourcen, Limits oder geschlossener GUI sauber stoppt.

Die Ziel-Auswahl: pro Verzauberung ein An/Aus + **Slider für das Mindestlevel** (1 bis Tisch-Maximum).

**Nicht im MVP:** Laufen, Tisch automatisch öffnen, Craften, Bücherregal-Manipulation, Seed-Cracking.

---

## 2. Spielmechanik-Fakten (verbindlich – nicht „verbessern“)

| # | Fakt |
|---|---|
| F1 | 3 Slots. `costs[s]` = **Level-Voraussetzung**; bezahlt werden nur `s+1` Level und `s+1` Lapis. |
| F2 | Angebote hängen am Enchanting-Seed des Spielers. Er ändert sich **nur nach einem Verzaubern**. Buch rein/raus ändert nichts → Reroll = Slot 0 verzaubern. |
| F3 | Pro Slot zeigt der Client **nur eine** Verzauberung: `enchantClue[s]` (Registry-ID, −1 = keine) + `levelClue[s]`. |
| F4 | Die Clue-Verzauberung ist garantiert auf dem Ergebnis. Bei Büchern wird bei mehreren gewürfelten Verzauberungen eine zufällig entfernt; weitere können trotzdem drauf sein. |
| F5 | Buch = Enchantability 1 → bei xpCost 30 liegt der interne Enchantment-Cost bei 26–36. Deshalb ist das **Tisch-Maximum oft niedriger als das Spiel-Maximum** (Tabelle Abschnitt 3). |
| F6 | Tisch-Verzauberungen = Tag `#minecraft:in_enchanting_table` (→ `#minecraft:non_treasure`, 36 Einträge). Nie am Tisch: `mending`, `frost_walker`, `soul_speed`, `swift_sneak`, `wind_burst`, `binding_curse`, `vanishing_curse`. |
| F7 | Menü-Slots `EnchantmentMenu`: 0 = Item, 1 = Lapis, 2–28 = Hauptinventar, 29–37 = Hotbar (siehe Meteor `SlotUtils`). |

---

## 3. Daten: alle 36 Tisch-Verzauberungen

`maxTable` ist der **Slider-Maximalwert** und Default. `~Rerolls` = grober Erwartungswert bei 30 Leveln (Simulation), nur für Setting-Beschreibungen.

| Kategorie | ID | maxTable | Spiel-Max | ~Rerolls@30 | Hinweis |
|---|---|---|---|---|---|
| ARMOR | `protection` | 4 | 4 | 15 | |
| ARMOR | `fire_protection` | 4 | 4 | 245 | |
| ARMOR | `blast_protection` | 4 | 4 | 75 | |
| ARMOR | `projectile_protection` | 4 | 4 | 900 | bei xpCost ~23 viel besser |
| ARMOR | `feather_falling` | 4 | 4 | 115 | bei xpCost ~25 viel besser |
| ARMOR | `respiration` | 3 | 3 | 80 | |
| ARMOR | `aqua_affinity` | 1 | 1 | 60 | niedrige Level besser |
| ARMOR | `thorns` | 2 | 3 | 160 | III nicht vom Tisch |
| ARMOR | `depth_strider` | 3 | 3 | 80 | |
| MELEE | `sharpness` | 4 | 5 | 135 | V nicht vom Tisch |
| MELEE | `smite` | 4 | 5 | 32 | |
| MELEE | `bane_of_arthropods` | 4 | 5 | 32 | |
| MELEE | `knockback` | 2 | 2 | 26 | |
| MELEE | `fire_aspect` | 2 | 2 | 62 | |
| MELEE | `looting` | 3 | 3 | 265 | |
| MELEE | `sweeping_edge` | 3 | 3 | 63 | |
| MELEE | `lunge` | 3 | 3 | 26 | Speer |
| MACE | `density` | 4 | 5 | 32 | |
| MACE | `breach` | 3 | 4 | 300 | |
| TOOLS | `efficiency` | 4 | 5 | 22 | |
| TOOLS | `fortune` | 3 | 3 | 270 | |
| TOOLS | `silk_touch` | 1 | 1 | 127 | |
| TOOLS | `unbreaking` | 3 | 3 | 26 | |
| BOW | `power` | 4 | 5 | 21 | |
| BOW | `punch` | 2 | 2 | 156 | |
| BOW | `flame` | 1 | 1 | 64 | |
| BOW | `infinity` | 1 | 1 | 126 | |
| CROSSBOW | `multishot` | 1 | 1 | 65 | |
| CROSSBOW | `piercing` | 4 | 4 | 22 | |
| CROSSBOW | `quick_charge` | 2 | 3 | 63 | III nicht vom Tisch |
| TRIDENT | `impaling` | 5 | 5 | 320 | |
| TRIDENT | `loyalty` | 3 | 3 | 25 | |
| TRIDENT | `riptide` | 3 | 3 | 107 | |
| TRIDENT | `channeling` | 1 | 1 | 130 | |
| FISHING | `luck_of_the_sea` | 3 | 3 | 270 | |
| FISHING | `lure` | 3 | 3 | 278 | |

Kontrolle: ARMOR 9 + MELEE 8 + MACE 2 + TOOLS 4 + BOW 4 + CROSSBOW 3 + TRIDENT 4 + FISHING 2 = **36**.

---

## 4. Architektur

```
src/main/java/de/tore/bookenchanter/
├── BookEnchanterAddon.java          // MeteorAddon-Entry, Kategorie "Book Enchanter"
├── data/
│   ├── TableEnchant.java            // enum, 36 Einträge (Abschnitt 3), key() -> ResourceKey<Enchantment>
│   └── Target.java                  // record Target(Setting<Boolean> on, Setting<Integer> minLevel)
├── logic/
│   ├── Offer.java                   // record Offer(int cost, String enchantId /*nullable*/, int level)
│   └── OfferEvaluator.java          // reine Logik, KEINE Minecraft-Klassen
└── modules/
    └── AutoBookEnchant.java         // Settings, State-Machine, Minecraft-Anbindung
src/test/java/de/tore/bookenchanter/
├── data/TableEnchantTest.java
└── logic/OfferEvaluatorTest.java
docs/
├── PLAN.md                          // diese Datei
└── API_NOTES.md                     // von Claude Code angelegt (M1)
```

Trennung: `OfferEvaluator` bekommt nur primitive Daten (`Offer[]`, `Map<String,Integer>`), damit Tests ohne Minecraft laufen.

---

## 5. API-Referenz

✅ = im Quellcode geprüft (Meteor 26.2, Stand 11.09.2026, bzw. clientcommands 26.3-rc-1)
⚠️ = in M1 per `javap` gegen 26.2 prüfen und in `docs/API_NOTES.md` eintragen

### Minecraft
| Aufruf | Status |
|---|---|
| `EnchantmentMenu.costs`, `.enchantClue`, `.levelClue` (`public int[]`, Länge 3) | ✅ (26.3-rc-1) / ⚠️ für 26.2 bestätigen |
| `EnchantmentMenu.getEnchantmentSeed()` | ✅ / ⚠️ |
| `EnchantmentMenu` Lapis-Anzahl (vermutlich `getGoldCount()`) | ⚠️ |
| `AbstractContainerMenu.containerId`, `getSlot(int)` | ⚠️ |
| `MultiPlayerGameMode.handleInventoryButtonClick(int containerId, int buttonId)` | ✅ (26.3-rc-1) / ⚠️ |
| `registryAccess().lookupOrThrow(Registries.ENCHANTMENT).asHolderIdMap().byId(int)` | ✅ / ⚠️ |
| `Holder#unwrapKey()` | ⚠️ |
| `ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace(id))` | ✅ (Meteor nutzt `Identifier`) |
| `DataComponents.STORED_ENCHANTMENTS` → `ItemEnchantments` (Iteration Holder + Level) | ⚠️ |
| `EnchantmentTags.IN_ENCHANTING_TABLE`, `Registry#getTagOrEmpty(...)` | ✅ / ⚠️ |
| Creative-Check: `Player#hasInfiniteMaterials()` oder `getAbilities().instabuild` | ⚠️ |
| `Items.BOOK`, `Items.LAPIS_LAZULI`, `Items.ENCHANTED_BOOK` | ⚠️ (trivial) |

### Meteor
| Aufruf | Status |
|---|---|
| `Module(Category, String name, String desc)`, `settings.getDefaultGroup()`, `settings.createGroup(String, boolean expanded)` | ✅ |
| `IntSetting.Builder().name().description().defaultValue().range(min,max).sliderRange(min,max).visible(IVisible).build()` | ✅ |
| `BoolSetting.Builder`, `EnumSetting.Builder` | ✅ |
| `@EventHandler` + `TickEvent.Post` | ✅ |
| `InvUtils.find(Item...)` → `FindItemResult` (`found()`, `slot()`) | ✅ |
| `InvUtils.shiftClick().slot(int inventoryIndex)` / `.slotId(int menuSlotId)` | ✅ |
| `InvUtils.drop().slotId(int)` | ⚠️ (Signatur prüfen) |
| `info(...)`, `warning(...)`, `error(...)`, `toggle()` in `Module` | ⚠️ |
| `GenericSetting<T extends IGeneric<T>>` + `WindowScreen` (nur M7) | ✅ |
| `theme.intEdit(value, min, max, sliderMin, sliderMax, noSlider)`, `theme.checkbox(bool)`, `theme.itemWithLabel(stack, name)`, `theme.section(title, expanded)` (nur M7) | ✅ |

---

## 6. Meilensteine

### M0 – Projekt aufsetzen
Aufgaben:
- [ ] Template-Dateien übernehmen (falls Repo leer: `git clone --depth 1 https://github.com/MeteorDevelopment/meteor-addon-template .` in temp-Ordner, Inhalt kopieren, `.git` des Templates nicht übernehmen).
- [ ] Package `com.example.addon` → `de.tore.bookenchanter`, Hauptklasse → `BookEnchanterAddon`.
- [ ] `fabric.mod.json`: `id` = `book-enchanter`, `name` = `Book Enchanter`, Entrypoint anpassen, Autor `Tore`.
- [ ] `gradle.properties`: `archives_base_name=book-enchanter`, `maven_group=de.tore`.
- [ ] Beispiel-Modul, -HUD, -Command und -Mixin löschen; `mixins`-Eintrag + Mixin-JSON entfernen.
- [ ] `getPackage()` = `"de.tore.bookenchanter"`, `getRepo()` auf Platzhalter setzen.
- [ ] JUnit-Jupiter als `testImplementation` + `tasks.test { useJUnitPlatform() }` ergänzen (aktuelle stabile Version selbst ermitteln).
Akzeptanz: `.\gradlew.bat build` grün, keine Template-Reste (`grep -ri "example\|template" src` leer außer Lizenztext).

### M1 – API-Verifikation
Aufgaben:
- [ ] Hilfs-Task in `build.gradle.kts`:
  ```kotlin
  tasks.register("printClasspath") {
      doLast { sourceSets["main"].compileClasspath.forEach { println(it) } }
  }
  ```
- [ ] Jar finden, das `net/minecraft/world/inventory/EnchantmentMenu.class` enthält; ebenso das Meteor-Jar.
- [ ] Jede ⚠️-Zeile aus Abschnitt 5 mit `javap -cp <jar> <klasse>` (bei Bedarf `-p`) prüfen.
- [ ] `docs/API_NOTES.md` anlegen: Tabelle *Aufruf | echte Signatur | Status ✅/❌ | Ersatz*.
Akzeptanz: Alle ⚠️-Punkte haben ein Ergebnis. Bei ❌ **stoppen und berichten**, bevor Ersatz-APIs erfunden werden.

### M2 – Datenmodell
Aufgaben:
- [ ] `TableEnchant` exakt nach Abschnitt 3 (Felder: `id`, `category`, `maxTable`, `rerollsAt30`).
- [ ] `key()` liefert `ResourceKey<Enchantment>`.
- [ ] `Target`-Record.
- [ ] `TableEnchantTest`: genau 36 Einträge, IDs eindeutig, `maxTable` ∈ 1..5, Stichproben: `sharpness=4`, `impaling=5`, `thorns=2`, `quick_charge=2`, `breach=3`, `protection=4`, keine Treasure-IDs aus F6.
Akzeptanz: Tests grün.

### M3 – Settings mit Slidern (Option A)
Aufgaben:
- [ ] Gruppe „General“ mit:

  | Name | Typ | Default | Range |
  |---|---|---|---|
  | `delay` | Int | 4 | 1–40, Slider 1–20 |
  | `use-top` / `use-middle` / `use-bottom` | Bool | false / true / true | |
  | `max-rerolls` | Int | 0 (= unbegrenzt) | 0–10000 |
  | `target-count` | Int | 0 (= unbegrenzt) | 0–1000 |
  | `min-levels` | Int | 30 | 1–100 |
  | `junk-books` | Enum `KEEP`, `DROP` | `KEEP` | |
  | `notify` | Bool | true | |
- [ ] Pro Kategorie eine eingeklappte Gruppe (`createGroup(name, false)`), Titel deutsch („Rüstung“, „Nahkampf“, „Streitkolben“, „Werkzeuge“, „Bogen“, „Armbrust“, „Dreizack“, „Angel“).
- [ ] Pro Verzauberung: `BoolSetting` Name = ID in kebab-case, Default false; `IntSetting` Name `<id>-level`, Default = `maxTable`, `range(1,maxTable)`, `sliderRange(1,maxTable)`, `.visible(on::get)`.
- [ ] Beschreibung des Bool-Settings enthält `~Rerolls@30` (z. B. „Ziel: protection · ca. 15 Rerolls bei 30 Leveln“).
- [ ] Map `ResourceKey<Enchantment> → Target` aufbauen; Hilfsmethode `Map<String,Integer> activeTargets()` (ID → Mindestlevel).
Akzeptanz: Build grün. (Sichtprüfung im Spiel macht Tore.)

### M4 – Entscheidungslogik
Signatur:
```java
public static int pick(Offer[] offers, Map<String, Integer> minLevels,
                       boolean[] allowedSlots, int playerLevel, boolean creative)
```
Regeln (in dieser Reihenfolge):
1. Slots von **2 nach 0** prüfen (höchster Slot zuerst).
2. Überspringen, wenn: Slot nicht erlaubt, `cost <= 0`, `enchantId == null`, oder `!creative && playerLevel < cost`.
3. Treffer, wenn `minLevels` die ID enthält und `level >= minLevels.get(id)` → Slot zurückgeben.
4. Kein Treffer → `-1`.

Zusätzlich: `static boolean canReroll(Offer[] offers, int playerLevel, int lapis, boolean creative)` (Slot 0: cost > 0, Level reicht, Lapis ≥ 1).

Tests (mindestens):
- [ ] Treffer im Bottom-Slot; Treffer nur im Top-Slot, aber Top gesperrt → −1
- [ ] Level zu niedrig → nächst tieferer Treffer oder −1; Creative ignoriert Level
- [ ] Level genau gleich Mindestlevel → Treffer; darunter → kein Treffer
- [ ] Mehrere Treffer → höchster Slot
- [ ] `enchantId == null` / `cost == 0` werden ignoriert
- [ ] leere Zielmap → immer −1
- [ ] `canReroll`-Grenzfälle (kein Lapis, Level < cost[0])
Akzeptanz: Tests grün, Klasse importiert **nichts** aus `net.minecraft`.

### M5 – State-Machine im Modul
Zustände: `WAIT_MENU → TAKE_RESULT → ENSURE_LAPIS → INSERT_BOOK → WAIT_OFFERS → DECIDE → WAIT_RESULT → TAKE_RESULT …`

Aufgaben:
- [ ] `@EventHandler onTick(TickEvent.Post)`: Wenn `mc.player.containerMenu` kein `EnchantmentMenu` ist → State `WAIT_MENU`, nichts tun.
- [ ] Delay-Zähler: nur alle `delay` Ticks eine Aktion.
- [ ] `TAKE_RESULT`: Slot 0 belegt → `handleResult(stack)` (M6), dann `InvUtils.shiftClick().slotId(0)`. Slot 0 danach noch belegt (Inventar voll) → Stopp.
- [ ] `ENSURE_LAPIS`: Lapis im Tisch < 3 → Lapis aus Inventar shift-klicken; keiner vorhanden → Stopp.
- [ ] `INSERT_BOOK`: `Items.BOOK` finden → shift-klicken (Tisch nimmt 1 Stück); keins → Stopp.
- [ ] `WAIT_OFFERS`: warten, bis mindestens ein `costs[s] > 0`. **Timeout 40 Ticks** → zurück zu `TAKE_RESULT` (Retry), nach 3 Timeouts Stopp.
- [ ] `DECIDE`: `Offer[]` aus `costs/enchantClue/levelClue` bauen (ID via Holder-Map → `unwrapKey()` → `identifier()`; nur Namespace `minecraft` akzeptieren und den **Pfad** mit `TableEnchant.id` vergleichen, andere Namespaces → `enchantId = null`). `OfferEvaluator.pick(...)`; −1 → `canReroll` prüfen → Slot 0 (`rerolls++`, `max-rerolls` prüfen). Level < `min-levels` → Stopp. Klick: `mc.gameMode.handleInventoryButtonClick(menu.containerId, slot)`.
- [ ] `WAIT_RESULT`: warten, bis Slot 0 ein `ENCHANTED_BOOK` ist. Timeout 40 Ticks → `TAKE_RESULT`.
- [ ] `onActivate()`: Zähler zurücksetzen; wenn keine Ziele aktiv → Warnung + deaktivieren.
- [ ] Stopp = Chat-Meldung (deutsch, Grund + Statistik) + `toggle()`.
Akzeptanz: Build grün; Code-Review-Checkliste: kein Klick ohne vorherige Zustandsprüfung, jeder Wartezustand hat Timeout.

### M6 – Ergebnis, Statistik, Datapack-Check
Aufgaben:
- [ ] `handleResult`: `STORED_ENCHANTMENTS` lesen; Treffer, wenn **irgendeine** Verzauberung Ziel mit ausreichendem Level ist (auch bei Reroll-Büchern). Treffer → `hits++`, bei `notify` Chat „✔ Treffer: Sharpness IV (+ Unbreaking III)“ mit Übersetzungsnamen. Kein Treffer + `DROP` → Buch nach dem Rausnehmen droppen.
- [ ] Statistik: `rerolls`, `hits`, `booksUsed`, `levelsSpent` (Summe `slot+1`), `lapisSpent` (Summe `slot+1`). Bei Stopp ausgeben.
- [ ] `target-count` erreicht → Stopp.
- [ ] Beim Aktivieren: aktive Ziele gegen Server-Tag `IN_ENCHANTING_TABLE` prüfen; fehlende IDs → Warnung (Datapack/Server-Änderung).
Akzeptanz: Build grün, `handleResult` hat eigene Hilfsfunktion `matches(Map<String,Integer> enchants, Map<String,Integer> targets)` mit Unit-Test.

### M7 – Eigene Auswahl-UI (Option B) · erst nach Freigabe
- `GenericSetting<EnchantTargets>`; `EnchantTargets implements IGeneric<EnchantTargets>` (`copy`, `set`, `toTag`, `fromTag`, `createScreen`).
- `EnchantTargetsScreen extends WindowScreen`: Suchfeld, `theme.section` pro Kategorie, Zeile = Buch-Icon + Name + Checkbox + Slider (`theme.intEdit(v, 1, max, 1, max, false)`), Buttons „Alle max“, „Alle aus“, Presets „Rüstung“/„Werkzeug“/„Waffen“.
- Liste zur Laufzeit aus `IN_ENCHANTING_TABLE`; Tisch-Max dynamisch = höchstes Level mit `getMinCost(level) ≤ 36` (⚠️ API), Fallback auf `TableEnchant`.
- Migration: alte Option-A-Werte einmalig übernehmen, dann Option-A-Settings entfernen.

### M8 – Optional, nur nach ausdrücklicher Freigabe
- Bücherregal-Scan (gleicher Seed, andere Regalzahl → andere Angebote; Blöcke in Transmitter-Lücken setzen/entfernen).
- Seed-Cracking (eigene Implementierung, **kein** clientcommands-Code).
- Tisch automatisch öffnen (nächster Tisch in Reichweite).

---

## 7. Manuelle Tests (macht Tore im Spiel)

1. Singleplayer-Survival mit Cheats, 15 Regale, `/xp add @s 500 levels`, Stack Bücher + Lapis.
2. Nur `protection` ≥ IV aktiv → Treffer im Schnitt nach ~15 Rerolls? Statistik plausibel?
3. GUI mitten im Zyklus schließen → Modul hängt nicht, macht beim Öffnen weiter.
4. Kein Lapis / keine Bücher / Level < 30 / Inventar voll → sauberer Stopp mit Meldung.
5. `/tick rate 5` (Lag simulieren) → keine Doppelklicks, Timeouts greifen.
6. Creative-Modus → funktioniert ohne Level-Abzug.
7. Erst danach Multiplayer mit hohem Delay (Serverregeln beachten).

---

## 8. Stolperfallen

- Angebote kommen asynchron vom Server → **niemals** direkt nach dem Einlegen klicken (`WAIT_OFFERS`).
- `InvUtils.shiftClick().slot(...)` nimmt **Inventar-Index**, `.slotId(...)` nimmt **Menü-Slot-ID** – nicht verwechseln.
- `enchantClue` ist eine **Registry-Raw-ID** des Servers, keine feste Konstante → immer über die Holder-Map auflösen.
- Level-Voraussetzung (`costs`) ≠ abgezogene Level (`slot+1`).
- 26.3 erscheint voraussichtlich am 15.09.2026 → keine Anpassung ohne Auftrag.

---

## 9. Quellen (Kurzform)

- minecraft.wiki: *Enchanting table mechanics* (Formeln, Kostenspannen), *Enchantment tag (Java Edition)* (36 × `non_treasure`, Stand 03.09.2026), *Java Edition 26.2* (Release 16.06.2026), *Java Edition 26.3* (geplant 15.09.2026)
- GitHub `MeteorDevelopment/meteor-addon-template`, Commit `58540ef` „26.2 update“ (10.08.2026)
- GitHub `MeteorDevelopment/meteor-client`, Commit `3128d5d` (11.09.2026)
- GitHub `Earthcomputer/clientcommands`, Commit `ca168a9` „Update to 26.3-rc-1“ (11.09.2026, LGPL-3.0 – nur Referenz)
- Eigene Monte-Carlo-Simulation (Werte `~Rerolls@30`, ±0.1–0.3 Prozentpunkte)
