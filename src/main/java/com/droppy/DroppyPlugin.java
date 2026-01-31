package com.droppy;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
    name = "Droppy",
    description = "Calculates the probability of receiving collection log drops based on your current kill count",
    tags = {"drop", "chance", "probability", "collection", "log", "kc", "calculator", "wiki"}
)
public class DroppyPlugin extends Plugin
{
    /**
     * Script ID fired when a collection log page is drawn/rendered.
     */
    private static final int COLLECTION_DRAW_LIST_SCRIPT_ID = 2731;

    /**
     * The collection log interface group ID.
     */
    private static final int COLLECTION_LOG_GROUP_ID = 621;

    // "New item added to your collection log: Tanzanite fang"
    private static final Pattern COLLECTION_LOG_PATTERN = Pattern.compile(
        "New item added to your collection log: (.+)",
        Pattern.CASE_INSENSITIVE
    );

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private DroppyConfig config;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ItemManager itemManager;

    @Inject
    private OkHttpClient okHttpClient;

    private WikiDropFetcher wikiDropFetcher;
    private PlayerDataManager playerDataManager;
    private CollectionLogManager collectionLogManager;
    private KillCountManager killCountManager;
    private DroppyPanel panel;
    private NavigationButton navButton;

    @Override
    protected void startUp() throws Exception
    {
        log.info("Droppy plugin started");

        wikiDropFetcher = new WikiDropFetcher(okHttpClient);
        playerDataManager = new PlayerDataManager(configManager);
        killCountManager = new KillCountManager(playerDataManager, configManager);
        collectionLogManager = new CollectionLogManager(client, itemManager, playerDataManager);

        // Load data if already logged in
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            playerDataManager.loadPlayerData();
        }

        panel = new DroppyPanel(config, wikiDropFetcher, playerDataManager,
            killCountManager, itemManager);

        BufferedImage icon = createPluginIcon();

        navButton = NavigationButton.builder()
            .tooltip("Droppy - Drop Chance Calculator")
            .icon(icon)
            .priority(10)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.info("Droppy plugin stopped");
        clientToolbar.removeNavigation(navButton);
        playerDataManager.savePlayerData();
        wikiDropFetcher.clearCache();
    }

    @Provides
    DroppyConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DroppyConfig.class);
    }

    // ==================== GAME STATE EVENTS ====================

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            playerDataManager.loadPlayerData();
        }
        else if (event.getGameState() == GameState.LOGIN_SCREEN)
        {
            playerDataManager.savePlayerData();
        }
    }

    /**
     * Handles RSProfile changes (account switches).
     */
    @Subscribe
    public void onProfileChanged(ProfileChanged event)
    {
        playerDataManager.loadPlayerData();
    }

    // ==================== COLLECTION LOG WIDGET EVENTS ====================

    /**
     * Fires when the collection log interface (group 621) is loaded.
     */
    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == COLLECTION_LOG_GROUP_ID)
        {
            log.debug("Collection log interface opened");
            panel.onCollectionLogOpened();
        }
    }

    /**
     * Fires after script 2731 (COLLECTION_DRAW_LIST) executes.
     * This is the primary mechanism to scrape collection log data.
     */
    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (event.getScriptId() == COLLECTION_DRAW_LIST_SCRIPT_ID)
        {
            clientThread.invokeLater(() ->
            {
                collectionLogManager.onCollectionLogPageRendered();
                panel.onCollectionLogSynced(playerDataManager.getSyncedPageCount());
            });
        }
    }

    // ==================== COMBAT & LOOT EVENTS ====================

    /**
     * Detects when the player starts attacking an NPC.
     */
    @Subscribe
    public void onInteractingChanged(InteractingChanged event)
    {
        if (event.getSource() != client.getLocalPlayer())
        {
            return;
        }

        if (event.getTarget() instanceof NPC)
        {
            NPC npc = (NPC) event.getTarget();
            String npcName = npc.getName();
            if (npcName != null && !npcName.isEmpty())
            {
                killCountManager.setLastKcMonster(npcName);
                panel.setCurrentMonster(npcName);
            }
        }
    }

    /**
     * Fires when loot is received from an NPC.
     */
    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event)
    {
        String npcName = event.getNpc().getName();
        if (npcName == null)
        {
            return;
        }

        killCountManager.handleLootReceived(npcName);
        panel.setCurrentMonster(npcName);

        log.debug("Loot received from: {}", npcName);
    }

    // ==================== CHAT MESSAGE EVENTS ====================

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() != ChatMessageType.GAMEMESSAGE
            && event.getType() != ChatMessageType.SPAM)
        {
            return;
        }

        String message = event.getMessage();

        // KC from chat (secondary source)
        if (config.trackKcFromChat())
        {
            String monsterName = killCountManager.handleChatMessage(message);
            if (monsterName != null)
            {
                panel.refreshCurrentForMonster(monsterName);
                if (monsterName.equalsIgnoreCase(panel.getSearchedMonster()))
                {
                    panel.refreshSearch();
                }
            }
        }

        // Collection log notification from chat
        if (config.autoDetectCollectionLog())
        {
            handleCollectionLogChatMessage(message);
        }
    }

    /**
     * Handles "New item added to your collection log" chat messages.
     */
    private void handleCollectionLogChatMessage(String message)
    {
        String cleaned = message.replaceAll("<[^>]+>", "").trim();
        Matcher matcher = COLLECTION_LOG_PATTERN.matcher(cleaned);
        if (!matcher.find())
        {
            return;
        }

        String itemName = matcher.group(1).trim();
        if (itemName.endsWith("."))
        {
            itemName = itemName.substring(0, itemName.length() - 1).trim();
        }

        String lastMonster = killCountManager.getLastKcMonster();
        log.debug("Collection log item from chat: {} (monster: {})", itemName, lastMonster);

        playerDataManager.recordCollectionLogItem(itemName, lastMonster);
        playerDataManager.savePlayerData();

        // Refresh panels
        panel.refreshCurrentForMonster(lastMonster);
        if (lastMonster != null && lastMonster.equalsIgnoreCase(panel.getSearchedMonster()))
        {
            panel.refreshSearch();
        }
    }

    // ==================== HELPERS ====================

    private BufferedImage createPluginIcon()
    {
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = icon.createGraphics();

        g.setColor(new java.awt.Color(70, 130, 230));
        g.fillRoundRect(0, 0, 16, 16, 4, 4);

        g.setColor(java.awt.Color.WHITE);
        g.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 11));
        g.drawString("%", 2, 13);

        g.dispose();
        return icon;
    }
}
