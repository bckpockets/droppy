package com.droppy;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Imports collection log data from the evansloan "Collection Log" RuneLite plugin.
 */
@Slf4j
public class CollectionLogImporter
{
    private final Gson gson;
    private final PlayerDataManager playerDataManager;

    private boolean importedThisSession = false;

    public CollectionLogImporter(Gson gson, PlayerDataManager playerDataManager)
    {
        this.gson = gson;
        this.playerDataManager = playerDataManager;
    }

    public void tryImport(String username)
    {
        if (importedThisSession || username == null || username.isEmpty())
        {
            return;
        }

        try
        {
            Path dataFile = findDataFile(username);
            if (dataFile == null || !Files.exists(dataFile))
            {
                log.debug("No Collection Log plugin data found for {}", username);
                return;
            }

            String json = Files.readString(dataFile);
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null || !root.has("tabs"))
            {
                return;
            }

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

                    playerDataManager.markPageSynced(pageName);
                    pagesImported++;
                }
            }

            if (itemsImported > 0 || kcImported > 0)
            {
                playerDataManager.savePlayerData();
                log.info("Imported from Collection Log plugin: {} items, {} KC entries, {} pages",
                    itemsImported, kcImported, pagesImported);
            }
            else
            {
                log.debug("Collection Log plugin data found but no new data to import");
            }

            importedThisSession = true;
        }
        catch (Exception e)
        {
            log.debug("Could not import Collection Log plugin data: {}", e.getMessage());
            importedThisSession = true;
        }
    }

    public void resetSession()
    {
        importedThisSession = false;
    }

    private Path findDataFile(String username)
    {
        String userHome = System.getProperty("user.home");
        if (userHome == null)
        {
            return null;
        }

        File runeliteDir = new File(userHome, ".runelite");
        if (!runeliteDir.exists())
        {
            return null;
        }

        File dataFile = new File(runeliteDir,
            "collectionlog" + File.separator
                + "data" + File.separator
                + username + File.separator
                + "collectionlog-" + username + ".json");

        if (dataFile.exists())
        {
            return dataFile.toPath();
        }

        return null;
    }
}
