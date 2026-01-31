package com.droppy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

/**
 * Reads collection log data directly from the game widget (interface 621)
 * when the player opens the collection log in-game.
 *
 * The collection log widget can only be read when it's actually open.
 * Data is scraped from the widget and cached in PlayerDataManager for
 * persistence between sessions.
 *
 * Detection method:
 * - Items with opacity == 0 are OBTAINED
 * - Items with opacity > 0 are NOT obtained (greyed out)
 * - KC is read from the entry header widget's dynamic children
 */
@Slf4j
public class CollectionLogManager
{
    // Widget component IDs within the collection log interface (group 621)
    private static final int COLLECTION_LOG_GROUP = 621;
    private static final int ENTRY_HEADER_CHILD = 20;
    private static final int ENTRY_ITEMS_CHILD = 37;
    private static final int ENTRY_TITLE_INDEX = 0;

    private final Client client;
    private final ItemManager itemManager;
    private final PlayerDataManager playerDataManager;

    // Last page title scraped (to avoid redundant re-scrapes)
    private String lastScrapedPage;

    public CollectionLogManager(Client client, ItemManager itemManager,
                                PlayerDataManager playerDataManager)
    {
        this.client = client;
        this.itemManager = itemManager;
        this.playerDataManager = playerDataManager;
    }

    /**
     * Called when ScriptID.COLLECTION_DRAW_LIST (2731) fires, meaning
     * a collection log page has just been rendered.
     * Scrapes the current page for obtained items and kill counts.
     */
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

    /**
     * Scrapes the currently displayed collection log page.
     * Reads the page title, kill counts from the header, and
     * item obtained status from widget opacity.
     */
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

        // First child is the page title (e.g., "Zulrah", "Vorkath")
        String pageTitle = headerChildren[ENTRY_TITLE_INDEX].getText();
        if (pageTitle == null || pageTitle.isEmpty())
        {
            return;
        }

        // Strip any HTML color tags from the title
        pageTitle = stripTags(pageTitle);

        log.debug("Scraping collection log page: {}", pageTitle);

        // Parse kill counts from header children (index 2+)
        // Format: "Kills: 1,234" or "Completions: 50" etc.
        parseKillCounts(pageTitle, headerChildren);

        // Parse item obtained status from the items widget
        parseItems(pageTitle);

        // Mark this page as synced from the authoritative widget source
        playerDataManager.markPageSynced(pageTitle);

        lastScrapedPage = pageTitle;

        // Persist the scraped data
        playerDataManager.savePlayerData();
    }

    /**
     * Parses kill count entries from the collection log header.
     * Header children after index 1 contain KC labels like "Kills: 1,234"
     */
    private void parseKillCounts(String pageTitle, Widget[] headerChildren)
    {
        // Children: [0] = title, [1] = separator line, [2+] = KC entries
        for (int i = 2; i < headerChildren.length; i++)
        {
            String text = headerChildren[i].getText();
            if (text == null || text.isEmpty())
            {
                continue;
            }

            text = stripTags(text);

            // Parse "Kills: 1,234" or "Completions: 50" format
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
                // Store using page title as the monster key
                playerDataManager.setKillCount(pageTitle, kcValue);
                log.debug("Collection log KC for {} ({}): {}", pageTitle, kcType, kcValue);
            }
            catch (NumberFormatException e)
            {
                log.debug("Could not parse KC value '{}' for {}", kcValueStr, pageTitle);
            }
        }
    }

    /**
     * Parses item obtained status from the collection log items widget.
     * Items with opacity == 0 are obtained.
     */
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

        Set<String> obtainedOnPage = new HashSet<>();
        Map<Integer, String> itemIdToName = new HashMap<>();

        for (Widget itemWidget : itemWidgets)
        {
            int itemId = itemWidget.getItemId();
            if (itemId <= 0)
            {
                continue;
            }

            // Get item name from the game cache
            String itemName = itemManager.getItemComposition(itemId).getMembersName();
            if (itemName == null || itemName.isEmpty() || itemName.equals("null"))
            {
                itemName = itemManager.getItemComposition(itemId).getName();
            }
            if (itemName == null || itemName.isEmpty())
            {
                continue;
            }

            itemIdToName.put(itemId, itemName);

            // opacity == 0 means the item IS obtained
            boolean obtained = itemWidget.getOpacity() == 0;

            if (obtained)
            {
                obtainedOnPage.add(itemName);
                playerDataManager.recordCollectionLogItem(itemName, pageTitle);
                log.debug("  Obtained: {} (id={})", itemName, itemId);
            }
            else
            {
                // Mark as not obtained (don't overwrite if previously obtained
                // from another source -- the collection log is authoritative)
                playerDataManager.markItemNotObtained(itemName, pageTitle);
                log.debug("  Missing: {} (id={})", itemName, itemId);
            }
        }

        log.debug("Scraped {} items from {}: {} obtained",
            itemWidgets.length, pageTitle, obtainedOnPage.size());
    }

    /**
     * Checks if the collection log interface is currently open.
     */
    public boolean isCollectionLogOpen()
    {
        Widget container = client.getWidget(COLLECTION_LOG_GROUP, ENTRY_ITEMS_CHILD);
        return container != null && !container.isHidden();
    }

    /**
     * Gets the title of the last scraped collection log page.
     */
    public String getLastScrapedPage()
    {
        return lastScrapedPage;
    }

    /**
     * Strips HTML tags from widget text.
     */
    private static String stripTags(String text)
    {
        return text.replaceAll("<[^>]+>", "").trim();
    }
}
