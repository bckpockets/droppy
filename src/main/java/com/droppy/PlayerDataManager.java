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
 * collection log items obtained, and kill counts since last drop.
 *
 * Uses RSProfile configuration so data is tied to the RuneScape account,
 * not just the RuneLite profile.
 *
 * Data sources (in priority order):
 * 1. Collection log widget scrape (authoritative for obtained items + KC)
 * 2. Chat messages (real-time KC updates)
 * 3. Loot events (fallback KC for monsters without chat KC)
 */
@Slf4j
public class PlayerDataManager
{
    private static final String CONFIG_GROUP = "droppy";
    private static final String KC_KEY = "killCounts";
    private static final String KC_SINCE_DROP_KEY = "kcSinceDrop";
    private static final String OBTAINED_KEY = "obtainedItems";
    private static final String CLOG_SYNCED_KEY = "clogSyncedPages";

    private final ConfigManager configManager;
    private final Gson gson = new Gson();

    // In-memory caches
    private final Map<String, Integer> killCounts = new HashMap<>();
    private final Map<String, Integer> kcSinceLastDrop = new HashMap<>();
    private final Set<String> obtainedItems = new HashSet<>();
    // Tracks which collection log pages have been synced from the widget
    private final Set<String> syncedPages = new HashSet<>();

    private boolean loaded = false;
    private boolean dirty = false;

    public PlayerDataManager(ConfigManager configManager)
    {
        this.configManager = configManager;
    }

    /**
     * Loads data for the current RSProfile.
     */
    public void loadPlayerData()
    {
        killCounts.clear();
        kcSinceLastDrop.clear();
        obtainedItems.clear();
        syncedPages.clear();

        loadMap(KC_KEY, killCounts);
        loadMap(KC_SINCE_DROP_KEY, kcSinceLastDrop);
        loadSet(OBTAINED_KEY, obtainedItems);
        loadSet(CLOG_SYNCED_KEY, syncedPages);

        loaded = true;
        dirty = false;

        log.debug("Loaded player data: {} kc entries, {} obtained items, {} synced pages",
            killCounts.size(), obtainedItems.size(), syncedPages.size());
    }

    /**
     * Saves all data for the current RSProfile.
     */
    public void savePlayerData()
    {
        if (!loaded || !dirty)
        {
            return;
        }

        try
        {
            configManager.setRSProfileConfiguration(CONFIG_GROUP, KC_KEY, gson.toJson(killCounts));
            configManager.setRSProfileConfiguration(CONFIG_GROUP, KC_SINCE_DROP_KEY, gson.toJson(kcSinceLastDrop));
            configManager.setRSProfileConfiguration(CONFIG_GROUP, OBTAINED_KEY, gson.toJson(obtainedItems));
            configManager.setRSProfileConfiguration(CONFIG_GROUP, CLOG_SYNCED_KEY, gson.toJson(syncedPages));
            dirty = false;
        }
        catch (Exception e)
        {
            log.error("Failed to save player data: {}", e.getMessage());
        }
    }

    // ==================== KILL COUNT ====================

    /**
     * Sets the total KC for a monster. Also updates KC-since-last-drop.
     * Called by CollectionLogManager (from widget) and KillCountManager (from chat).
     */
    public void setKillCount(String monsterName, int kc)
    {
        String key = normalize(monsterName);
        int previousKc = killCounts.getOrDefault(key, 0);
        killCounts.put(key, kc);

        // If KC went up, increment the since-last-drop counter by the delta
        int delta = kc - previousKc;
        if (delta > 0)
        {
            int currentSince = kcSinceLastDrop.getOrDefault(key, 0);
            kcSinceLastDrop.put(key, currentSince + delta);
        }
        else if (previousKc == 0)
        {
            // First time seeing KC -- use full KC as since-drop baseline
            // unless we already have a since-drop value (from collection log scrape)
            if (!kcSinceLastDrop.containsKey(key))
            {
                kcSinceLastDrop.put(key, kc);
            }
        }

        dirty = true;
    }

    /**
     * Increments KC by 1. Used for loot-event-based tracking.
     */
    public void incrementKillCount(String monsterName)
    {
        String key = normalize(monsterName);
        killCounts.merge(key, 1, Integer::sum);
        kcSinceLastDrop.merge(key, 1, Integer::sum);
        dirty = true;
    }

