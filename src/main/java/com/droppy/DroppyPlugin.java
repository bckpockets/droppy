package com.droppy;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
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
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
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
    private static final int COLLECTION_DRAW_LIST_SCRIPT_ID = 2731;
    private static final int COLLECTION_LOG_GROUP_ID = 621;
    private static final String DRY_COMMAND = "!dry";

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

    @Inject
    private ChatCommandManager chatCommandManager;

    @Inject
    private ChatMessageManager chatMessageManager;

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
        chatCommandManager.registerCommandAsync(DRY_COMMAND, this::dryLookup);
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.info("Droppy plugin stopped");
        chatCommandManager.unregisterCommand(DRY_COMMAND);
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

    @Subscribe
    public void onProfileChanged(ProfileChanged event)
    {
        playerDataManager.loadPlayerData();
    }

    // ==================== COLLECTION LOG WIDGET EVENTS ====================

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
     * This is the primary mechanism to scrape collection log data
     * during the initial flip-through.
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
     * Updates the panel to show that monster's drop table.
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
     * Fires when loot is received from an NPC kill.
     * This is our primary forward-tracking mechanism for NPC kills:
     * increments KC and cross-references received items against the
     * wiki drop table to detect collection log drops in real-time.
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
        checkForCollectionLogDrops(npcName, event.getItems());
        panel.setCurrentMonster(npcName);

        log.debug("Loot received from NPC: {}", npcName);
    }

    /**
     * Fires when the loot tracker plugin processes any loot event.
     * Covers sources beyond NPC kills: raids, Barrows, clue scrolls,
     * Tempoross, pickpockets, etc.
     *
     * NPC kills are already KC-incremented by onNpcLootReceived, so we
     * skip the increment here for LootRecordType.NPC to avoid double-counting.
     * Cross-referencing and panel updates are idempotent and safe to repeat.
     */
    @Subscribe
    public void onLootReceived(LootReceived event)
    {
        String name = event.getName();
        if (name == null || name.isEmpty())
        {
            return;
        }

        // Only increment KC for non-NPC sources; NPC kills handled by onNpcLootReceived
        if (event.getType() != LootRecordType.NPC)
        {
            killCountManager.handleLootReceived(name);
        }

        // Cross-referencing is idempotent -- safe to run for all loot sources
        checkForCollectionLogDrops(name, event.getItems());
        panel.setCurrentMonster(name);

        log.debug("Loot received (loot tracker): {} type={}", name, event.getType());
    }

    /**
     * Cross-references received loot items against the cached wiki drop table.
     * If a received item matches a collection log entry that the player
     * doesn't have yet, records it as obtained immediately.
     *
     * This provides real-time drop detection without waiting for the
     * "New item added to your collection log" chat message (which can
     * be missed if chat is filtered or scrolled).
     */
    private void checkForCollectionLogDrops(String monsterName, Collection<ItemStack> items)
    {
        MonsterDropData dropData = wikiDropFetcher.getCachedData(monsterName);
        if (dropData == null)
        {
            return;
        }

        // Collect all received item IDs for quick lookup
        Set<Integer> receivedIds = new HashSet<>();
        for (ItemStack item : items)
        {
            receivedIds.add(item.getId());
        }

        boolean anyNew = false;
        for (DropEntry drop : dropData.getDrops())
        {
            if (drop.getItemId() > 0
                && drop.isCollectionLog()
                && receivedIds.contains(drop.getItemId())
                && !playerDataManager.hasItem(drop.getItemName()))
            {
                playerDataManager.recordCollectionLogItem(drop.getItemName(), monsterName);
                log.info("Collection log drop detected from loot: {} from {}",
                    drop.getItemName(), monsterName);
                anyNew = true;
            }
        }

        if (anyNew)
        {
            playerDataManager.savePlayerData();
            // Refresh panels to show the newly obtained item
            panel.refreshCurrentForMonster(monsterName);
            if (monsterName.equalsIgnoreCase(panel.getSearchedMonster()))
            {
                panel.refreshSearch();
            }
        }
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

        // KC from chat (authoritative -- overrides loot-based tracking)
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
     * This is a secondary detection mechanism alongside the loot
     * cross-referencing in checkForCollectionLogDrops.
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

        panel.refreshCurrentForMonster(lastMonster);
        if (lastMonster != null && lastMonster.equalsIgnoreCase(panel.getSearchedMonster()))
        {
            panel.refreshSearch();
        }
    }

    // ==================== !DRY CHAT COMMAND ====================

    /**
     * Handles the !dry command. Runs async (network call may be needed).
     *
     * Usage:
     *   !dry zulrah     -- shows dry rates for Zulrah
     *   !dry             -- shows dry rates for the last killed monster
     *
     * Replaces the chat message with a summary visible to other players,
     * then queues game messages with per-item details visible only to you.
     */
    private void dryLookup(ChatMessage chatMessage, String message)
    {
        String monsterName = message.substring(DRY_COMMAND.length()).trim();

        if (monsterName.isEmpty())
        {
            monsterName = killCountManager.getLastKcMonster();
            if (monsterName == null || monsterName.isEmpty())
            {
                return;
            }
        }

        MonsterDropData data = wikiDropFetcher.fetchMonsterDrops(monsterName);
        if (data == null || data.getDrops().isEmpty())
        {
            return;
        }

        String displayName = data.getMonsterName();
        int totalKc = killCountManager.getKillCount(displayName);

        List<String> obtainedNames = new ArrayList<>();
        List<DropEntry> unobtained = new ArrayList<>();

        for (DropEntry drop : data.getDrops())
        {
            if (playerDataManager.hasItem(drop.getItemName()))
            {
                obtainedNames.add(drop.getItemName());
            }
            else
            {
                unobtained.add(drop);
            }
        }

        int totalItems = obtainedNames.size() + unobtained.size();

        // Summary line (replaces the !dry message, visible to other players)
        String response = new ChatMessageBuilder()
            .append(ChatColorType.HIGHLIGHT)
            .append(displayName)
            .append(ChatColorType.NORMAL)
            .append(" — ")
            .append(ChatColorType.HIGHLIGHT)
            .append(String.format("%,d", totalKc) + " kc")
            .append(ChatColorType.NORMAL)
            .append(" — " + obtainedNames.size() + "/" + totalItems + " items obtained")
            .build();

        clientThread.invokeLater(() ->
        {
            chatMessage.getMessageNode().setRuneLiteFormatMessage(response);
            client.refreshChat();
        });

        // Detail messages (game messages, visible only to the player)
        if (!obtainedNames.isEmpty())
        {
            String obtainedMsg = new ChatMessageBuilder()
                .append(ChatColorType.HIGHLIGHT)
                .append("Obtained: ")
                .append(ChatColorType.NORMAL)
                .append(String.join(", ", obtainedNames))
                .build();

            chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .runeLiteFormattedMessage(obtainedMsg)
                .build());
        }

        for (DropEntry drop : unobtained)
        {
            int kc = playerDataManager.getKcSinceLastDrop(displayName, drop.getItemName());
            double chance = DropChanceCalculator.calculateChance(drop.getDropRate(), kc);

            String rateStr = drop.getRarityDisplay() != null
                ? drop.getRarityDisplay()
                : DropChanceCalculator.formatDropRate(drop.getDropRate());

            String itemMsg = new ChatMessageBuilder()
                .append(ChatColorType.HIGHLIGHT)
                .append(drop.getItemName())
                .append(ChatColorType.NORMAL)
                .append(" (" + rateStr + ") — ")
                .append(String.format("%,d", kc) + " kc — ")
                .append(ChatColorType.HIGHLIGHT)
                .append(DropChanceCalculator.formatPercent(chance))
                .append(ChatColorType.NORMAL)
                .append(" chance")
                .build();

            chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .runeLiteFormattedMessage(itemMsg)
                .build());
        }

        // Also update the side panel to show this monster
        SwingUtilities.invokeLater(() -> panel.setCurrentMonster(displayName));
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
