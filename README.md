# Book Enchanter

A [Meteor Client](https://meteorclient.com) addon that farms enchanted books for you.

Open an enchanting table, pick the enchantments you want and the minimum level you will accept, and
the module does the rest: insert a book, top up the lapis, read the three offers, and either take
the one that matches your target or reroll through the top slot and try again. It stops cleanly when
it runs out of books, lapis or levels.

**Minecraft 1.26.2 · Fabric · requires Meteor Client**

---

## Features

- **One toggle and one slider per enchantment.** All 36 enchantments obtainable from a table,
  grouped by category. Each has an on/off switch and a minimum level slider, capped at the highest
  level a table can actually produce for that enchantment - which is often lower than the game
  maximum (Sharpness caps at IV, Thorns at II).
- **Catches every hit, not just the advertised one.** A book can end up with more enchantments than
  the offer promised, so the result is checked against all your targets.
- **Bookshelf prediction.** The module works out which bookshelf count would offer one of your
  targets for the current seed, so you can rebuild instead of burning books. No seed cracking
  involved - the server hands the client its enchantment seed, and the prediction is verified
  against the offers you actually received before it is shown.
- **Reroll and target limits.** Stop after N rerolls or N hits, and keep a level floor so you never
  drain your XP completely.
- **Junk handling.** Books that miss every target can be dropped instead of filling your inventory.
- **Datapack aware.** On activation your targets are checked against the server's
  `#minecraft:in_enchanting_table` tag, and you get a warning for anything that server will never
  offer.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.3 or newer for Minecraft 1.26.2.
2. Put [Meteor Client](https://meteorclient.com) (26.2) in your `mods` folder.
3. Drop `book-enchanter-<version>.jar` in the same `mods` folder.

## Usage

1. Open the Meteor GUI and find **auto-book-enchant** in the **Book Enchanter** category.
2. Expand a category (Armor, Melee, Tools, ...) and tick at least one enchantment. Without a target
   the module refuses to start and tells you so.
3. Adjust the minimum level slider for that enchantment if you will accept less than the maximum.
4. Stand at an enchanting table with books and lapis in your inventory, open the table, and enable
   the module.

### Settings

| Setting | Default | What it does |
|---|---|---|
| `delay` | 4 | Ticks between two actions. Lower is faster but more obvious. |
| `use-top` / `use-middle` / `use-bottom` | off / on / on | Which offer slots may count as a hit. The top slot is off by default because it is used for rerolling. |
| `max-rerolls` | 0 | Stop after this many rerolls. 0 means unlimited. |
| `target-count` | 0 | Stop after this many hits. 0 means unlimited. |
| `min-levels` | 30 | Stop once you have fewer experience levels than this. |
| `junk-books` | KEEP | `DROP` throws away books that hit no target. |
| `predict` | on | Print which bookshelf count would offer a target for the current seed. |
| `auto-open` | off | Open the nearest enchanting table in reach by yourself. |
| `notify` | on | Print a chat message on every hit. |

### How rerolling works

The offers depend on your enchantment seed, and that seed only changes when you actually enchant
something. Taking the book in and out does nothing. So a "reroll" means enchanting the cheapest slot
to burn the current seed - which costs one level, one lapis and one book, and produces a junk
enchanted book. That is expected: a run that is looking for Sharpness IV will produce a lot of
level-1 books on the way there.

The `predict` setting exists to avoid that cost. If it reports that 12 bookshelves would offer your
target right now, rebuilding the shelves is free compared to rerolling.

## Building

```bash
./gradlew build          # compile and run the unit tests
./gradlew runClient      # launch Minecraft with the addon
```

The jar ends up in `build/libs/`.

The decision logic in `de.tore.bookenchanter.logic` deliberately contains no Minecraft types, so it
is covered by plain JUnit tests - including a test that asserts the compiled classes reference
nothing from `net.minecraft`.

## Credits

Built on the official
[meteor-addon-template](https://github.com/MeteorDevelopment/meteor-addon-template).

[Earthcomputer/clientcommands](https://github.com/Earthcomputer/clientcommands) was looked at as a
reference for what is possible with enchantment seeds. No code from it is used - it is LGPL-3.0 and
this project deliberately keeps its distance.

## License

See [LICENSE](LICENSE).
