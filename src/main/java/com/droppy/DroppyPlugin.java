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
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@Slf4j
@PluginDescriptor(
    name = "Droppy",
    description = "Calculates the probability of receiving collection log drops based on your current kill count",
    tags = {"drop", "chance", "probability", "collection", "log", "kc", "calculator", "wiki"}
)
public class DroppyPlugin extends Plugin
{
    // "Your Zulrah kill count is: 150."
    private static final Pattern KC_PATTERN = Pattern.compile(
        "Your (.+?) kill count is: ([\\d,]+)",
        Pattern.CASE_INSENSITIVE
    );

    // "Your Chambers of Xeric count is: 50."
    private static final Pattern KC_PATTERN_ALT = Pattern.compile(
        "Your (.+?) count is: ([\\d,]+)",
        Pattern.CASE_INSENSITIVE
    );

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

        panel = new DroppyPanel(config, wikiDropFetcher, playerDataManager, itemManager);

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

    /**
     * Detects when the player starts attacking an NPC and updates the Current tab.
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
                lastKilledMonster = npcName;
                panel.setCurrentMonster(npcName);
            }
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

        String message = event.getMessage().replaceAll("<[^>]+>", "").trim();

        if (config.trackKcFromChat())
        {
            handleKillCountMessage(message);
        }

        if (config.autoDetectCollectionLog())
        {
            handleCollectionLogMessage(message);
        }
    }

    private void handleKillCountMessage(String message)
    {
        Matcher matcher = KC_PATTERN.matcher(message);
        if (!matcher.find())
        {
            matcher = KC_PATTERN_ALT.matcher(message);
            if (!matcher.find())
            {
                return;
            }
        }

        String monsterName = matcher.group(1).trim();
        String kcStr = matcher.group(2).replace(",", "");

        try
        {
            int kc = Integer.parseInt(kcStr);
            lastKilledMonster = monsterName;
            playerDataManager.setKillCount(monsterName, kc);
            log.debug("Updated KC for {}: {}", monsterName, kc);

            // Refresh the Current tab if it's showing this monster
            panel.refreshCurrentForMonster(monsterName);

            // Also refresh Search tab if it's showing this monster
            if (monsterName.equalsIgnoreCase(panel.getSearchedMonster()))
            {
                panel.refreshSearch();
            }
        }
        catch (NumberFormatException e)
        {
            log.warn("Failed to parse KC from message: {}", message);
        }
    }

    private void handleCollectionLogMessage(String message)
    {
        Matcher matcher = COLLECTION_LOG_PATTERN.matcher(message);
        if (!matcher.find())
        {
            return;
        }

        String itemName = matcher.group(1).trim();
        if (itemName.endsWith("."))
        {
            itemName = itemName.substring(0, itemName.length() - 1).trim();
        }

        log.debug("Collection log item detected: {} (last monster: {})", itemName, lastKilledMonster);

        playerDataManager.recordCollectionLogItem(itemName, lastKilledMonster);

        // Refresh both tabs
        panel.refreshCurrentForMonster(lastKilledMonster);
        if (lastKilledMonster != null && lastKilledMonster.equalsIgnoreCase(panel.getSearchedMonster()))
        {
            panel.refreshSearch();
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

        // Ensure Current tab is showing this monster
        panel.setCurrentMonster(npcName);

        log.debug("Loot received from: {}", npcName);
    }

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
