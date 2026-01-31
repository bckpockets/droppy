package com.droppy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Tracks kill counts from multiple sources (in priority order):
 *
 * 1. Our own stored data (from collection log widget, chat messages, loot events)
 * 2. Chat-commands plugin config ("killcount" RSProfile group) -- passive
 * 3. Loot tracker plugin config ("loottracker" RSProfile group) -- "kills with loot" fallback
 * 4. NPC loot events -- increment-based fallback for untracked monsters
 *
 * After the initial collection log flip-through establishes baseline KC,
 * forward tracking happens via loot events and chat messages.
 */
@Slf4j
public class KillCountManager
{
    // "Your Zulrah kill count is: 150."
    private static final Pattern KC_PATTERN = Pattern.compile(
        "Your (.+?) kill count is: ([\\d,]+)",
        Pattern.CASE_INSENSITIVE
    );

    // "Your completion count for Chambers of Xeric is: 50."
    private static final Pattern KC_COMPLETION_PATTERN = Pattern.compile(
        "Your completion count for (.+?) is: ([\\d,]+)",
        Pattern.CASE_INSENSITIVE
    );

    // "Your Chambers of Xeric count is: 50."
    private static final Pattern KC_COUNT_PATTERN = Pattern.compile(
        "Your (.+?) count is: ([\\d,]+)",
        Pattern.CASE_INSENSITIVE
    );

    // "Your subdued Wintertodt count is: 100."
    private static final Pattern KC_SUBDUED_PATTERN = Pattern.compile(
        "Your subdued (.+?) count is: ([\\d,]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final String CHAT_COMMANDS_KC_GROUP = "killcount";
    private static final String LOOT_TRACKER_GROUP = "loottracker";

    private final PlayerDataManager playerDataManager;
    private final ConfigManager configManager;

    private String lastKcMonster;

    public KillCountManager(PlayerDataManager playerDataManager, ConfigManager configManager)
    {
        this.playerDataManager = playerDataManager;
        this.configManager = configManager;
    }

    /**
     * Gets the kill count for a monster, checking multiple sources:
     * 1. PlayerDataManager (our own stored data)
     * 2. Chat-commands plugin config (true KC from game messages)
     * 3. Loot tracker plugin config (kills-with-loot, approximate)
     *
     * If KC is found from an external source, it's imported into our data.
     */
    public int getKillCount(String monsterName)
    {
        // First check our own stored data
        int ourKc = playerDataManager.getKillCount(monsterName);
        if (ourKc > 0)
        {
            return ourKc;
        }

        // Fall back to chat-commands plugin (true KC)
        int chatCmdKc = readFromChatCommands(monsterName);
        if (chatCmdKc > 0)
        {
            playerDataManager.setKillCount(monsterName, chatCmdKc);
            log.debug("Imported KC from chat-commands for {}: {}", monsterName, chatCmdKc);
            return chatCmdKc;
        }

        // Fall back to loot tracker (kills-with-loot, not true KC but better than 0)
        int lootTrackerKc = readFromLootTracker(monsterName);
        if (lootTrackerKc > 0)
        {
            playerDataManager.setKillCount(monsterName, lootTrackerKc);
            log.debug("Imported KC from loot tracker for {}: {}", monsterName, lootTrackerKc);
            return lootTrackerKc;
        }

        return 0;
    }

    /**
     * Reads KC from the chat-commands plugin RSProfile config.
     */
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

    /**
     * Reads KC from the loot tracker plugin RSProfile config.
     * The loot tracker stores data as JSON under keys like "drops_NPC_Zulrah"
     * and "drops_EVENT_Chambers of Xeric". The "kills" field represents
     * kills where loot was received (not exact KC, but a useful approximation).
     */
    private int readFromLootTracker(String monsterName)
    {
        // Try NPC kills first, then event-based (raids, etc.)
        int kc = readLootTrackerEntry("drops_NPC_" + monsterName);
        if (kc > 0)
        {
            return kc;
        }

        return readLootTrackerEntry("drops_EVENT_" + monsterName);
    }

    /**
     * Reads a single loot tracker config entry and extracts the kills count.
     * The loot tracker stores JSON: {"type":"NPC","name":"Zulrah","kills":150,"drops":[...]}
     */
    private int readLootTrackerEntry(String key)
    {
        try
        {
            String json = configManager.getRSProfileConfiguration(LOOT_TRACKER_GROUP, key);
            if (json == null || json.isEmpty())
            {
                return 0;
            }

            JsonObject data = JsonParser.parseString(json).getAsJsonObject();
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

    /**
     * Attempts to parse a KC update from a chat message.
     * Returns the monster name if a KC was parsed, null otherwise.
     */
    public String handleChatMessage(String message)
    {
        message = message.replaceAll("<[^>]+>", "").trim();

        String monsterName = null;
        int kc = -1;

        Matcher matcher = KC_PATTERN.matcher(message);
        if (matcher.find())
        {
            monsterName = matcher.group(1).trim();
            kc = parseKc(matcher.group(2));
        }

        if (monsterName == null)
        {
            matcher = KC_COMPLETION_PATTERN.matcher(message);
            if (matcher.find())
            {
                monsterName = matcher.group(1).trim();
                kc = parseKc(matcher.group(2));
            }
        }

        if (monsterName == null)
        {
            matcher = KC_SUBDUED_PATTERN.matcher(message);
            if (matcher.find())
            {
                monsterName = matcher.group(1).trim();
                kc = parseKc(matcher.group(2));
            }
        }

        if (monsterName == null)
        {
            matcher = KC_COUNT_PATTERN.matcher(message);
            if (matcher.find())
            {
                monsterName = matcher.group(1).trim();
                kc = parseKc(matcher.group(2));
            }
        }

        if (monsterName != null && kc >= 0)
        {
            lastKcMonster = monsterName;
            playerDataManager.setKillCount(monsterName, kc);
            log.debug("KC from chat: {} = {}", monsterName, kc);
            return monsterName;
        }

        return null;
    }

    /**
     * Called when loot is received from any source (NPC kill, raid, event).
     * Increments KC by 1 for forward tracking.
     */
    public void handleLootReceived(String sourceName)
    {
        lastKcMonster = sourceName;
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
        this.lastKcMonster = name;
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
