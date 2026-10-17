package com.droppy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Slf4j
public class KillCountManager
{
    // Mirrors the game's count-message forms handled by RuneLite Chat Commands,
    // after game colour tags have been removed.
    private static final Pattern GAME_COUNT_PATTERN = Pattern.compile(
        "^Your (?<pre>completion count for |subdued |completed )?"
            + "(?<source>.+?) (?<post>(?:(?:kill|harvest|lap|completion|success|Total Ticket) )?"
            + "(?:count )?)is: ?(?<kc>[\\d,]+)\\.?$",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern KC_FOR_PATTERN = Pattern.compile(
        "^Your kill count for (.+?) is: ?([\\d,]+)\\.?$",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern RIFTS_CLOSED_PATTERN = Pattern.compile(
        "^Amount of Rifts you have closed: ?([\\d,]+)\\.?$",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern HUNTER_RUMOURS_PATTERN = Pattern.compile(
        "^You have completed ([\\d,]+) rumours? for the Hunter Guild\\.?$",
        Pattern.CASE_INSENSITIVE
    );

    private static final String CHAT_COMMANDS_KC_GROUP = "killcount";
    private static final String LOOT_TRACKER_GROUP = "loottracker";

    private static final long CHAT_KC_DEDUP_WINDOW_MS = 2000;
    private static final long CHEST_KC_DEDUP_WINDOW_MS = 10 * 60 * 1000;

    private final PlayerDataManager playerDataManager;
    private final ConfigManager configManager;
    private final WikiDropFetcher wikiDropFetcher;

    private String lastKcMonster;

    private String chatKcMonster;
    private long chatKcTimestamp;

    public KillCountManager(PlayerDataManager playerDataManager, ConfigManager configManager)
    {
        this(playerDataManager, configManager, null);
    }

    public KillCountManager(PlayerDataManager playerDataManager, ConfigManager configManager,
                            WikiDropFetcher wikiDropFetcher)
    {
        this.playerDataManager = playerDataManager;
        this.configManager = configManager;
        this.wikiDropFetcher = wikiDropFetcher;
    }

    public int getKillCount(String monsterName)
    {
        String canonical = canonicalSource(monsterName);
        if (canonical == null || canonical.isEmpty())
        {
            return 0;
        }
        int ourKc = playerDataManager.getKillCount(canonical);
        if (configManager == null)
        {
            return ourKc;
        }

        int chatCmdKc = Math.max(readFromChatCommands(monsterName),
            canonical.equalsIgnoreCase(monsterName) ? 0 : readFromChatCommands(canonical));
        int lootTrackerKc = Math.max(readFromLootTracker(monsterName),
            canonical.equalsIgnoreCase(monsterName) ? 0 : readFromLootTracker(canonical));
        int bestKc = Math.max(ourKc, Math.max(chatCmdKc, lootTrackerKc));
        if (bestKc > ourKc)
        {
            playerDataManager.setKillCount(canonical, bestKc);
            log.debug("Imported higher KC for {}: {}", monsterName, bestKc);
        }
        return bestKc;
    }

    private int readFromChatCommands(String monsterName)
    {
        if (monsterName == null || monsterName.isEmpty())
        {
            return 0;
        }

        try
        {
            String key = monsterName.toLowerCase().trim();
            Integer kc = configManager.getRSProfileConfiguration(
                CHAT_COMMANDS_KC_GROUP, key, int.class);
            if (kc != null && kc > 0)
            {
                return kc;
            }
        }
        catch (Exception e)
        {
            log.debug("Could not read chat-commands KC for {}: {}", monsterName, e.getMessage());
        }

        return 0;
    }

    private int readFromLootTracker(String monsterName)
    {
        int kc = readLootTrackerEntry("drops_NPC_" + monsterName);
        if (kc > 0)
        {
            return kc;
        }

        return readLootTrackerEntry("drops_EVENT_" + monsterName);
    }

    private int readLootTrackerEntry(String key)
    {
        try
        {
            String json = configManager.getRSProfileConfiguration(LOOT_TRACKER_GROUP, key);
            if (json == null || json.isEmpty())
            {
                return 0;
            }

            @SuppressWarnings("deprecation")
            JsonObject data = new JsonParser().parse(json).getAsJsonObject();
            if (data.has("kills"))
            {
                int kills = data.get("kills").getAsInt();
                if (kills > 0)
                {
                    return kills;
                }
            }
        }
        catch (Exception e)
        {
            log.debug("Could not read loot tracker entry {}: {}", key, e.getMessage());
        }

        return 0;
    }

    public String handleChatMessage(String message)
    {
        message = message.replaceAll("<[^>]+>", "")
            .replaceAll("@[a-zA-Z0-9_]+@", "").trim();

        String monsterName = null;
        int kc = -1;

        Matcher matcher = GAME_COUNT_PATTERN.matcher(message);
        if (matcher.matches()
            && (matcher.group("pre") != null || !matcher.group("post").isBlank()))
        {
            monsterName = matcher.group("source").trim();
            kc = parseKc(matcher.group("kc"));
        }

        if (monsterName == null)
        {
            matcher = KC_FOR_PATTERN.matcher(message);
            if (matcher.matches())
            {
                monsterName = matcher.group(1).trim();
                kc = parseKc(matcher.group(2));
            }
        }

        if (monsterName == null)
        {
            matcher = RIFTS_CLOSED_PATTERN.matcher(message);
            if (matcher.matches())
            {
                monsterName = "Guardians of the Rift";
                kc = parseKc(matcher.group(1));
            }
        }

        if (monsterName == null)
        {
            matcher = HUNTER_RUMOURS_PATTERN.matcher(message);
            if (matcher.matches())
            {
                monsterName = "Hunter Guild";
                kc = parseKc(matcher.group(1));
            }
        }

        if (monsterName != null && kc >= 0)
        {
            monsterName = canonicalSource(monsterName);
            lastKcMonster = monsterName;
            playerDataManager.setKillCount(monsterName, kc);

            chatKcMonster = monsterName;
            chatKcTimestamp = System.currentTimeMillis();

            log.debug("KC from chat: {} = {}", monsterName, kc);
            return monsterName;
        }

        return null;
    }

    public void handleLootReceived(String sourceName)
    {
        sourceName = canonicalSource(sourceName);
        if (sourceName == null || sourceName.isEmpty())
        {
            return;
        }
        lastKcMonster = sourceName;

        if (sourceName.equalsIgnoreCase(chatKcMonster)
            && System.currentTimeMillis() - chatKcTimestamp
                < (sourceName.equals("Moons of Peril")
                    || sourceName.equals("Barrows Chests")
                    || sourceName.equals("The Gauntlet")
                    || sourceName.equals("The Corrupted Gauntlet")
                    ? CHEST_KC_DEDUP_WINDOW_MS : CHAT_KC_DEDUP_WINDOW_MS))
        {
            chatKcMonster = null;
            log.debug("KC increment skipped for {} (chat already set)", sourceName);
            return;
        }

        playerDataManager.incrementKillCount(sourceName);
        log.debug("KC incremented from loot: {} = {}", sourceName,
            playerDataManager.getKillCount(sourceName));
    }

    public String getLastKcMonster()
    {
        return lastKcMonster;
    }

    public void setLastKcMonster(String name)
    {
        this.lastKcMonster = canonicalSource(name);
    }

    public String canonicalSource(String source)
    {
        if (source == null)
        {
            return null;
        }
        String normalized = source.trim().toLowerCase(java.util.Locale.ROOT);
        switch (normalized)
        {
            case "barrows":
            case "barrows chest":
            case "barrows chests":
                return "Barrows Chests";
            case "lunar chest":
            case "lunar chests":
            case "moons of peril":
                return "Moons of Peril";
            case "gauntlet":
            case "the gauntlet":
                return "The Gauntlet";
            case "corrupted gauntlet":
            case "the corrupted gauntlet":
                return "The Corrupted Gauntlet";
            case "the leviathan":
                return "Leviathan";
            case "the whisperer":
                return "Whisperer";
            case "the nightmare":
                return "Nightmare";
            case "the mimic":
                return "Mimic";
            default:
                String trimmed = source.trim();
                if (wikiDropFetcher != null)
                {
                    MonsterDropData data = wikiDropFetcher.getDropData(trimmed);
                    if (data != null && equivalentSourceName(trimmed, data.getMonsterName()))
                    {
                        return data.getMonsterName();
                    }
                }
                return trimmed;
        }
    }

    private boolean equivalentSourceName(String observed, String catalogue)
    {
        String left = observed.toLowerCase(java.util.Locale.ROOT);
        String right = catalogue.toLowerCase(java.util.Locale.ROOT);
        return left.equals(right)
            || left.equals("the " + right)
            || right.equals("the " + left)
            || left.equals(right + "s")
            || right.equals(left + "s")
            || left.equals(right + "es")
            || right.equals(left + "es");
    }

    private int parseKc(String kcStr)
    {
        try
        {
            return Integer.parseInt(kcStr.replace(",", ""));
        }
        catch (NumberFormatException e)
        {
            return -1;
        }
    }
}
