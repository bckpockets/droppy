# Droppy

Shows you how dry (or lucky) you are on collection log drops. Pulls rates from the OSRS Wiki, tracks your KC, reads your clog, does the math for you.

by **bckpockets(SIP YE)**

---

## what it does

- side panel shows every drop for whatever you're fighting + your % chance of having gotten each one by now
- reads your collection log so it knows what you have vs what you're still missing
- per-item KC tracking -- not just "kills since any drop" but "kills since you last got THIS specific item"
- works for bosses, raids, minigames, gauntlet, clues, basically anything in the clog
- `!dry` chat command so you can show your clan how cooked you are (or flex your spoon luck)
- search tab to look up any monster without needing to go fight it

---

## using the plugin

### step 1: sync your collection log

this is the important part. droppy needs to read your clog to know what you have

1. log in to your account
2. open your collection log (green book icon)
3. click through the pages you care about -- zulrah, vorkath, cox, whatever you're grinding
4. watch the status bar at the top of the droppy panel, it'll say "Synced X pages" as you go

you only gotta do this once per source. it saves to your runelite profile. after that, new drops get picked up automatically when you get them

### step 2: go kill stuff

just play normally. when you attack something or get loot the panel auto-switches to show that monster's drop table with:
- every drop + its wiki rate (original fractions like 3/128, not collapsed to 1/43)
- your % chance of having gotten each item by now based on your KC
- green checkmarks on stuff you already have
- progress bars that fill up as you go more dry

KC tracks automatically from loot drops, same way loot logger works. it also pulls in any existing KC you have from the loot tracker or chat-commands plugins so you don't start from zero

### step 3: flex on your clan

type `!dry` followed by a monster name in any chat:
```
!dry zulrah
```

everyone sees something like:
```
Zulrah — 1,583 kc (5/7 logged) | Got: Tanzanite fang 1/512 at 230 kc 36%, Magic fang 1/512 at 890 kc 82% | Pet snakeling 1/4,000 — 800 dry 18%
```

obtained items show what KC you got them at and the % you were at (so people can see if you spooned). dry items show how many kills without it and the expected %

works for bosses, raids, minigames, gauntlet, clues -- anything with a drop table on the wiki

just `!dry` with no monster name uses whatever you killed last

### the search tab

don't feel like fighting something to see the rates? switch to the search tab and type any monster name. autocomplete kicks in after 2 characters

### colors

- **blue** = normal
- **yellow** = above your highlight threshold (configurable)
- **red** = 90%+ chance, you're properly dry
- **green** = you got it

### settings

runelite settings > Droppy:
- show only unobtained items (hides stuff you already have)
- highlight threshold % (default 50)
- KC milestones
- auto-switch panel when you start fighting
- show/hide drop rates

---

## if something isn't working

**KC stuck at 0** -- KC picks up from loot events automatically. just do a kill. if you have existing KC from loot tracker or chat-commands plugins it'll import that too

**items showing as not obtained even though you have them** -- open your collection log to that monster's page. droppy needs to read the clog widget at least once per source to know what you have

**clog sync stopped working after a wednesday update** -- jagex sometimes changes widget IDs. check if there's a plugin update