    public int getKillCount(String monsterName)
    {
        return killCounts.getOrDefault(normalize(monsterName), 0);
    }

    /**
     * Gets KC since last drop for a monster (monster-level).
     */
    public int getKcSinceLastDrop(String monsterName)
    {
        return kcSinceLastDrop.getOrDefault(normalize(monsterName), 0);
    }

    /**
     * Gets KC since last drop for a specific item from a monster.
     * Falls back to monster-level KC if no per-item tracking exists.
     */
    public int getKcSinceLastDrop(String monsterName, String itemName)
    {
        String itemKey = normalize(monsterName) + "_" + normalize(itemName);
        Integer perItem = kcSinceLastDrop.get(itemKey);
        if (perItem != null)
        {
            return perItem;
        }
        return kcSinceLastDrop.getOrDefault(normalize(monsterName), 0);
    }

    // ==================== COLLECTION LOG ====================

    /**
     * Records an item as obtained from a specific monster.
     * Resets the per-item KC-since-drop counter.
     * Called by CollectionLogManager (widget scrape) and chat message detection.
     */
    public void recordCollectionLogItem(String itemName, String monsterName)
    {
        String normalItem = normalize(itemName);
        boolean isNew = obtainedItems.add(normalItem);

        if (monsterName != null && !monsterName.isEmpty())
        {
            // Reset per-item KC counter when item is newly obtained
            if (isNew)
            {
                String itemKey = normalize(monsterName) + "_" + normalItem;
                kcSinceLastDrop.put(itemKey, 0);
            }
        }

        dirty = true;
    }

    /**
     * Called by CollectionLogManager when an item is confirmed NOT obtained
     * from the widget (opacity > 0). Only removes if the data came from
     * a widget scrape (authoritative), not from chat.
     *
     * This corrects stale data if a player was incorrectly marked as having an item.
     */
    public void markItemNotObtained(String itemName, String monsterName)
    {
        String normalItem = normalize(itemName);
        // Only remove if we've synced this page before -- this prevents
        // removing items that were correctly detected via chat from a different page
        if (syncedPages.contains(normalize(monsterName)))
        {
            if (obtainedItems.remove(normalItem))
            {
                dirty = true;
                log.debug("Corrected: {} marked as not obtained (from widget)", itemName);
            }
        }
    }

    /**
     * Marks a collection log page as synced from the widget.
     */
    public void markPageSynced(String pageName)
    {
        syncedPages.add(normalize(pageName));
        dirty = true;
    }

    /**
     * Checks if a page has been synced from the collection log widget.
     */
    public boolean isPageSynced(String pageName)
    {
        return syncedPages.contains(normalize(pageName));
    }

    public boolean hasItem(String itemName)
    {
        return obtainedItems.contains(normalize(itemName));
    }

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
     * Returns how many collection log pages have been synced from the widget.
     */
    public int getSyncedPageCount()
    {
        return syncedPages.size();
    }

    // ==================== HELPERS ====================

    private String normalize(String name)
    {
        return name == null ? "" : name.toLowerCase().trim();
    }

    private void loadMap(String key, Map<String, Integer> target)
    {
        String json = configManager.getRSProfileConfiguration(CONFIG_GROUP, key);
        if (json != null && !json.isEmpty())
        {
            try
            {
                Type type = new TypeToken<Map<String, Integer>>(){}.getType();
                Map<String, Integer> loaded = gson.fromJson(json, type);
                if (loaded != null)
                {
                    target.putAll(loaded);
                }
            }
            catch (Exception e)
            {
                log.warn("Failed to load {}: {}", key, e.getMessage());
            }
        }
    }

    private void loadSet(String key, Set<String> target)
    {
        String json = configManager.getRSProfileConfiguration(CONFIG_GROUP, key);
        if (json != null && !json.isEmpty())
        {
            try
            {
                Type type = new TypeToken<Set<String>>(){}.getType();
                Set<String> loaded = gson.fromJson(json, type);
                if (loaded != null)
                {
                    target.addAll(loaded);
                }
            }
            catch (Exception e)
            {
                log.warn("Failed to load {}: {}", key, e.getMessage());
            }
        }
    }
}
