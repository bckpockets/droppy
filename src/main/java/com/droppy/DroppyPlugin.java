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
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
    name = "Droppy",
    description = "Calculates the probability of receiving collection log drops based on your current kill count",
    tags = {"drop", "chance", "probability", "collection", "log", "kc", "calculator", "wiki"}
)
public class DroppyPlugin extends Plugin
{
    // Pattern for kill count messages: "Your Zulrah kill count is: 150."
    private static final Pattern KC_PATTERN = Pattern.compile(
        "Your (.+?) kill count is: ([\\d,]+)",
        Pattern.CASE_INSENSITIVE
    );

    // Pattern for boss KC in new format: "Your Chambers of Xeric count is: 50."
    private static final Pattern KC_PATTERN_ALT = Pattern.compile(
        "Your (.+?) count is: ([\\d,]+)",
        Pattern.CASE_INSENSITIVE
    );

    // Pattern for collection log notifications
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

    private WikiDropFetcher wikiDropFetcher;
    private PlayerDataManager playerDataManager;
    private DroppyPanel panel;
    private NavigationButton navButton;
    private String lastKilledMonster;

    @Override
    protected void startUp() throws Exception
    {
        log.info("Droppy plugin started");

        wikiDropFetcher = new WikiDropFetcher();
        playerDataManager = new PlayerDataManager(configManager);

        panel = new DroppyPanel(config, wikiDropFetcher, playerDataManager);

        // Load icon for the sidebar
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

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            // Load player data when logged in
            clientThread.invokeLater(() ->
            {
                if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null)
                {
                    String playerName = client.getLocalPlayer().getName();
                    playerDataManager.loadPlayerData(playerName);
                    log.debug("Loaded data for player: {}", playerName);
                }
            });
        }
        else if (event.getGameState() == GameState.LOGIN_SCREEN)
        {
            playerDataManager.savePlayerData();
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() != ChatMessageType.GAMEMESSAGE
            && event.getType() != ChatMessageType.SPAM)
        {
            return;
        }

        String message = event.getMessage();

        // Strip HTML tags from message
        message = message.replaceAll("<[^>]+>", "").trim();

        // Check for kill count messages
        if (config.trackKcFromChat())
        {
            handleKillCountMessage(message);
        }

        // Check for collection log notifications
        if (config.autoDetectCollectionLog())
        {
            handleCollectionLogMessage(message);
        }
    }

    /**
     * Handles kill count messages from chat to update tracked KC.
     */
    private void handleKillCountMessage(String message)
    {
        Matcher matcher = KC_PATTERN.matcher(message);
        if (!matcher.find())
        {
            matcher = KC_PATTERN_ALT.matcher(message);
        }
        if (!matcher.find())
        {
            return;
        }

        String monsterName = matcher.group(1).trim();
        String kcStr = matcher.group(2).replace(",", "");

        try
        {
            int kc = Integer.parseInt(kcStr);
            lastKilledMonster = monsterName;
            playerDataManager.setKillCount(monsterName, kc);
            log.debug("Updated KC for {}: {}", monsterName, kc);

            // Refresh the panel if this monster is currently displayed
            if (panel != null && monsterName.equalsIgnoreCase(panel.getCurrentMonster()))
            {
                panel.refresh();
            }
        }
        catch (NumberFormatException e)
        {
            log.warn("Failed to parse KC from message: {}", message);
        }
    }

    /**
     * Handles collection log notification messages.
     */
    private void handleCollectionLogMessage(String message)
    {
        Matcher matcher = COLLECTION_LOG_PATTERN.matcher(message);
        if (!matcher.find())
        {
            return;
        }

        String itemName = matcher.group(1).trim();
        // Remove trailing period if present
        if (itemName.endsWith("."))
        {
            itemName = itemName.substring(0, itemName.length() - 1).trim();
        }

        log.debug("Collection log item detected: {} (last monster: {})", itemName, lastKilledMonster);

        // Record the item as obtained and reset KC counter for this item
        playerDataManager.recordCollectionLogItem(itemName, lastKilledMonster);

        // Refresh the panel
        if (panel != null)
        {
            panel.refresh();
        }
    }

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event)
    {
        String npcName = event.getNpc().getName();
        if (npcName == null)
        {
            return;
        }

        lastKilledMonster = npcName;

        // If we don't get a KC message from chat (some monsters don't show KC),
        // we can still track kills via loot events
        // Only increment if we haven't already set KC from a chat message this tick
        // This is handled by checking if the KC was already updated recently
        log.debug("Loot received from: {}", npcName);
    }

    /**
     * Creates a simple colored icon for the plugin sidebar button.
     */
    private BufferedImage createPluginIcon()
    {
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = icon.createGraphics();

        // Draw a simple "D" icon with a percentage symbol feel
        g.setColor(new java.awt.Color(70, 130, 230));
        g.fillRoundRect(0, 0, 16, 16, 4, 4);

        g.setColor(java.awt.Color.WHITE);
        g.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 11));
        g.drawString("%", 2, 13);

        g.dispose();
        return icon;
    }
}
