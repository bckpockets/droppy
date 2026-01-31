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
    description = "Shows your % chance of getting collection log drops based on KC",
    tags = {"drop", "chance", "probability", "collection", "log", "kc", "calculator", "wiki"}
)
public class DroppyPlugin extends Plugin
{
    private static final int COLLECTION_DRAW_LIST_SCRIPT_ID = 2731;
    private static final int COLLECTION_LOG_GROUP_ID = 621;
    private static final String DRY_COMMAND = "!dry";

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

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == COLLECTION_LOG_GROUP_ID)
        {
            log.debug("Collection log interface opened");
            SwingUtilities.invokeLater(() -> panel.onCollectionLogOpened());
        }
    }

    // Script 2731 = collection log page rendered
    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (event.getScriptId() == COLLECTION_DRAW_LIST_SCRIPT_ID)
        {
            clientThread.invokeLater(() ->
            {
                collectionLogManager.onCollectionLogPageRendered();
                int syncedCount = playerDataManager.getSyncedPageCount();
                SwingUtilities.invokeLater(() -> panel.onCollectionLogSynced(syncedCount));
            });
        }
    }

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
                SwingUtilities.invokeLater(() -> panel.setCurrentMonster(npcName));
            }
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

        killCountManager.handleLootReceived(npcName);
        checkForCollectionLogDrops(npcName, event.getItems());
        SwingUtilities.invokeLater(() -> panel.setCurrentMonster(npcName));

        log.debug("Loot received from NPC: {}", npcName);
    }

    // Covers non-NPC sources: raids, Barrows, clues, Tempoross, etc.
    // NPC KC is already handled by onNpcLootReceived so skip increment here.
    @Subscribe
    public void onLootReceived(LootReceived event)
    {
        String name = event.getName();
        if (name == null || name.isEmpty())
        {
            return;
        }

        if (event.getType() != LootRecordType.NPC)
        {
            killCountManager.handleLootReceived(name);
        }

        checkForCollectionLogDrops(name, event.getItems());
        SwingUtilities.invokeLater(() -> panel.setCurrentMonster(name));

        log.debug("Loot received (loot tracker): {} type={}", name, event.getType());
    }

    // Cross-references loot against cached wiki drop table. Only marks items
    // as obtained if we know they're actual clog items (from widget scrape).
    private void checkForCollectionLogDrops(String monsterName, Collection<ItemStack> items)
    {
        MonsterDropData dropData = wikiDropFetcher.getCachedData(monsterName);
        if (dropData == null)
        {
            return;
        }

        Set<Integer> receivedIds = new HashSet<>();
        for (ItemStack item : items)
        {
            receivedIds.add(item.getId());
        }

        boolean anyNew = false;
        for (DropEntry drop : dropData.getDrops())
        {
            if (drop.getItemId() > 0
                && playerDataManager.isClogItem(drop.getItemName())
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
            SwingUtilities.invokeLater(() ->
            {
                panel.refreshCurrentForMonster(monsterName);
                if (monsterName.equalsIgnoreCase(panel.getSearchedMonster()))
                {
                    panel.refreshSearch();
                }
            });
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

        if (config.trackKcFromChat())
        {
            String monsterName = killCountManager.handleChatMessage(message);
            if (monsterName != null)
            {
                SwingUtilities.invokeLater(() ->
                {
                    panel.refreshCurrentForMonster(monsterName);
                    if (monsterName.equalsIgnoreCase(panel.getSearchedMonster()))
                    {
                        panel.refreshSearch();
                    }
                });
            }
        }

        if (config.autoDetectCollectionLog())
        {
            handleCollectionLogChatMessage(message);
        }
    }

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

        SwingUtilities.invokeLater(() ->
        {
            panel.refreshCurrentForMonster(lastMonster);
            if (lastMonster != null && lastMonster.equalsIgnoreCase(panel.getSearchedMonster()))
            {
                panel.refreshSearch();
            }
        });
    }

    // !dry command handler (runs async via registerCommandAsync)
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
        List<String> dryParts = new ArrayList<>();

        for (DropEntry drop : data.getDrops())
        {
            if (playerDataManager.hasItem(drop.getItemName()))
            {
                obtainedNames.add(drop.getItemName());
            }
            else
            {
                int kc = playerDataManager.getKcSinceLastDrop(displayName, drop.getItemName());
                double chance = DropChanceCalculator.calculateChance(drop.getDropRate(), kc);

                String rateStr = drop.getRarityDisplay() != null
                    ? drop.getRarityDisplay()
                    : DropChanceCalculator.formatDropRate(drop.getDropRate());

                dryParts.add(drop.getItemName() + " (" + rateStr + ") "
                    + String.format("%,d", kc) + " kc "
                    + DropChanceCalculator.formatPercent(chance));
            }
        }

        int totalItems = obtainedNames.size() + dryParts.size();

        ChatMessageBuilder builder = new ChatMessageBuilder();

        builder.append(ChatColorType.HIGHLIGHT)
            .append(displayName)
            .append(ChatColorType.NORMAL)
            .append(" — ")
            .append(ChatColorType.HIGHLIGHT)
            .append(String.format("%,d", totalKc) + " kc")
            .append(ChatColorType.NORMAL)
            .append(" — " + obtainedNames.size() + "/" + totalItems + " obtained");

        if (!obtainedNames.isEmpty())
        {
            builder.append(" | ")
                .append(ChatColorType.HIGHLIGHT)
                .append("Got: ")
                .append(ChatColorType.NORMAL)
                .append(String.join(", ", obtainedNames));
        }

        if (!dryParts.isEmpty())
        {
            builder.append(" | ")
                .append(ChatColorType.HIGHLIGHT)
                .append("Dry: ")
                .append(ChatColorType.NORMAL)
                .append(String.join(" | ", dryParts));
        }

        String response = builder.build();

        String finalDisplayName = displayName;
        clientThread.invokeLater(() ->
        {
            chatMessage.getMessageNode().setRuneLiteFormatMessage(response);
            client.refreshChat();
        });

        SwingUtilities.invokeLater(() -> panel.setCurrentMonster(finalDisplayName));
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
