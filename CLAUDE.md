# CLAUDE.md – BookEnchanter (Meteor-Client-Addon, Minecraft 26.2)

## Projekt
Meteor-Client-Addon mit dem Modul `auto-book-enchant`: verzaubert am Verzauberungstisch automatisch Bücher,
bis eine per Slider gewählte Ziel-Verzauberung (Mindestlevel) angeboten wird. Sonst Reroll über den obersten Slot.
**Vollständige Spezifikation und Meilensteine: `docs/PLAN.md` – vor jedem Meilenstein den passenden Abschnitt lesen.**

## Stack (NICHT ändern ohne Rückfrage)
- Minecraft **26.2**, Java **25**, Fabric Loader **0.19.3**, Fabric Loom **1.17-SNAPSHOT**, Gradle **9.6.1**
- Dependency: `meteordevelopment:meteor-client:26.2-SNAPSHOT` (Repos: maven.meteordev.org/releases + /snapshots)
- Basis: offizielles `meteor-addon-template` (Commit „26.2 update“, 10.08.2026)
- Versionen stehen in `gradle/libs.versions.toml`. Keine Versions-Bumps (auch nicht auf 26.3), außer ich sage es.

## Befehle
Windows (PowerShell):
```powershell
.\gradlew.bat build          # kompilieren + Tests; muss nach JEDER Änderung grün sein
.\gradlew.bat test           # nur Unit-Tests
.\gradlew.bat runClient      # Minecraft mit Addon starten (nur ich teste im Spiel)
```
Linux/macOS: `./gradlew build` usw.

## Konventionen
- Package: `de.tore.bookenchanter` · Mod-ID: `book-enchanter` · Modul-Name (kebab-case): `auto-book-enchant`
- Minecraft 26.x ist unobfuskiert → **Mojang-Namen** (`EnchantmentMenu`, `Identifier`, `BlockPos`, `DataComponents`).
  Keine Yarn-Namen aus alten Tutorials verwenden (`MinecraftClient`, `EnchantmentScreenHandler`, `ScreenHandler` …).
- Code, Bezeichner und Kommentare auf Englisch. **Auch alle Texte im Spiel auf Englisch**
  (Setting-Namen, Beschreibungen, Gruppentitel, Chat-Meldungen) - der Mod soll international nutzbar sein.
- Meteor-Patterns nachbauen, nicht erfinden. Referenzen im Meteor-Quellcode:
  `AutoBrewer`, `AutoSmelter` (Container-Automation), `StatusEffectAmplifierMapSettingScreen`,
  `BlockESP`/`ESPBlockData` (GenericSetting + eigene GUI), `InvUtils`, `SlotUtils`.
- Reine Entscheidungslogik (`logic/`) ohne Minecraft-Klassen halten → per JUnit testbar.

## API-Namen verifizieren, nicht raten
- Jeder Minecraft-/Meteor-Aufruf, der in `docs/PLAN.md` mit ⚠️ markiert ist, muss vor Nutzung per `javap`
  gegen die echten Jars geprüft werden. Ergebnis in `docs/API_NOTES.md` festhalten (Klasse, Signatur, ✅/❌).
- Meteor-Quellcode als Referenz darf außerhalb des Repos geklont werden:
  `git clone --depth 1 https://github.com/MeteorDevelopment/meteor-client ../meteor-client-ref`
  (niemals ins Projekt kopieren oder committen).

## Harte Regeln
- **Kein Code aus `Earthcomputer/clientcommands` kopieren** (LGPL-3.0). Nur als Ideen-Referenz.
- Keine Mixins, solange das MVP ohne auskommt (Tick-Event + `mc.player.containerMenu` reichen).
- Keine Features zum Umgehen von Anti-Cheat (Packet-Spoofing, Timer-Tricks o. Ä.). Default-Delay bleibt ≥ 4 Ticks.
- Optionale Phasen (Bücherregal-Scan, Seed-Cracking, Auto-Tisch-Öffnen) **nicht ohne meine Freigabe** starten.
- Nach jedem abgeschlossenen Meilenstein: Build grün → kurze Zusammenfassung → Git-Commit
  (`M<n>: <kurzbeschreibung>`) → auf mein OK warten, bevor der nächste Meilenstein beginnt.
- Im Zweifel fragen statt raten – besonders bei Spielmechanik-Werten (die stehen verbindlich in `docs/PLAN.md`).
