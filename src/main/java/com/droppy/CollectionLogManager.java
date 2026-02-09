package com.droppy;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

@Slf4j
public class CollectionLogManager
{
    private static final int COLLECTION_LOG_GROUP = 621;
    private static final int ENTRY_HEADER_CHILD = 20;
    private static final int ENTRY_ITEMS_CHILD = 37;
    private static final int ENTRY_TITLE_INDEX = 0;

    private final Client client;
    private final ItemManager itemManager;
    private final PlayerDataManager playerDataManager;

    private String lastScrapedPage;

    public CollectionLogManager(Client client, ItemManager itemManager,
                                PlayerDataManager playerDataManager)
    {
        this.client = client;
        this.itemManager = itemManager;
        this.playerDataManager = playerDataManager;
    }

    public void onCollectionLogPageRendered()
    {
        try
        {
            scrapeCurrentPage();
        }
        catch (Exception e)
        {
            log.warn("Failed to scrape collection log page: {}", e.getMessage());
        }
    }

    private void scrapeCurrentPage()
    {
        Widget entryHeader = client.getWidget(COLLECTION_LOG_GROUP, ENTRY_HEADER_CHILD);
        if (entryHeader == null)
        {
            return;
        }

        Widget[] headerChildren = entryHeader.getDynamicChildren();
        if (headerChildren == null || headerChildren.length < 1)
        {
            return;
        }

        String pageTitle = headerChildren[ENTRY_TITLE_INDEX].getText();
        if (pageTitle == null || pageTitle.isEmpty())
        {
            return;
        }

        pageTitle = stripTags(pageTitle);

        log.debug("Scraping collection log page: {}", pageTitle);

        parseKillCounts(pageTitle, headerChildren);
        parseItems(pageTitle);

        playerDataManager.markPageSynced(pageTitle);
        lastScrapedPage = pageTitle;
        playerDataManager.savePlayerData();
    }

    private void parseKillCounts(String pageTitle, Widget[] headerChildren)
    {
        for (int i = 2; i < headerChildren.length; i++)
        {
            String text = headerChildren[i].getText();
            if (text == null || text.isEmpty())
            {
                continue;
            }

            text = stripTags(text);

            int colonIdx = text.indexOf(':');
            if (colonIdx < 0)
            {
                continue;
            }

            String kcType = text.substring(0, colonIdx).trim();
            String kcValueStr = text.substring(colonIdx + 1).trim().replace(",", "");

            try
            {
                int kcValue = Integer.parseInt(kcValueStr);
                playerDataManager.setKillCount(pageTitle, kcValue);
                log.debug("Collection log KC for {} ({}): {}", pageTitle, kcType, kcValue);
            }
            catch (NumberFormatException e)
            {
                log.debug("Could not parse KC value '{}' for {}", kcValueStr, pageTitle);
            }
        }
    }

    private void parseItems(String pageTitle)
    {
        Widget itemsContainer = client.getWidget(COLLECTION_LOG_GROUP, ENTRY_ITEMS_CHILD);
        if (itemsContainer == null)
        {
            return;
        }

        Widget[] itemWidgets = itemsContainer.getDynamicChildren();
        if (itemWidgets == null)
        {
            return;
        }

        int obtainedCount = 0;

        for (Widget itemWidget : itemWidgets)
        {
            int itemId = itemWidget.getItemId();
            if (itemId <= 0)
            {
                continue;
            }

            String itemName = itemManager.getItemComposition(itemId).getName();
            if (itemName == null || itemName.isEmpty())
            {
                continue;
            }

            playerDataManager.addClogItem(itemName, itemId);

            boolean obtained = itemWidget.getOpacity() == 0;

            if (obtained)
            {
                obtainedCount++;
                playerDataManager.markItemObtainedFromSync(itemName);
                log.debug("  Obtained: {} (id={})", itemName, itemId);
            }
            else
            {
                playerDataManager.markItemNotObtained(itemName, pageTitle);
                log.debug("  Missing: {} (id={})", itemName, itemId);
            }
        }

        log.debug("Scraped {} items from {}: {} obtained",
            itemWidgets.length, pageTitle, obtainedCount);
    }

    public boolean isCollectionLogOpen()
    {
        Widget container = client.getWidget(COLLECTION_LOG_GROUP, ENTRY_ITEMS_CHILD);
        return container != null && !container.isHidden();
    }

    public String getLastScrapedPage()
    {
        return lastScrapedPage;
    }

    private static String stripTags(String text)
    {
        return text.replaceAll("<[^>]+>", "").trim();
    }
}
