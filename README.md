# Dry Drop Tracker

Shows you how dry (or lucky) you are on collection log drops. Pulls rates from the OSRS Wiki, tracks your KC, reads your clog, does the math for you.

by **bckpockets(SIP YE)**

---

## what it does

- side panel shows supported collection log drops for what you're fighting, with a KC percentage where the rate is fixed
- reads your collection log so it knows what you have vs what you're still missing
- per-item KC tracking -- not just "kills since any drop" but "kills since you last got THIS specific item"
- shows wiki rates for supported bosses, raids, minigames, gauntlet, and clues; variable rewards are labeled as conditional
- `!dry` chat command so you can show your clan how cooked you are (or flex your spoon luck)
- search tab to look up any monster without needing to go fight it

---

## using the plugin

### step 1: sync your collection log

this is the important part. Dry Drop Tracker needs to read your clog to know what you have

1. log in to your account
2. open your collection log (green book icon)
3. click through the pages you care about -- zulrah, vorkath, cox, whatever you're grinding
4. watch the status bar at the top of the Dry Drop Tracker panel, it'll say "Synced X pages" as you go

you only gotta do this once per source. it saves to your runelite profile. after that, new drops get picked up automatically when you get them

### step 2: go kill stuff

just play normally. when you attack something or get loot the panel auto-switches to show that monster's drop table with:
- supported drops + their wiki rates (original fractions like 3/128)
- your % chance of having gotten an item by now when its per-kill rate is fixed
- green checkmarks on stuff you already have
- progress bars that fill up as you go more dry

The percentage is the chance of at least one drop across your tracked KC when a fixed per-kill rate is known. Dry Drop Tracker applies the wiki's roll count when an item gets multiple rolls per kill. Raid rewards, clue caskets, Wintertodt, and other conditional tables show their listed rate without a KC percentage because points, reward rolls, difficulty, or encounter choice can change the real chance. The regular and Corrupted Gauntlet tables have separate rates. A chest reported under the shared `Reward Chest (The Gauntlet)` name shows "Rate varies by variant" because that event does not identify which mode was completed. Vorkath's head and Desert Treasure II vestiges also omit a KC percentage: the 50th Vorkath head is guaranteed, and vestiges require three hidden progress rolls.

The bundled catalogue includes every item in the 124 collection-log sections on the OSRS Wiki at the time of the last data refresh. Items without a usable numeric rate remain visible as "Rate unavailable" and receive no dry percentage. Ordinary loot outside the collection log is not included. KC imported from RuneLite or other plugins may include attempts before this plugin observed your drops, so a displayed historical dry streak depends on that imported history being complete. Wiki updates, new content, and differences between the Wiki and the in-game log require another data refresh and verification.

KC tracks automatically from loot and game chat messages. Chat KC is enabled by default and can fill a collection-log source even when its log page has no count. The plugin maps common game names such as Barrows chest and Lunar Chest to their collection-log pages, and keeps regular and Corrupted Gauntlet counts separate. It also imports existing KC from the loot tracker or chat-commands plugins. A count cannot be recovered from older chat messages that the plugin never observed.

### step 3: flex on your clan

type `!dry` followed by a monster name in any chat:
```
!dry zulrah
```

everyone sees something like:
```
Zulrah — 1,583 kc (5/7 logged) | Got: Tanzanite fang 2 × 1/1,024 per kill at 230 kc 36%, Magic fang 2 × 1/1,024 per kill at 890 kc 82% | Pet snakeling 1/4,000 — 800 dry 18%
```

obtained items show what KC you got them at and the % you were at (so people can see if you spooned). dry items show how many kills without it and the expected %

The wiki rates are available for supported sources. KC percentages appear only where the plugin can model a fixed per-kill chance.

just `!dry` with no monster name uses whatever you killed last

### the search tab

don't feel like fighting something to see the rates? switch to the search tab and type any monster name. autocomplete kicks in after 2 characters

### colors

- **blue** = normal
- **yellow** = above your highlight threshold (configurable)
- **red** = 90%+ chance, you're properly dry
- **green** = you got it

### settings

runelite settings > Dry Drop Tracker:
- show only unobtained items (hides stuff you already have)
- highlight threshold % (default 50)
- track KC from game chat messages
- detect new collection log items from game chat notifications
- sharing API URL for `!dry` (leave blank to disable sharing)

---

## if something isn't working

**KC stuck at 0** -- KC picks up from loot events automatically. just do a kill. if you have existing KC from loot tracker or chat-commands plugins it'll import that too

**items showing as not obtained even though you have them** -- open your collection log to that monster's page. Dry Drop Tracker needs to read the clog widget at least once per source to know what you have

**clog sync stopped working after a wednesday update** -- jagex sometimes changes widget IDs. check if there's a plugin update
