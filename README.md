# Droppy

Drop chance calculator for Old School RuneScape. Shows your probability of getting collection log drops based on your kill count, using real drop rates from the OSRS Wiki.

Built by **bckpockets(SIP YE)**

---

## What it does

- Side panel with your current monster's drop table and per-item % chance
- Pulls drop rates straight from the OSRS Wiki
- Syncs with your collection log so it knows what you have and what you're dry on
- Tracks per-item KC (not just overall KC -- knows how many kills since you last got each specific item)
- `!dry` chat command to flex your dry streaks (or lack thereof) to your friends
- Search tab to look up any monster's drops without fighting it

---

## Getting set up

You need three things installed before building:

### Java 11+

You need the JDK (development kit), not just the JRE.

- **Windows**: Download from https://adoptium.net/temurin/releases/ -- pick the `.msi` installer for Windows x64, JDK 11 or higher. Run the installer, make sure "Set JAVA_HOME" is checked.
- **Mac**: `brew install openjdk@11` or download from the same Adoptium link above. Pick the `.pkg` for macOS.

Check it's working:
```
java -version
```
Should say 11 or higher.

### Git

- **Windows**: https://git-scm.com/download/win -- run the installer, defaults are fine
- **Mac**: `brew install git` or just type `git` in terminal and macOS will prompt you to install dev tools

### Gradle

- **Windows**: Easiest way is `scoop install gradle` if you use scoop. Otherwise download from https://gradle.org/install/ and add it to your PATH.
- **Mac**: `brew install gradle`

Check it's working:
```
gradle --version
```

---

## Building and running

### Windows

```
git clone https://github.com/bckpockets/droppy.git
cd droppy
gradle wrapper
gradlew.bat build
```

Then run RuneLite with the plugin loaded:
```
java -jar "%USERPROFILE%\.runelite\launcher.jar" --developer-mode --sideload-external-plugin="build\libs\droppy-1.0.0.jar"
```

### Mac

```
git clone https://github.com/bckpockets/droppy.git
cd droppy
gradle wrapper
./gradlew build
```

Then run RuneLite with the plugin loaded:
```
java -jar ~/.runelite/launcher.jar --developer-mode --sideload-external-plugin=build/libs/droppy-1.0.0.jar
```

If the launcher path is wrong, poke around wherever you installed RuneLite. On Mac it might be under `/Applications/RuneLite.app/Contents/Resources/`.

---

## How to use it

### First things first -- sync your collection log

Before Droppy can track what you have and don't have, it needs to read your collection log.

1. Log in and open your collection log (the green book icon)
2. Click through the boss/monster pages you want to track -- Zulrah, Vorkath, whatever you're grinding
3. The status bar at the top of the Droppy panel will update as pages sync

You only have to do this once per monster. After that it saves to your RuneLite profile and picks up new drops automatically.

### Then go wild

**Current tab** -- just start fighting stuff. The panel auto-switches to show whatever you're killing with the full drop table, your KC, and your % chance for each item.

**Search tab** -- type any monster name and hit enter. Good for checking rates on stuff you're not currently fighting.

**!dry command** -- type `!dry zulrah` (or any monster) in chat. Everyone in the chat sees something like:

```
Zulrah — 1,583 kc — 5/7 obtained | Got: Tanzanite fang, Magic fang, Serpentine visage, Uncut onyx, Magma mutagen | Dry: Jar of swamp (1/3,000) 1,583 kc 41% | Pet snakeling (1/4,000) 800 kc 18%
```

### Reading the panel

Each item row shows:
- Icon and name (green check if you have it)
- Drop rate as the original wiki fraction (3/128, not 1/43)
- How many kills since you last got that item
- Progress bar and % chance

**Colors**: blue = normal, yellow = above your threshold, red = 90%+ (you're cooked), green = obtained

### Settings

RuneLite settings > Droppy:
- Show only unobtained items
- Highlight threshold (default 50%)
- KC milestones
- Auto-switch panel on combat
- Toggle drop rate display

---

## Troubleshooting

**"gradlew is not recognized"** -- you need to run `gradle wrapper` first to generate the wrapper scripts

**Build fails with Java errors** -- make sure `java -version` says 11+. If you have multiple Java versions, set JAVA_HOME to the right one.

**Plugin doesn't show up in RuneLite** -- double check the jar path exists. Run `dir build\libs` (Windows) or `ls build/libs` (Mac) to see what the jar is actually called.

**No KC showing** -- KC tracks automatically from loot drops (like loot logger). It also imports existing KC from the loot tracker and chat-commands plugins if you already have data there. If it's still at 0, do one kill and it'll start counting. Or use the Search tab to look up rates without needing KC.

**Items not marked as obtained** -- you need to open your collection log to that monster's page at least once so Droppy can read it.

**Widget IDs changed after a game update** -- Jagex sometimes changes internal widget IDs on Wednesday updates, which can break collection log reading. If the clog sync stops working after an update, check for a plugin update.
