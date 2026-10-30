# KitBot (Fabric, Minecraft 1.21.11)

A sparring partner that fights you with the Classes SMP kit. Server-side only: it works in singleplayer and
on a dedicated server, and other players need nothing installed.

    /kitbot         spawn a bot six blocks in front of you; it fights you (and only you)
    /kitbot clear   remove your bots (max 3 at a time)
    /kit            give yourself the kit (also /kitbot kit)

The bot is a zombie body wearing the kit, so it walks and swings like a vanilla mob. You will see a zombie
in diamond armor, not a player skin.

## Building the .jar

This is a complete Fabric project, but it has **never been compiled against Minecraft** (it was written
somewhere without access to the Minecraft or Fabric downloads). You need JDK 21 and an internet connection.

    ./gradlew build          (Windows: gradlew.bat build)

The jar is `build/libs/kitbot-1.0.0.jar` (not the `-sources` one). Put it in `mods/` next to Fabric API.

If the first build stops while downloading dependencies, the version numbers in `gradle.properties`
are my best guesses and need updating: open https://fabricmc.net/develop/ , pick 1.21.11, and copy the
loader, Loom and Fabric API versions it shows. If Gradle says it needs a newer Gradle, change the version
in `gradle/wrapper/gradle-wrapper.properties`.

Fallback: generate a fresh project at https://fabricmc.net/develop/template (Minecraft 1.21.11), copy
`src/main/java/dev` and `src/main/resources/fabric.mod.json` into it, delete the template's example mod
class and any `*.mixins.json`, and run `./gradlew build` there.

## If the compile fails

Every Minecraft call whose name or signature moved between 1.21.x releases is in
`src/main/java/dev/kitbot/mc/Compat.java`, and the rest of the code sticks to stable API. If the compiler
complains it should be a one-line fix there (likely suspects: the `MobEffects` names, `getMobArrow`,
`setDropChance`, `ItemStack.enchant`). Send me the first few errors and I'll fix them.

## /kit

`/kit` (or `/kitbot kit`) gives you the kit laid out slot for slot like the inventory screenshot in the Discord post:
diamond Protection III armor and a shield, netherite Sharpness IV sword and axe, diamond pickaxe, Power V bow,
64 arrows, 128 golden apples, 128 XP bottles, 64 cobwebs, 64 wind charges, 16 buckets and 2 water buckets,
a totem, and splash potions: 3 speed, 3 strength, 2 turtle master II, 3 weaving, 9 instant health II.
Whatever you were carrying is dropped at your feet, not deleted. The screenshot has one stack of 64 that is too
blurry to identify (inventory slot 26), so that slot is empty, and it shows no enchanted golden apples even
though the post lists 2.

## What it does in a fight

- Kit: diamond armor Protection III, netherite sword Sharpness IV, Power V bow, totem in the offhand,
  128 golden apples, 2 enchanted golden apples, 64 cobwebs, 2 strong turtle masters. Speed, strength and
  weaving are applied at the start.
- Melee is the zombie's normal pathfinding and attacks.
- Bow: from 12 to 40 blocks with a clear line of sight it draws for a full second and fires a 15-damage
  arrow aimed ahead of where you are running. It stops drawing if you close in.
- Cobwebs: drops one on your feet at close range, then takes it back after eight seconds. It breaks webs it
  gets stuck in.
- Healing: turtle master when low and you are close, then an enchanted gapple, then regular gapples. Once
  the totem has popped it heals about 3 HP earlier.
- If you drink turtle master (Slowness VI, Resistance IV) it backs off and shoots instead of trading blows.

## Not done

Lava, water and bucket draining are ignored, XP bottles are ignored, and there is no config file. The
numbers (healing thresholds, bow range) are constants at the top of `KitBrain.java`.

## Extras

- `.github/workflows/build.yml` builds the jar on GitHub if you ever push this to a repo.
- Aiming maths check: `javac -d out src/main/java/dev/kitbot/core/Ballistics.java tests/BallisticsCheck.java`
  then `java -cp out BallisticsCheck`.
