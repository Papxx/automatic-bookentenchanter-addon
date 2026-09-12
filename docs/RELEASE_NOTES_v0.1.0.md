# Book Enchanter v0.1.0

First release. A Meteor Client addon that farms enchanted books at an enchanting table until a
target enchantment shows up at the level you asked for.

**Minecraft 1.26.2 · Fabric Loader 0.19.3+ · Meteor Client 26.2**

## What it does

Open a table, tick the enchantments you want, set a minimum level for each, and enable the module.
It inserts a book, tops the lapis up, reads the three offers, and either enchants the slot that
matches one of your targets or rerolls through the top slot and tries again. It stops with a chat
message and a summary when it runs out of books, lapis or levels, when your level floor is reached,
or when you hit your reroll or target limit.

## Highlights

- **All 36 table enchantments**, grouped by category, each with an on/off toggle and a minimum level
  slider. The slider caps at the highest level a table can actually produce for that enchantment,
  which for several of them is below the game maximum — Sharpness stops at IV, Thorns at II,
  Quick Charge at II.
- **Every enchantment on the finished book counts as a hit**, not just the one the offer advertised.
  A book can come out with more than it promised.
- **Bookshelf prediction.** For the current seed the module works out which bookshelf count would
  offer one of your targets, so you can rebuild the shelves instead of burning books and levels.
  This needs no seed cracking — the server sends the client its enchantment seed as a plain menu
  value, and the generation itself is public game API.
- **The prediction verifies itself.** Before showing you a number it simulates your current bookshelf
  count and compares it against the offers the server actually sent. If they disagree — a modified
  server, a datapack — it says prediction is unavailable for this run instead of giving you a wrong
  answer.
- **Datapack aware.** Your targets are checked against the server's `#minecraft:in_enchanting_table`
  tag on activation, and you get a warning for anything that server will never offer.
- **Optional auto-open** for the nearest table in reach, and **junk book dropping** so failed
  attempts do not fill your inventory.

## Things worth knowing

- **Rerolling costs a book.** The offers depend on your enchantment seed, and that seed only changes
  when you actually enchant something — taking the book in and out does nothing. So a reroll means
  enchanting the cheapest slot, which consumes a book, a level and a lapis and hands back a junk
  enchanted book. A hunt for Sharpness IV averages around 135 rerolls. That is the game, not a bug,
  and it is exactly what the `predict` setting is there to avoid.
- **The default level floor is 30.** With exactly 30 levels the module does one reroll and stops.
  Bring a buffer.
- **The default delay is 4 ticks** and stays there deliberately. This addon contains nothing aimed at
  defeating anti-cheat.

## Not included

Bookshelf manipulation (placing and removing blocks to reach a predicted count) is deliberately left
out. The module tells you which count to build; it does not build it for you.

## Known limitations

This release has had no extended in-game testing across different servers. Feedback on the
[issue tracker](https://github.com/Papxx/automatic-bookentenchanter-addon/issues) is welcome,
especially about the prediction on modded or datapacked servers.

## Install

1. [Fabric Loader](https://fabricmc.net/use/) 0.19.3 or newer for Minecraft 1.26.2.
2. [Meteor Client](https://meteorclient.com) 26.2 in your `mods` folder.
3. `book-enchanter-0.1.0.jar` in the same folder.

Then: Meteor GUI → **Book Enchanter** → **auto-book-enchant**. Tick at least one target enchantment,
or the module will refuse to start.
