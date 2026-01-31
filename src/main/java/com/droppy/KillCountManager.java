package com.droppy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Tracks kill counts from multiple sources:
 *
 * 1. Collection log widget header (primary, authoritative) -- read by CollectionLogManager
 * 2. Chat messages ("Your X kill count is: Y") -- secondary/real-time
 * 3. Chat-commands plugin stored data ("killcount" config group) -- passive bootstrap
 * 4. NPC loot events -- increment-based fallback for monsters without KC messages
 *
 * All KC data is persisted through PlayerDataManager.
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

    /**
     * The chat-commands plugin stores KC under this config group.
     * Key format: bossname (lowercase), value: int KC.
     * This is read passively to bootstrap KC data without requiring
     * the player to receive chat messages first.
     */
    private static final String CHAT_COMMANDS_KC_GROUP = "killcount";

    private final PlayerDataManager playerDataManager;
    private final ConfigManager configManager;

    // Track the last monster name extracted from a KC message
    private String lastKcMonster;

    public KillCountManager(PlayerDataManager playerDataManager, ConfigManager configManager)
    {
        this.playerDataManager = playerDataManager;
        this.configManager = configManager;
    }

    /**
     * Gets the kill count for a monster, checking multiple sources in priority order:
     * 1. PlayerDataManager (our own stored data from widget scrape, chat, loot)
     * 2. Chat-commands plugin config ("killcount" RSProfile group)
     *
     * If KC is found from chat-commands but not in our data, it's imported
     * into PlayerDataManager for future use.
     */
    public int getKillCount(String monsterName)
    {
        // First check our own stored data
        int ourKc = playerDataManager.getKillCount(monsterName);
        if (ourKc > 0)
        {
            return ourKc;
        }

        // Fall back to chat-commands plugin stored KC
        int chatCmdKc = readFromChatCommands(monsterName);
        if (chatCmdKc > 0)
        {
            // Import into our data so we have it cached and can track deltas
            playerDataManager.setKillCount(monsterName, chatCmdKc);
            log.debug("Imported KC from chat-commands for {}: {}", monsterName, chatCmdKc);
            return chatCmdKc;
        }

        return 0;
    }

    /**
     * Reads KC from the RuneLite chat-commands plugin RSProfile config.
     * The chat-commands plugin stores KC whenever a player receives a
     * "Your X kill count is: Y" message or uses the !kc command.
     * Key is the boss name in lowercase.
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
     * Attempts to parse a KC update from a chat message.
     * Returns the monster name if a KC was parsed, null otherwise.
     */
    public String handleChatMessage(String message)
    {
        // Strip HTML tags
        message = message.replaceAll("<[^>]+>", "").trim();

        String monsterName = null;
        int kc = -1;

        // Try each pattern in order of specificity
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
     * Called when loot is received from an NPC.
     * Only increments KC if we don't already have a chat-based KC
     * for this monster (to avoid double-counting).
     */
    public void handleLootReceived(String npcName)
    {
        lastKcMonster = npcName;

        // If we have no KC data at all for this monster, start counting from loot
        int existingKc = getKillCount(npcName);
        if (existingKc == 0)
        {
            playerDataManager.incrementKillCount(npcName);
            log.debug("KC from loot (new monster): {} = 1", npcName);
        }
    }

    /**
     * Gets the last monster name that had a KC update.
     */
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
