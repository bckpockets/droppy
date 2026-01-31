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

## getting the prereqs

you need java, git, and gradle. if you already have all three skip to the next section

### java 11 or higher (JDK not JRE)

**windows:**
1. go to https://adoptium.net/temurin/releases/
2. pick Windows, x64, JDK, version 11 or higher
3. download the `.msi` installer
4. run it -- when it asks, check the box that says "Set JAVA_HOME variable"
5. close and reopen your terminal after installing

**mac:**
```
brew install openjdk@11
```
or grab the `.pkg` from the same adoptium link

**check it worked:**
```
java -version
```
should say `openjdk version "11.x.x"` or higher. if it still shows an old version or says not found, you probably need to close/reopen your terminal or fix your PATH

### git

**windows:**
1. go to https://git-scm.com/download/win
2. download and run the installer
3. just click through with the defaults, they're all fine

**mac:**
```
brew install git
```
or just type `git` in terminal -- macOS will ask if you want to install developer tools, say yes

### gradle

**windows:**

easiest way if you have scoop:
```
scoop install gradle
```

otherwise:
1. go to https://gradle.org/releases/
2. download the latest binary-only zip
3. unzip it somewhere like `C:\gradle`
4. add `C:\gradle\bin` to your system PATH (search "environment variables" in start menu, edit PATH, add it)
5. close/reopen terminal

**mac:**
```
brew install gradle
```

**check it worked:**
```
gradle --version
```

---

## building the plugin

### windows

open command prompt or powershell, then:
```
git clone https://github.com/bckpockets/droppy.git
cd droppy
gradle wrapper
gradlew.bat build
```

### mac

open terminal, then:
```
git clone https://github.com/bckpockets/droppy.git
cd droppy
gradle wrapper
./gradlew build
```

if everything worked you should see `BUILD SUCCESSFUL` and your jar will be at `build/libs/droppy-1.0.0.jar`

---

## loading it into runelite

you need runelite installed normally from https://runelite.net first

### windows
```
java -jar "%USERPROFILE%\.runelite\launcher.jar" --developer-mode --sideload-external-plugin="build\libs\droppy-1.0.0.jar"
```

### mac
```
java -jar ~/.runelite/launcher.jar --developer-mode --sideload-external-plugin=build/libs/droppy-1.0.0.jar
```

if it can't find the launcher jar, look in wherever you installed runelite. on mac check `/Applications/RuneLite.app/Contents/Resources/`

runelite will open with the plugin loaded. you should see **Droppy** in the side panel (right edge, little % icon)

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

**build says "gradlew is not recognized"** -- you forgot to run `gradle wrapper` first. that generates the wrapper scripts

**build fails** -- run `java -version`. needs to be 11+. if you just installed java, close and reopen your terminal

**plugin not in the side panel** -- make sure the jar path in the sideload flag actually points to a file. run `dir build\libs` (windows) or `ls build/libs` (mac) to check

**KC stuck at 0** -- KC picks up from loot events automatically. just do a kill. if you have existing KC from loot tracker or chat-commands plugins it'll import that too

**items showing as not obtained even though you have them** -- open your collection log to that monster's page. droppy needs to read the clog widget at least once per source to know what you have

**clog sync stopped working after a wednesday update** -- jagex sometimes changes widget IDs. check if there's a plugin update
