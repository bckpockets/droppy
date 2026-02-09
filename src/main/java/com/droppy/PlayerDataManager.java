package com.droppy;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Slf4j
public class PlayerDataManager
{
    private static final String CONFIG_GROUP = "droppy";
    private static final String KC_KEY = "killCounts";
    private static final String KC_SINCE_DROP_KEY = "kcSinceDrop";
    private static final String LAST_DROP_KC_KEY = "lastDropKc";
    private static final String OBTAINED_KEY = "obtainedItems";
    private static final String CLOG_SYNCED_KEY = "clogSyncedPages";
    private static final String CLOG_ITEMS_KEY = "clogItems";

    private final ConfigManager configManager;
    private final Gson gson;

    private final Map<String, Integer> killCounts = new ConcurrentHashMap<>();
    private final Map<String, Integer> kcSinceLastDrop = new ConcurrentHashMap<>();
    private final Map<String, Integer> lastDropKc = new ConcurrentHashMap<>();
    private final Set<String> obtainedItems = ConcurrentHashMap.newKeySet();
    private final Set<String> syncedPages = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> clogItems = new ConcurrentHashMap<>();

    private volatile boolean loaded = false;
    private volatile boolean dirty = false;

    public PlayerDataManager(ConfigManager configManager, Gson gson)
    {
        this.configManager = configManager;
        this.gson = gson;
    }

    public void loadPlayerData()
    {
        killCounts.clear();
        kcSinceLastDrop.clear();
        lastDropKc.clear();
        obtainedItems.clear();
        syncedPages.clear();
        clogItems.clear();

        loadMap(KC_KEY, killCounts);
        loadMap(KC_SINCE_DROP_KEY, kcSinceLastDrop);
        loadMap(LAST_DROP_KC_KEY, lastDropKc);
        loadSet(OBTAINED_KEY, obtainedItems);
        loadSet(CLOG_SYNCED_KEY, syncedPages);
        loadMap(CLOG_ITEMS_KEY, clogItems);

        loaded = true;
        dirty = false;

        log.debug("Loaded player data: {} kc entries, {} obtained items, {} synced pages",
            killCounts.size(), obtainedItems.size(), syncedPages.size());
    }

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
            configManager.setRSProfileConfiguration(CONFIG_GROUP, LAST_DROP_KC_KEY, gson.toJson(lastDropKc));
            configManager.setRSProfileConfiguration(CONFIG_GROUP, OBTAINED_KEY, gson.toJson(obtainedItems));
            configManager.setRSProfileConfiguration(CONFIG_GROUP, CLOG_SYNCED_KEY, gson.toJson(syncedPages));
            configManager.setRSProfileConfiguration(CONFIG_GROUP, CLOG_ITEMS_KEY, gson.toJson(clogItems));
            dirty = false;
        }
        catch (Exception e)
        {
            log.error("Failed to save player data: {}", e.getMessage());
        }
    }

    public void setKillCount(String monsterName, int kc)
    {
        String key = normalize(monsterName);
        int previousKc = killCounts.getOrDefault(key, 0);
        killCounts.put(key, kc);

        int delta = kc - previousKc;
        if (delta > 0)
        {
            kcSinceLastDrop.merge(key, delta, Integer::sum);
        }
        else if (previousKc == 0 && !kcSinceLastDrop.containsKey(key))
        {
            kcSinceLastDrop.put(key, kc);
        }

        dirty = true;
    }

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

    public int getKcSinceLastDrop(String monsterName)
    {
        return kcSinceLastDrop.getOrDefault(normalize(monsterName), 0);
    }

    public int getKcSinceLastDrop(String monsterName, String itemName)
    {
        String monsterKey = normalize(monsterName);
        String itemKey = monsterKey + "_" + normalize(itemName);

        Integer dropKc = lastDropKc.get(itemKey);
        if (dropKc != null)
        {
            int currentKc = killCounts.getOrDefault(monsterKey, 0);
            return Math.max(0, currentKc - dropKc);
        }

        return killCounts.getOrDefault(monsterKey, 0);
    }

    public int getItemDropKc(String monsterName, String itemName)
    {
        String itemKey = normalize(monsterName) + "_" + normalize(itemName);
        Integer dropKc = lastDropKc.get(itemKey);
        return dropKc != null ? dropKc : -1;
    }

    public void recordCollectionLogItem(String itemName, String monsterName)
    {
        String normalItem = normalize(itemName);
        obtainedItems.add(normalItem);

        if (monsterName != null && !monsterName.isEmpty())
        {
            String monsterKey = normalize(monsterName);
            String itemKey = monsterKey + "_" + normalItem;
            int currentKc = killCounts.getOrDefault(monsterKey, 0);
            lastDropKc.put(itemKey, currentKc);
            kcSinceLastDrop.put(monsterKey, 0);
        }

        dirty = true;
    }

    public void markItemObtainedFromSync(String itemName)
    {
        obtainedItems.add(normalize(itemName));
        dirty = true;
    }

    public void markItemNotObtained(String itemName, String monsterName)
    {
        String normalItem = normalize(itemName);
        if (syncedPages.contains(normalize(monsterName)))
        {
            if (obtainedItems.remove(normalItem))
            {
                dirty = true;
                log.debug("Corrected: {} marked as not obtained (from widget)", itemName);
            }
        }
    }

    public void markPageSynced(String pageName)
    {
        syncedPages.add(normalize(pageName));
        dirty = true;
    }

    public boolean isPageSynced(String pageName)
    {
        return syncedPages.contains(normalize(pageName));
    }

    public boolean isPageSyncedFuzzy(String pageName)
    {
        String norm = normalize(pageName);
        for (String page : syncedPages)
        {
            String pageNorm = normalize(page);
            if (norm.equals(pageNorm)) return true;
            if (norm.equals(pageNorm + "s") || pageNorm.equals(norm + "s")) return true;
            if (norm.equals(pageNorm + "es") || pageNorm.equals(norm + "es")) return true;
            if (pageNorm.contains(norm) || norm.contains(pageNorm)) return true;
        }
        return false;
    }

    public void addClogItem(String itemName, int itemId)
    {
        String key = normalize(itemName);
        if (!clogItems.containsKey(key))
        {
            clogItems.put(key, itemId);
            dirty = true;
        }
    }

    public boolean isClogItem(String itemName)
    {
        return clogItems.containsKey(normalize(itemName));
    }

    public int getClogItemId(String itemName)
    {
        return clogItems.getOrDefault(normalize(itemName), -1);
    }

    public boolean hasItem(String itemName)
    {
        return obtainedItems.contains(normalize(itemName));
    }

    public Set<String> getObtainedItems()
    {
        return new HashSet<>(obtainedItems);
    }

    public Set<String> getTrackedMonsters()
    {
        return new HashSet<>(killCounts.keySet());
    }

    public int getSyncedPageCount()
    {
        return syncedPages.size();
    }

    public Set<String> getSyncedPages()
    {
        return new HashSet<>(syncedPages);
    }


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
