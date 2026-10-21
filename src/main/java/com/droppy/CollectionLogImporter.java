package com.droppy;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Imports collection log data from the evansloan "Collection Log" RuneLite plugin.
 */
@Slf4j
public class CollectionLogImporter
{
    private final Gson gson;
    private final PlayerDataManager playerDataManager;

    public CollectionLogImporter(Gson gson, PlayerDataManager playerDataManager)
    {
        this.gson = gson;
        this.playerDataManager = playerDataManager;
    }

    public JsonObject readFile(Filepath dataFile, String username) throws Exception
    {
        if (dataFile == null || !dataFile.isFile())
        {
            throw new IllegalArgumentException("Select a Collection Log JSON file");
        }
        if (dataFile.size() > 20_000_000)
        {
            throw new IllegalArgumentException("Collection Log JSON is too large");
        }
        JsonObject root;
        try (var reader = dataFile.openReader())
        {
            root = gson.fromJson(reader, JsonObject.class);
        }
        if (root == null || !root.has("tabs") || !root.get("tabs").isJsonObject())
        {
            throw new IllegalArgumentException("This is not a Collection Log JSON file");
        }
        if (!root.has("username") || root.get("username").isJsonNull()
            || username == null || !root.get("username").getAsString()
                .replace('\u00a0', ' ').trim().equalsIgnoreCase(username.replace('\u00a0', ' ').trim()))
        {
            throw new IllegalArgumentException("This Collection Log file belongs to a different account");
        }
        return root;
    }

    public ImportResult importData(JsonObject root)
    {

        int itemsImported = 0;
        int kcImported = 0;
        int pagesImported = 0;

        JsonObject tabs = root.getAsJsonObject("tabs");
        for (Map.Entry<String, JsonElement> tabEntry : tabs.entrySet())
        {
            if (!tabEntry.getValue().isJsonObject())
            {
                continue;
            }

            JsonObject pages = tabEntry.getValue().getAsJsonObject();
            for (Map.Entry<String, JsonElement> pageEntry : pages.entrySet())
            {
                if (!pageEntry.getValue().isJsonObject())
                {
                    continue;
                }

                String pageName = pageEntry.getKey();
                JsonObject page = pageEntry.getValue().getAsJsonObject();

                // Skip pages that haven't been opened in-game
                if (!page.has("isUpdated") || !page.get("isUpdated").getAsBoolean())
                {
                    continue;
                }

                if (page.has("items") && page.get("items").isJsonArray())
                {
                    for (JsonElement itemEl : page.getAsJsonArray("items"))
                    {
                        if (!itemEl.isJsonObject())
                        {
                            continue;
                        }

                        JsonObject item = itemEl.getAsJsonObject();
                        boolean obtained = item.has("obtained")
                            && item.get("obtained").getAsBoolean();
                        String itemName = item.has("name")
                            ? item.get("name").getAsString() : null;
                        int itemId = item.has("id")
                            ? item.get("id").getAsInt() : -1;

                        if (itemName == null || itemName.isEmpty())
                        {
                            continue;
                        }

                        if (itemId > 0)
                        {
                            playerDataManager.addClogItem(itemName, itemId);
                        }

                        if (obtained && !playerDataManager.hasItem(itemName))
                        {
                            playerDataManager.markItemObtainedFromSync(itemName);
                            itemsImported++;
                        }
                    }
                }

                if (page.has("killCounts") && page.get("killCounts").isJsonArray())
                {
                    for (JsonElement kcEl : page.getAsJsonArray("killCounts"))
                    {
                        if (!kcEl.isJsonObject())
                        {
                            continue;
                        }

                        JsonObject kc = kcEl.getAsJsonObject();
                        int amount = kc.has("amount") ? kc.get("amount").getAsInt() : 0;

                        if (amount > 0)
                        {
                            int currentKc = playerDataManager.getKillCount(pageName);
                            if (amount > currentKc)
                            {
                                playerDataManager.setKillCount(pageName, amount);
                                kcImported++;
                            }
                        }
                    }
                }

                if (!playerDataManager.isPageSynced(pageName))
                {
                    playerDataManager.markPageSynced(pageName);
                    pagesImported++;
                }
            }
        }

        playerDataManager.savePlayerData();
        if (itemsImported > 0 || kcImported > 0 || pagesImported > 0)
        {
            log.info("Imported from Collection Log plugin: {} items, {} KC entries, {} pages",
                itemsImported, kcImported, pagesImported);
        }
        return new ImportResult(itemsImported, kcImported, pagesImported);
    }

    public static class ImportResult
    {
        final int items;
        final int killCounts;
        final int pages;

        ImportResult(int items, int killCounts, int pages)
        {
            this.items = items;
            this.killCounts = killCounts;
            this.pages = pages;
        }
    }
}
