package com.droppy;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Manages persistence of player-specific data including kill counts,
 * collection log items obtained, and kill counts since last collection log drop.
 *
 * Data is stored per-player using RuneLite's ConfigManager.
 */
@Slf4j
public class PlayerDataManager
{
    private static final String CONFIG_GROUP = "droppy";
    private static final String KC_KEY_PREFIX = "kc_";
    private static final String KC_SINCE_DROP_PREFIX = "kcSinceDrop_";
    private static final String COLLECTION_LOG_KEY = "collectionLog_";

    private final ConfigManager configManager;
    private final Gson gson = new Gson();

    // In-memory caches
    private String currentPlayerName;
    private final Map<String, Integer> killCounts = new HashMap<>();
    private final Map<String, Integer> kcSinceLastDrop = new HashMap<>();
    private final Set<String> obtainedItems = new HashSet<>();

    public PlayerDataManager(ConfigManager configManager)
    {
        this.configManager = configManager;
    }

    /**
     * Loads data for a specific player.
     */
    public void loadPlayerData(String playerName)
    {
        if (playerName == null || playerName.isEmpty())
        {
            return;
        }

        this.currentPlayerName = playerName.toLowerCase().trim();
        killCounts.clear();
        kcSinceLastDrop.clear();
        obtainedItems.clear();

        // Load kill counts
        String kcJson = configManager.getConfiguration(CONFIG_GROUP, KC_KEY_PREFIX + currentPlayerName);
        if (kcJson != null && !kcJson.isEmpty())
        {
            try
            {
                Type type = new TypeToken<Map<String, Integer>>(){}.getType();
                Map<String, Integer> loaded = gson.fromJson(kcJson, type);
                if (loaded != null)
                {
                    killCounts.putAll(loaded);
                }
            }
            catch (Exception e)
            {
                log.warn("Failed to load kill counts for {}: {}", currentPlayerName, e.getMessage());
            }
        }

        // Load KC since last drop
        String kcSinceJson = configManager.getConfiguration(CONFIG_GROUP, KC_SINCE_DROP_PREFIX + currentPlayerName);
        if (kcSinceJson != null && !kcSinceJson.isEmpty())
        {
            try
            {
                Type type = new TypeToken<Map<String, Integer>>(){}.getType();
                Map<String, Integer> loaded = gson.fromJson(kcSinceJson, type);
                if (loaded != null)
                {
                    kcSinceLastDrop.putAll(loaded);
                }
            }
            catch (Exception e)
            {
                log.warn("Failed to load kc-since-drop for {}: {}", currentPlayerName, e.getMessage());
            }
        }

        // Load collection log items
        String clJson = configManager.getConfiguration(CONFIG_GROUP, COLLECTION_LOG_KEY + currentPlayerName);
        if (clJson != null && !clJson.isEmpty())
        {
            try
            {
                Type type = new TypeToken<Set<String>>(){}.getType();
                Set<String> loaded = gson.fromJson(clJson, type);
                if (loaded != null)
                {
                    obtainedItems.addAll(loaded);
                }
            }
            catch (Exception e)
            {
                log.warn("Failed to load collection log for {}: {}", currentPlayerName, e.getMessage());
            }
        }

        log.debug("Loaded data for {}: {} kc entries, {} obtained items",
            currentPlayerName, killCounts.size(), obtainedItems.size());
    }

    /**
     * Saves all data for the current player.
     */
    public void savePlayerData()
    {
        if (currentPlayerName == null)
        {
            return;
        }

        try
        {
            configManager.setConfiguration(CONFIG_GROUP,
                KC_KEY_PREFIX + currentPlayerName, gson.toJson(killCounts));
            configManager.setConfiguration(CONFIG_GROUP,
                KC_SINCE_DROP_PREFIX + currentPlayerName, gson.toJson(kcSinceLastDrop));
            configManager.setConfiguration(CONFIG_GROUP,
                COLLECTION_LOG_KEY + currentPlayerName, gson.toJson(obtainedItems));
        }
        catch (Exception e)
        {
            log.error("Failed to save player data for {}: {}", currentPlayerName, e.getMessage());
        }
    }

    /**
     * Updates the total kill count for a monster.
     * Also increments the KC-since-last-drop counter for that monster.
     */
    public void setKillCount(String monsterName, int kc)
    {
        String key = monsterName.toLowerCase().trim();
        int previousKc = killCounts.getOrDefault(key, 0);
        killCounts.put(key, kc);

        // Calculate how many new kills were added
        int delta = kc - previousKc;
        if (delta > 0)
        {
            int currentSince = kcSinceLastDrop.getOrDefault(key, 0);
            kcSinceLastDrop.put(key, currentSince + delta);
        }

        savePlayerData();
    }

    /**
     * Gets the total kill count for a monster.
     */
    public int getKillCount(String monsterName)
    {
        return killCounts.getOrDefault(monsterName.toLowerCase().trim(), 0);
    }

    /**
     * Gets the KC since the last collection log drop for a monster.
     * This is the value used for probability calculations.
     */
    public int getKcSinceLastDrop(String monsterName)
    {
        return kcSinceLastDrop.getOrDefault(monsterName.toLowerCase().trim(), 0);
    }

    /**
     * Gets the KC since last drop for a specific item from a specific monster.
     * Uses the monster-level counter as a simpler approach.
     */
    public int getKcSinceLastDrop(String monsterName, String itemName)
    {
        // We track at monster level. For per-item tracking, use itemKey
        String itemKey = (monsterName + "_" + itemName).toLowerCase().trim();
        Integer perItem = kcSinceLastDrop.get(itemKey);
        if (perItem != null)
        {
            return perItem;
        }
        // Fall back to monster-level KC
        return kcSinceLastDrop.getOrDefault(monsterName.toLowerCase().trim(), 0);
    }

    /**
     * Records that a collection log item was obtained.
     * Resets the KC-since-last-drop counter for that monster's specific item.
     */
    public void recordCollectionLogItem(String itemName, String monsterName)
    {
        obtainedItems.add(itemName.toLowerCase().trim());

        if (monsterName != null && !monsterName.isEmpty())
        {
            // Reset per-item KC counter
            String itemKey = (monsterName + "_" + itemName).toLowerCase().trim();
            kcSinceLastDrop.put(itemKey, 0);
        }

        savePlayerData();
        log.debug("Recorded collection log item: {} from {}", itemName, monsterName);
    }

    /**
     * Checks if a collection log item has been obtained.
     */
    public boolean hasItem(String itemName)
    {
        return obtainedItems.contains(itemName.toLowerCase().trim());
    }

    /**
     * Gets the set of all obtained item names.
     */
    public Set<String> getObtainedItems()
    {
        return new HashSet<>(obtainedItems);
    }

    /**
     * Gets all tracked monster names.
     */
    public Set<String> getTrackedMonsters()
    {
        return new HashSet<>(killCounts.keySet());
    }

    /**
     * Increments the KC for a monster by 1. Used when detecting kills via loot events.
     */
    public void incrementKillCount(String monsterName)
    {
        String key = monsterName.toLowerCase().trim();
        int current = killCounts.getOrDefault(key, 0);
        killCounts.put(key, current + 1);

        int currentSince = kcSinceLastDrop.getOrDefault(key, 0);
        kcSinceLastDrop.put(key, currentSince + 1);

        savePlayerData();
    }

    public String getCurrentPlayerName()
    {
        return currentPlayerName;
    }
}
