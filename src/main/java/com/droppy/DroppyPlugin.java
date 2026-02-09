package com.droppy;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
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
import net.runelite.client.events.ChatInput;
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
    private ChatCommandManager chatCommandManager;

    @Inject
    private Gson gson;

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private ScheduledExecutorService executor;

    private WikiDropFetcher wikiDropFetcher;
    private PlayerDataManager playerDataManager;
    private CollectionLogManager collectionLogManager;
    private CollectionLogImporter collectionLogImporter;
    private KillCountManager killCountManager;
    private DroppyApiClient apiClient;
    private DroppyPanel panel;
    private NavigationButton navButton;

    @Override
    protected void startUp() throws Exception
    {
        log.info("Droppy plugin started");

        wikiDropFetcher = new WikiDropFetcher(gson);
        playerDataManager = new PlayerDataManager(configManager, gson);
        killCountManager = new KillCountManager(playerDataManager, configManager);
        collectionLogManager = new CollectionLogManager(client, itemManager, playerDataManager);
        collectionLogImporter = new CollectionLogImporter(gson, playerDataManager);

        String apiUrl = config.apiUrl();
        if (apiUrl != null && !apiUrl.trim().isEmpty())
        {
            apiClient = new DroppyApiClient(okHttpClient, gson, apiUrl.trim());
        }

        if (client.getGameState() == GameState.LOGGED_IN)
        {
            playerDataManager.loadPlayerData();
            tryImportCollectionLog();
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
        chatCommandManager.registerCommandAsync(DRY_COMMAND, this::dryOutput, this::dryInput);
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.info("Droppy plugin stopped");
        chatCommandManager.unregisterCommand(DRY_COMMAND);
        clientToolbar.removeNavigation(navButton);
        playerDataManager.savePlayerData();
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
            tryImportCollectionLog();
        }
        else if (event.getGameState() == GameState.LOGIN_SCREEN)
        {
            playerDataManager.savePlayerData();
            collectionLogImporter.resetSession();
        }
    }

    @Subscribe
    public void onProfileChanged(ProfileChanged event)
    {
        playerDataManager.loadPlayerData();
        tryImportCollectionLog();
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == COLLECTION_LOG_GROUP_ID)
        {
            log.debug("Collection log interface opened");
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

    private void checkForCollectionLogDrops(String monsterName, Collection<ItemStack> items)
    {
        MonsterDropData dropData = wikiDropFetcher.getDropData(monsterName);
        if (dropData == null)
        {
            return;
        }

        Set<Integer> receivedIds = new HashSet<>();
        Set<String> receivedNames = new HashSet<>();
        for (ItemStack item : items)
        {
            receivedIds.add(item.getId());
            try
            {
                String name = itemManager.getItemComposition(item.getId()).getName();
                if (name != null)
                {
                    receivedNames.add(name.toLowerCase().trim());
                }
            }
            catch (Exception ignored)
            {
            }
        }

        boolean anyNew = false;
        for (DropEntry drop : dropData.getDrops())
        {
            if (playerDataManager.hasItem(drop.getItemName()))
            {
                continue;
            }

            boolean matched = (drop.getItemId() > 0 && receivedIds.contains(drop.getItemId()))
                || receivedNames.contains(drop.getItemName().toLowerCase().trim());

            if (matched)
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

    private String buildDryResponse(String monsterName)
    {
        MonsterDropData data = wikiDropFetcher.getDropData(monsterName);
        if (data == null || data.getDrops().isEmpty())
        {
            return null;
        }

        String displayName = data.getMonsterName();

        int totalKc = killCountManager.getKillCount(monsterName);
        String kcName = monsterName;
        if (totalKc == 0 && !monsterName.equalsIgnoreCase(displayName))
        {
            int displayKc = killCountManager.getKillCount(displayName);
            if (displayKc > 0)
            {
                totalKc = displayKc;
                kcName = displayName;
            }
        }

        List<String> obtainedParts = new ArrayList<>();
        List<String> dryParts = new ArrayList<>();

        for (DropEntry drop : data.getDrops())
        {
            String rateStr = drop.getRarityDisplay() != null
                ? drop.getRarityDisplay()
                : DropChanceCalculator.formatDropRate(drop.getDropRate());

            if (playerDataManager.hasItem(drop.getItemName()))
            {
                int dropKc = playerDataManager.getItemDropKc(kcName, drop.getItemName());
                if (dropKc > 0)
                {
                    double chanceAtDrop = DropChanceCalculator.calculateChance(drop.getDropRate(), dropKc);
                    obtainedParts.add(drop.getItemName() + " " + rateStr
                        + " at " + String.format("%,d", dropKc) + " kc "
                        + DropChanceCalculator.formatPercent(chanceAtDrop));
                }
                else
                {
                    obtainedParts.add(drop.getItemName());
                }
            }
            else
            {
                int kc = playerDataManager.getKcSinceLastDrop(kcName, drop.getItemName());
                double chance = DropChanceCalculator.calculateChance(drop.getDropRate(), kc);

                dryParts.add(drop.getItemName() + " " + rateStr
                    + " — " + String.format("%,d", kc) + " dry "
                    + DropChanceCalculator.formatPercent(chance));
            }
        }

        ChatMessageBuilder builder = new ChatMessageBuilder();
        int total = obtainedParts.size() + dryParts.size();

        builder.append(ChatColorType.HIGHLIGHT)
            .append(displayName)
            .append(ChatColorType.NORMAL)
            .append(" — ")
            .append(ChatColorType.HIGHLIGHT)
            .append(String.format("%,d", totalKc) + " kc")
            .append(ChatColorType.NORMAL)
            .append(" (" + obtainedParts.size() + "/" + total + " logged)");

        if (!obtainedParts.isEmpty())
        {
            builder.append(" | ")
                .append(ChatColorType.NORMAL)
                .append("Got: ")
                .append(String.join(", ", obtainedParts));
        }

        if (dryParts.isEmpty() && !obtainedParts.isEmpty())
        {
            builder.append(" — all items logged, you're done!");
        }
        else
        {
            for (String part : dryParts)
            {
                builder.append(" | ")
                    .append(ChatColorType.HIGHLIGHT)
                    .append(part);
            }
        }

        String response = builder.build();
        if (response.length() > 490)
        {
            response = response.substring(0, 487) + "...";
        }
        return response;
    }

    private boolean dryInput(ChatInput chatInput, String message)
    {
        String monsterName = message.substring(DRY_COMMAND.length()).trim();

        if (monsterName.isEmpty())
        {
            monsterName = killCountManager.getLastKcMonster();
            if (monsterName == null || monsterName.isEmpty())
            {
                return false;
            }
        }

        String response = buildDryResponse(monsterName);
        if (response == null)
        {
            return false;
        }

        if (apiClient != null)
        {
            String playerName = client.getLocalPlayer() != null
                ? client.getLocalPlayer().getName() : null;
            if (playerName != null)
            {
                String finalResponse = response;
                executor.execute(() ->
                {
                    try
                    {
                        apiClient.submitDry(playerName, finalResponse);
                    }
                    finally
                    {
                        chatInput.resume();
                    }
                });
                return true;
            }
        }

        return false;
    }

    private void dryOutput(ChatMessage chatMessage, String message)
    {
        String monsterName = message.substring(DRY_COMMAND.length()).trim();

        String senderName = chatMessage.getName().replaceAll("<[^>]+>", "").trim();
        boolean isLocalPlayer = client.getLocalPlayer() != null
            && client.getLocalPlayer().getName() != null
            && client.getLocalPlayer().getName().equalsIgnoreCase(senderName);

        String response;

        if (isLocalPlayer)
        {
            if (monsterName.isEmpty())
            {
                monsterName = killCountManager.getLastKcMonster();
                if (monsterName == null || monsterName.isEmpty())
                {
                    return;
                }
            }
            response = buildDryResponse(monsterName);
        }
        else
        {
            if (apiClient == null)
            {
                return;
            }
            response = apiClient.lookupDry(senderName);
        }

        if (response == null)
        {
            return;
        }

        String finalResponse = response;
        clientThread.invokeLater(() ->
        {
            chatMessage.getMessageNode().setRuneLiteFormatMessage(finalResponse);
            client.refreshChat();
        });

        if (isLocalPlayer)
        {
            MonsterDropData data = wikiDropFetcher.getDropData(
                monsterName.isEmpty() ? killCountManager.getLastKcMonster() : monsterName);
            if (data != null)
            {
                String displayName = data.getMonsterName();
                SwingUtilities.invokeLater(() -> panel.setCurrentMonster(displayName));
            }
        }
    }

    private void tryImportCollectionLog()
    {
        if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null)
        {
            return;
        }
        collectionLogImporter.tryImport(client.getLocalPlayer().getName());
    }

    private BufferedImage createPluginIcon()
    {
        // Pixelated sand pile icon (because dry)
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

        // Sand colors (ARGB)
        int L = 0xFFEDC9AF;  // light tan
        int M = 0xFFD2B48C;  // medium tan
        int D = 0xFFB4966E;  // dark tan
        int H = 0xFFF5E6D3;  // highlight

        // Pixel art sand pile
        int[][] px = {
            {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,H,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,H,L,L,0,0,0,0,0,0,0},
            {0,0,0,0,0,H,L,L,M,L,0,0,0,0,0,0},
            {0,0,0,0,H,L,M,L,L,M,L,0,0,0,0,0},
            {0,0,0,H,L,L,L,M,M,L,L,L,0,0,0,0},
            {0,0,H,L,M,L,M,L,L,M,L,M,L,0,0,0},
            {0,H,L,L,L,M,L,M,M,L,M,L,L,L,0,0},
            {0,L,M,L,M,L,L,L,L,L,L,M,L,M,L,0},
            {L,L,L,M,L,M,M,L,M,M,M,L,M,L,L,L},
            {M,L,M,L,L,L,L,M,L,L,L,L,L,M,L,M},
            {L,M,L,M,M,L,M,L,M,L,M,M,L,L,M,L},
            {D,L,M,L,L,M,L,M,L,M,L,L,M,L,L,D},
            {D,D,M,M,L,L,M,L,L,L,M,L,L,M,D,D},
            {D,D,D,M,M,M,L,M,M,M,L,M,M,D,D,D},
        };

        for (int y = 0; y < 16; y++)
        {
            for (int x = 0; x < 16; x++)
            {
                if (px[y][x] != 0)
                {
                    icon.setRGB(x, y, px[y][x]);
                }
            }
        }

        return icon;
    }
}
