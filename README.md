# Minecraft Horror Plugin: PokerDevil

PokerDevil is a Minecraft horror plugin for Paper servers that adds a hostile Poker Devil encounter, automatic poker deck items, a five-card hold-or-draw game, and a timed escape chase.

## Install

1. Build the plugin with Java 21 and Maven:

   ```text
   mvn package
   ```

2. Copy the generated `target/plugin.jar` file into your Paper server's `plugins` folder.

3. Start or restart the server.

Requirements:

- Paper 1.21.11 or a compatible 1.21 server
- Java 21

## Use

1. Join the server. PokerDevil automatically gives each player a marked Poker Deck item if they do not already have one.
2. Explore the world at night or in dark underground areas. The Poker Devil periodically has a chance to spawn near eligible players.
3. If the Poker Devil attacks, the attack opens the Poker Devil: Hold or Fold game instead of dealing normal damage.
4. Click cards in the game inventory to mark them as held.
5. Click Draw Cards to replace every card that is not held.
6. Click Reveal Hand to compare your final poker hand with the Poker Devil's randomly generated hand.
7. If your hand is higher, the Poker Devil chases you for 10 seconds. Survive until the timer ends to escape.
8. If the Poker Devil reaches you during the escape or your hand does not beat its hand, you take damage and the encounter ends.

---

More Minecraft plugin projects by EnderDevelopment: https://github.com/EnderDevelopment

Project page: https://dash.enderdevelopment.com/community/55067882-a71f-49cf-8d13-68bd7155d9a6?utm_source=github&utm_medium=readme&utm_campaign=auto_publish&utm_content=55067882-a71f-49cf-8d13-68bd7155d9a6
