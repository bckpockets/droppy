package com.droppy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Tracks kill counts from multiple sources:
 *
 * 1. Collection log widget header (primary, authoritative) -- read by CollectionLogManager
 * 2. Chat messages ("Your X kill count is: Y") -- secondary/real-time fallback
 * 3. NPC loot events -- increment-based fallback for monsters without KC messages
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

    private final PlayerDataManager playerDataManager;

    // Track the last monster name extracted from a KC message for linking to collection log drops
    private String lastKcMonster;

    public KillCountManager(PlayerDataManager playerDataManager)
    {
        this.playerDataManager = playerDataManager;
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
        // Otherwise, the chat message or collection log will provide the authoritative count
        int existingKc = playerDataManager.getKillCount(npcName);
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
