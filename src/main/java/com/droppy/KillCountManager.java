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

    private static final long CHAT_KC_DEDUP_WINDOW_MS = 2000;

    private final PlayerDataManager playerDataManager;
    private final ConfigManager configManager;

    private String lastKcMonster;

    private String chatKcMonster;
    private long chatKcTimestamp;

    public KillCountManager(PlayerDataManager playerDataManager, ConfigManager configManager)
    {
        this.playerDataManager = playerDataManager;
        this.configManager = configManager;
    }

    public int getKillCount(String monsterName)
    {
        int ourKc = playerDataManager.getKillCount(monsterName);
        if (ourKc > 0)
        {
            return ourKc;
        }

        int chatCmdKc = readFromChatCommands(monsterName);
        if (chatCmdKc > 0)
        {
            playerDataManager.setKillCount(monsterName, chatCmdKc);
            log.debug("Imported KC from chat-commands for {}: {}", monsterName, chatCmdKc);
            return chatCmdKc;
        }

        int lootTrackerKc = readFromLootTracker(monsterName);
        if (lootTrackerKc > 0)
        {
            playerDataManager.setKillCount(monsterName, lootTrackerKc);
            log.debug("Imported KC from loot tracker for {}: {}", monsterName, lootTrackerKc);
            return lootTrackerKc;
        }

        return 0;
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

            chatKcMonster = monsterName;
            chatKcTimestamp = System.currentTimeMillis();

            log.debug("KC from chat: {} = {}", monsterName, kc);
            return monsterName;
        }

        return null;
    }

    public void handleLootReceived(String sourceName)
    {
        lastKcMonster = sourceName;

        if (sourceName.equalsIgnoreCase(chatKcMonster)
            && System.currentTimeMillis() - chatKcTimestamp < CHAT_KC_DEDUP_WINDOW_MS)
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
