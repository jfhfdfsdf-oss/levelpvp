# LevelPvP (Paper 26.2)

Java 25 / Gradle project. GitHub Actions builds the plugin JAR automatically.

## Build with GitHub
1. Create a new GitHub repository.
2. Upload the **contents** of this folder (including `.github/workflows/build.yml`).
3. Open the repository's **Actions** tab and wait for **Build LevelPvP** to finish.
4. Open the successful workflow run, download the `LevelPvP-plugin` artifact, and extract the JAR.
5. Put the JAR into the server's `plugins` folder and restart the server.

## Commands
- `/delivery` – send items to a player (including offline players) or claim deliveries.
- `/withdraw <amount>` – exchange levels for that many individual one-level bottles.
- `/stats` – open your stats GUI.
- `/levelcap <amount>` – set the global cap.
- `/levelcap <player> <amount|reset>` – set/reset a personal cap.
- `/setlevel <player> <level>` – set a player's level.
- `/revivestar` – open the revive GUI.
- `/revivestar <player>` – unban a player through LevelPvP.
- `/levelpvp reload` – reload plugin configuration.

Admin commands are operator-only (`levelpvp.admin`, default OP).

## Level rules
- Starts at 0; default range is -10 to 20.
- Player kills give the killer +1 level; every death removes 1 level.
- A player who dies while already at -10 is banned by LevelPvP.
- Health: level 0 = 10 hearts; positive levels add half a heart each; negative levels remove one heart each, with a minimum of half a heart at -10.
- Outgoing player damage changes by 5% per level; incoming damage reduction changes by 1% per level; XP changes by 25% per level.
- Level in a Bottle items each grant exactly +1 level. `/withdraw 3` creates three one-level bottles.
- A kill at the current level cap drops one Level in a Bottle.

## Recipes
- Revive Star: Nether Star in the middle, Netherite Scrap in the four corners, Diamond Blocks on the other four edge slots.
- Crafted Level: Golden Apple in the middle, Gold Blocks on the four edge slots, Diamonds in the corners.
- Level Extender: Dragon Egg in the middle, Diamond Blocks in all eight surrounding slots.

## Notes
Player data and offline deliveries are stored in `plugins/LevelPvP/data.yml`.

## Custom brewing
Put a regular Strength II or Speed II potion in the brewing stand and Redstone in the ingredient slot. The result is the same level-II effect with an 8-minute duration.
