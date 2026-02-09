package com.droppy;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

@Slf4j
public class DroppyPanel extends PluginPanel
{
    // Use RuneLite's standard color scheme
    private static final Color BACKGROUND_COLOR = ColorScheme.DARK_GRAY_COLOR;
    private static final Color HEADER_COLOR = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color ITEM_BG_COLOR = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color ITEM_BG_HOVER = ColorScheme.DARK_GRAY_HOVER_COLOR;
    private static final Color OBTAINED_COLOR = ColorScheme.PROGRESS_COMPLETE_COLOR;
    private static final Color HIGH_CHANCE_COLOR = ColorScheme.PROGRESS_INPROGRESS_COLOR;
    private static final Color LOW_CHANCE_COLOR = ColorScheme.LIGHT_GRAY_COLOR;
    private static final Color VERY_HIGH_CHANCE_COLOR = ColorScheme.PROGRESS_ERROR_COLOR;
    private static final Color TAB_ACTIVE_COLOR = ColorScheme.BRAND_ORANGE;
    private static final Color TAB_INACTIVE_COLOR = ColorScheme.DARKER_GRAY_COLOR;


    private static final String CURRENT_TAB = "CURRENT";
    private static final String SEARCH_TAB = "SEARCH";
    private static final String SYNC_TAB = "SYNC";

    // All collection log pages in OSRS
    private static final String[] ALL_CLOG_PAGES = {
        // Bosses
        "Abyssal Sire", "Alchemical Hydra", "Amoxliatl", "Araxxor", "Barrows Chests",
        "Bryophyta", "Callisto and Artio", "Cerberus", "Chaos Elemental", "Chaos Fanatic",
        "Commander Zilyana", "Corporeal Beast", "Crazy Archaeologist", "Dagannoth Kings",
        "Deranged Archaeologist", "Duke Sucellus", "General Graardor", "Giant Mole",
        "Grotesque Guardians", "Hespori", "Hueycoatl", "Kalphite Queen", "King Black Dragon",
        "Kraken", "Kree'arra", "K'ril Tsutsaroth", "Leviathan", "Mimic", "Nex",
        "Nightmare", "Obor", "Phantom Muspah", "Royal Titans", "Sarachnis", "Scorpia",
        "Scurrius", "Skotizo", "Sol Heredit", "Spindel", "The Hueycoatl",
        "Thermonuclear Smoke Devil", "Tormented Demons", "Vardorvis", "Venenatis and Spindel",
        "Vet'ion and Calvar'ion", "Vorkath", "Whisperer", "Wintertodt", "Zalcano", "Zulrah",
        // Raids
        "Chambers of Xeric", "Theatre of Blood", "Tombs of Amascut",
        // Clues
        "Beginner Treasure Trails", "Easy Treasure Trails", "Medium Treasure Trails",
        "Hard Treasure Trails", "Elite Treasure Trails", "Master Treasure Trails",
        "Shared Treasure Trail Rewards",
        // Minigames
        "Barbarian Assault", "Brimhaven Agility Arena", "Castle Wars", "Creature Creation",
        "Fishing Trawler", "Gnome Restaurant", "Guardians of the Rift", "Hallowed Sepulchre",
        "Last Man Standing", "Magic Training Arena", "Mahogany Homes", "Pest Control",
        "Pyramid Plunder", "Rogues' Den", "Shades of Mort'ton", "Soul Wars",
        "Tai Bwo Wannai Cleanup", "Temple Trekking", "Tithe Farm", "Trouble Brewing",
        "Volcanic Mine",
        // Other
        "Aerial Fishing", "All Pets", "Champion's Challenge", "Chaos Druids",
        "Chompy Bird Hunting", "Colosseum", "Cyclopes", "Defenders of Varrock",
        "Fossil Island Notes", "Glough's Experiments", "Gorak", "Graceful",
        "Miscellaneous", "Monkey Backpacks", "Motherlode Mine", "My Notes",
        "Random Events", "Revenants", "Rooftop Agility", "Shayzien Armour",
        "Shooting Stars", "Skilling Pets", "Slayer", "TzHaar", "Undead Druids"
    };

    private final DroppyConfig config;
    private final WikiDropFetcher wikiDropFetcher;
    private final PlayerDataManager playerDataManager;
    private final KillCountManager killCountManager;
    private final ItemManager itemManager;

    private JButton currentTabBtn;
    private JButton searchTabBtn;
    private JButton syncTabBtn;
    private CardLayout cardLayout;
    private JPanel cardPanel;

    // Sync tab
    private JPanel syncListPanel;

    // Current tab
    private JPanel currentDropsPanel;
    private JLabel currentMonsterTitle;
    private JLabel currentKcLabel;
    private JLabel currentStatusLabel;
    private String currentFightMonster;

    // Search tab
    private JTextField searchField;
    private JPanel searchResultsPanel;
    private JPanel searchDropsPanel;
    private JLabel searchMonsterTitle;
    private JLabel searchKcLabel;
    private JLabel searchStatusLabel;
    private String searchedMonster;

    public DroppyPanel(DroppyConfig config, WikiDropFetcher wikiDropFetcher,
                       PlayerDataManager playerDataManager,
                       KillCountManager killCountManager, ItemManager itemManager)
    {
        this.config = config;
        this.wikiDropFetcher = wikiDropFetcher;
        this.playerDataManager = playerDataManager;
        this.killCountManager = killCountManager;
        this.itemManager = itemManager;

        setLayout(new BorderLayout());
        setBackground(BACKGROUND_COLOR);
        setBorder(new EmptyBorder(0, 0, 0, 0));

        buildPanel();
    }

    private void buildPanel()
    {
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBackground(HEADER_COLOR);

        // Title
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(HEADER_COLOR);
        titleBar.setBorder(new EmptyBorder(10, 10, 6, 10));

        JLabel titleLabel = new JLabel("Droppy");
        titleLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
        titleLabel.setForeground(Color.WHITE);
        titleBar.add(titleLabel, BorderLayout.WEST);

        JLabel subtitleLabel = new JLabel("Are you dry or lucky? by SIP YE");
        subtitleLabel.setFont(FontManager.getRunescapeSmallFont());
        subtitleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        titleBar.add(subtitleLabel, BorderLayout.EAST);
        topPanel.add(titleBar);

        // Tabs
        JPanel tabBar = new JPanel(new GridBagLayout());
        tabBar.setBackground(HEADER_COLOR);
        tabBar.setBorder(new EmptyBorder(0, 0, 0, 0));

        GridBagConstraints tabGbc = new GridBagConstraints();
        tabGbc.fill = GridBagConstraints.HORIZONTAL;
        tabGbc.weightx = 0.33;
        tabGbc.gridy = 0;
        tabGbc.insets = new Insets(0, 0, 0, 0);

        currentTabBtn = createTabButton("Current", true);
        currentTabBtn.addActionListener(e -> switchTab(CURRENT_TAB));
        tabGbc.gridx = 0;
        tabBar.add(currentTabBtn, tabGbc);

        searchTabBtn = createTabButton("Search", false);
        searchTabBtn.addActionListener(e -> switchTab(SEARCH_TAB));
        tabGbc.gridx = 1;
        tabBar.add(searchTabBtn, tabGbc);

        syncTabBtn = createTabButton("Sync", false);
        syncTabBtn.addActionListener(e -> {
            refreshSyncTab();
            switchTab(SYNC_TAB);
        });
        tabGbc.gridx = 2;
        tabBar.add(syncTabBtn, tabGbc);

        topPanel.add(tabBar);

        add(topPanel, BorderLayout.NORTH);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setBackground(BACKGROUND_COLOR);

        cardPanel.add(buildCurrentTab(), CURRENT_TAB);
        cardPanel.add(buildSearchTab(), SEARCH_TAB);
        cardPanel.add(buildSyncTab(), SYNC_TAB);

        add(cardPanel, BorderLayout.CENTER);

        cardLayout.show(cardPanel, CURRENT_TAB);
    }

    private JPanel buildCurrentTab()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BACKGROUND_COLOR);

        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBackground(HEADER_COLOR);
        headerPanel.setBorder(new EmptyBorder(8, 10, 8, 10));

        currentMonsterTitle = new JLabel("Waiting for combat...");
        currentMonsterTitle.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));
        currentMonsterTitle.setForeground(Color.WHITE);
        currentMonsterTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.add(currentMonsterTitle);

        currentKcLabel = new JLabel("");
        currentKcLabel.setFont(FontManager.getRunescapeSmallFont());
        currentKcLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        currentKcLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.add(currentKcLabel);

        panel.add(headerPanel, BorderLayout.NORTH);

        currentDropsPanel = new JPanel();
        currentDropsPanel.setLayout(new BoxLayout(currentDropsPanel, BoxLayout.Y_AXIS));
        currentDropsPanel.setBackground(BACKGROUND_COLOR);

        JScrollPane scrollPane = new JScrollPane(currentDropsPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(BACKGROUND_COLOR);
        panel.add(scrollPane, BorderLayout.CENTER);

        currentStatusLabel = new JLabel("Kill a monster to see drop chances", SwingConstants.CENTER);
        currentStatusLabel.setFont(FontManager.getRunescapeSmallFont());
        currentStatusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        currentStatusLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.add(currentStatusLabel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildSearchTab()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BACKGROUND_COLOR);

        JPanel searchHeader = new JPanel();
        searchHeader.setLayout(new BoxLayout(searchHeader, BoxLayout.Y_AXIS));
        searchHeader.setBackground(HEADER_COLOR);
        searchHeader.setBorder(new EmptyBorder(8, 10, 8, 10));

        searchField = new JTextField();
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchField.setToolTipText("Search for a monster (e.g., Zulrah, Vorkath)");
        searchField.setBackground(ITEM_BG_COLOR);
        searchField.setForeground(Color.WHITE);
        searchField.setCaretColor(Color.WHITE);
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(4, 8, 4, 8)
        ));
        searchField.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                if (e.getKeyCode() == KeyEvent.VK_ENTER)
                {
                    String query = searchField.getText().trim();
                    if (!query.isEmpty())
                    {
                        loadSearchMonster(query);
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent e)
            {
                String query = searchField.getText().trim();
                if (query.length() >= 2 && e.getKeyCode() != KeyEvent.VK_ENTER)
                {
                    performSearch(query);
                }
                else if (query.isEmpty())
                {
                    clearSearchResults();
                }
            }
        });
        searchHeader.add(searchField);

        searchResultsPanel = new JPanel();
        searchResultsPanel.setLayout(new BoxLayout(searchResultsPanel, BoxLayout.Y_AXIS));
        searchResultsPanel.setBackground(ITEM_BG_COLOR);
        searchResultsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchHeader.add(searchResultsPanel);

        searchHeader.add(Box.createVerticalStrut(6));

        searchMonsterTitle = new JLabel("");
        searchMonsterTitle.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));
        searchMonsterTitle.setForeground(Color.WHITE);
        searchMonsterTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchHeader.add(searchMonsterTitle);

        searchKcLabel = new JLabel("");
        searchKcLabel.setFont(FontManager.getRunescapeSmallFont());
        searchKcLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        searchKcLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchHeader.add(searchKcLabel);

        panel.add(searchHeader, BorderLayout.NORTH);

        searchDropsPanel = new JPanel();
        searchDropsPanel.setLayout(new BoxLayout(searchDropsPanel, BoxLayout.Y_AXIS));
        searchDropsPanel.setBackground(BACKGROUND_COLOR);

        JScrollPane scrollPane = new JScrollPane(searchDropsPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(BACKGROUND_COLOR);
        panel.add(scrollPane, BorderLayout.CENTER);

        searchStatusLabel = new JLabel("Search for any monster above", SwingConstants.CENTER);
        searchStatusLabel.setFont(FontManager.getRunescapeSmallFont());
        searchStatusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        searchStatusLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.add(searchStatusLabel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildSyncTab()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BACKGROUND_COLOR);

        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBackground(HEADER_COLOR);
        headerPanel.setBorder(new EmptyBorder(8, 10, 8, 10));

        JLabel titleLabel = new JLabel("Collection Log Sync Status");
        titleLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.add(titleLabel);

        JLabel helpLabel = new JLabel("Open clog pages in-game to sync them");
        helpLabel.setFont(FontManager.getRunescapeSmallFont());
        helpLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        helpLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.add(helpLabel);

        panel.add(headerPanel, BorderLayout.NORTH);

        syncListPanel = new JPanel();
        syncListPanel.setLayout(new BoxLayout(syncListPanel, BoxLayout.Y_AXIS));
        syncListPanel.setBackground(BACKGROUND_COLOR);

        JScrollPane scrollPane = new JScrollPane(syncListPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(BACKGROUND_COLOR);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void refreshSyncTab()
    {
        syncListPanel.removeAll();

        java.util.Set<String> syncedPages = playerDataManager.getSyncedPages();

        // Find all unsynced clog pages
        java.util.List<String> unsyncedPages = new java.util.ArrayList<>();
        for (String page : ALL_CLOG_PAGES)
        {
            if (!isSynced(page, syncedPages))
            {
                unsyncedPages.add(page);
            }
        }

        int totalPages = ALL_CLOG_PAGES.length;
        int syncedCount = totalPages - unsyncedPages.size();

        // Header showing progress
        JLabel headerLabel = new JLabel(syncedCount + "/" + totalPages + " pages synced", SwingConstants.CENTER);
        headerLabel.setFont(FontManager.getRunescapeBoldFont());
        headerLabel.setForeground(syncedCount == totalPages ? OBTAINED_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
        headerLabel.setBorder(new EmptyBorder(8, 10, 8, 10));
        headerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        syncListPanel.add(headerLabel);

        if (unsyncedPages.isEmpty())
        {
            JLabel doneLabel = new JLabel("\u2713 All pages synced!", SwingConstants.CENTER);
            doneLabel.setFont(FontManager.getRunescapeSmallFont());
            doneLabel.setForeground(OBTAINED_COLOR);
            doneLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
            doneLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            syncListPanel.add(doneLabel);
        }
        else
        {
            // Show unsynced pages
            for (String page : unsyncedPages)
            {
                JPanel row = createSyncRow(page, false);
                syncListPanel.add(row);
            }
        }

        syncListPanel.revalidate();
        syncListPanel.repaint();
    }

    private boolean isSynced(String monster, java.util.Set<String> syncedPages)
    {
        String norm = monster.toLowerCase().trim();
        for (String page : syncedPages)
        {
            String pageNorm = page.toLowerCase().trim();
            // Exact match
            if (norm.equals(pageNorm)) return true;
            // Singular/plural: "tormented demon" vs "tormented demons"
            if (norm.equals(pageNorm + "s") || pageNorm.equals(norm + "s")) return true;
            if (norm.equals(pageNorm + "es") || pageNorm.equals(norm + "es")) return true;
            // Contains match for partial names
            if (pageNorm.contains(norm) || norm.contains(pageNorm)) return true;
        }
        return false;
    }

    private JPanel createSyncRow(String name, boolean synced)
    {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(ITEM_BG_COLOR);
        row.setBorder(new EmptyBorder(4, 8, 4, 8));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        String icon = synced ? "\u2713 " : "\u26A0 ";
        JLabel label = new JLabel(icon + name);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(synced ? OBTAINED_COLOR : HIGH_CHANCE_COLOR);
        row.add(label, BorderLayout.WEST);

        if (!synced)
        {
            int kc = playerDataManager.getKillCount(name);
            if (kc > 0)
            {
                JLabel kcLabel = new JLabel(String.format("%,d kc", kc));
                kcLabel.setFont(FontManager.getRunescapeSmallFont());
                kcLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                row.add(kcLabel, BorderLayout.EAST);
            }
        }

        return row;
    }

    private void switchTab(String tab)
    {
        cardLayout.show(cardPanel, tab);
        styleTabButton(currentTabBtn, CURRENT_TAB.equals(tab));
        styleTabButton(searchTabBtn, SEARCH_TAB.equals(tab));
        styleTabButton(syncTabBtn, SYNC_TAB.equals(tab));
    }

    private JButton createTabButton(String text, boolean active)
    {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        btn.setForeground(Color.WHITE);
        btn.setPreferredSize(new Dimension(0, 28));
        styleTabButton(btn, active);
        return btn;
    }

    private void styleTabButton(JButton btn, boolean active)
    {
        if (active)
        {
            btn.setBackground(TAB_ACTIVE_COLOR);
            btn.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Color.WHITE));
        }
        else
        {
            btn.setBackground(TAB_INACTIVE_COLOR);
            btn.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, TAB_INACTIVE_COLOR));
        }
    }

    public void setCurrentMonster(String monsterName)
    {
        if (monsterName == null || monsterName.equals(currentFightMonster))
        {
            return;
        }

        currentFightMonster = monsterName;
        currentMonsterTitle.setText(monsterName);
        currentDropsPanel.removeAll();

        MonsterDropData data = wikiDropFetcher.getDropData(monsterName);
        populateDrops(monsterName, data, currentDropsPanel,
            currentMonsterTitle, currentKcLabel, currentStatusLabel);
    }

    public void refreshCurrent()
    {
        if (currentFightMonster != null)
        {
            String name = currentFightMonster;
            currentFightMonster = null;
            setCurrentMonster(name);
        }
    }

    public void refreshCurrentForMonster(String monsterName)
    {
        if (monsterName != null && monsterName.equalsIgnoreCase(currentFightMonster))
        {
            currentFightMonster = null;
            setCurrentMonster(monsterName);
        }
    }

    public String getCurrentFightMonster()
    {
        return currentFightMonster;
    }

    private void performSearch(String query)
    {
        String queryLower = query.toLowerCase();
        java.util.List<String> results = new java.util.ArrayList<>();

        for (MonsterDropData data : wikiDropFetcher.getAllMonsterData())
        {
            if (data.getMonsterName().toLowerCase().contains(queryLower))
            {
                results.add(data.getMonsterName());
            }
        }

        results.sort(String::compareToIgnoreCase);

        // Limit results
        if (results.size() > 10)
        {
            results = results.subList(0, 10);
        }

        searchResultsPanel.removeAll();
        for (String result : results)
        {
            JButton btn = new JButton(result);
            btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
            btn.setAlignmentX(Component.LEFT_ALIGNMENT);
            btn.setBackground(ITEM_BG_COLOR);
            btn.setForeground(Color.WHITE);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            btn.setHorizontalAlignment(SwingConstants.LEFT);
            btn.setFont(FontManager.getRunescapeSmallFont());
            btn.addActionListener(e ->
            {
                searchField.setText(result);
                clearSearchResults();
                loadSearchMonster(result);
            });
            btn.addMouseListener(new java.awt.event.MouseAdapter()
            {
                public void mouseEntered(java.awt.event.MouseEvent evt) { btn.setBackground(ITEM_BG_HOVER); }
                public void mouseExited(java.awt.event.MouseEvent evt) { btn.setBackground(ITEM_BG_COLOR); }
            });
            searchResultsPanel.add(btn);
        }
        searchResultsPanel.revalidate();
        searchResultsPanel.repaint();
    }

    private void clearSearchResults()
    {
        searchResultsPanel.removeAll();
        searchResultsPanel.revalidate();
        searchResultsPanel.repaint();
    }

    private void loadSearchMonster(String monsterName)
    {
        searchedMonster = monsterName;
        searchDropsPanel.removeAll();

        MonsterDropData data = wikiDropFetcher.getDropData(monsterName);
        populateDrops(monsterName, data, searchDropsPanel,
            searchMonsterTitle, searchKcLabel, searchStatusLabel);
    }

    public void refreshSearch()
    {
        if (searchedMonster != null)
        {
            loadSearchMonster(searchedMonster);
        }
    }

    public String getSearchedMonster()
    {
        return searchedMonster;
    }

    private void populateDrops(String monsterName, MonsterDropData data,
                               JPanel dropsPanel, JLabel titleLabel,
                               JLabel kcLabel, JLabel statusLabel)
    {
        dropsPanel.removeAll();

        if (data == null || data.getDrops().isEmpty())
        {
            statusLabel.setText("No drop data found for " + monsterName);
            titleLabel.setText(monsterName);
            kcLabel.setText("");
            dropsPanel.revalidate();
            dropsPanel.repaint();
            return;
        }

        titleLabel.setText(data.getMonsterName());

        int totalKc = killCountManager.getKillCount(monsterName);
        int kcSinceDrop = playerDataManager.getKcSinceLastDrop(monsterName);
        if (totalKc > 0)
        {
            kcLabel.setText("Total KC: " + String.format("%,d", totalKc)
                + "  |  Since drop: " + String.format("%,d", kcSinceDrop));
        }
        else
        {
            kcLabel.setText("No KC tracked yet");
        }

        int count = 0;
        for (DropEntry drop : data.getDrops())
        {
            if (config.showOnlyUnobtained() && playerDataManager.hasItem(drop.getItemName()))
            {
                continue;
            }

            JPanel row = createDropRow(monsterName, drop);
            dropsPanel.add(row);
            dropsPanel.add(Box.createVerticalStrut(1));
            count++;
        }

        if (count == 0)
        {
            statusLabel.setText("No drop data available");
        }
        else
        {
            statusLabel.setText(count + " collection log items");
        }
        dropsPanel.revalidate();
        dropsPanel.repaint();
    }

    private JPanel createDropRow(String monsterName, DropEntry drop)
    {
        boolean obtained = playerDataManager.hasItem(drop.getItemName());
        int kc = playerDataManager.getKcSinceLastDrop(monsterName, drop.getItemName());
        double chance = DropChanceCalculator.calculateChance(drop.getDropRate(), kc);
        String chanceStr = DropChanceCalculator.formatPercent(chance);

        Color chanceColor;
        if (obtained)
        {
            chanceColor = OBTAINED_COLOR;
        }
        else if (chance >= 0.9)
        {
            chanceColor = VERY_HIGH_CHANCE_COLOR;
        }
        else if (config.highlightThreshold() > 0 && chance >= (config.highlightThreshold() / 100.0))
        {
            chanceColor = HIGH_CHANCE_COLOR;
        }
        else
        {
            chanceColor = ColorScheme.LIGHT_GRAY_COLOR;
        }

        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(ITEM_BG_COLOR);
        row.setBorder(new EmptyBorder(4, 4, 4, 4));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));

        // Item icon - prefer wiki ID, fallback to clog-scraped ID
        JLabel iconLabel = new JLabel();
        Dimension iconSize = new Dimension(32, 32);
        iconLabel.setPreferredSize(iconSize);
        iconLabel.setMinimumSize(iconSize);
        iconLabel.setMaximumSize(iconSize);
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);

        int itemId = drop.getItemId();
        if (itemId <= 0)
        {
            itemId = playerDataManager.getClogItemId(drop.getItemName());
        }

        if (itemId > 0 && itemManager != null)
        {
            AsyncBufferedImage itemImage = itemManager.getImage(itemId);
            if (itemImage != null)
            {
                iconLabel.setIcon(new ImageIcon(itemImage));
                itemImage.onLoaded(() ->
                {
                    iconLabel.setIcon(new ImageIcon(itemImage));
                    iconLabel.revalidate();
                    iconLabel.repaint();
                });
            }
        }
        else
        {
            BufferedImage placeholder = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = placeholder.createGraphics();
            g.setColor(new Color(60, 60, 60));
            g.fillRoundRect(0, 0, 32, 32, 6, 6);
            g.setColor(Color.GRAY);
            g.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 14));
            String letter = drop.getItemName().isEmpty() ? "?" : drop.getItemName().substring(0, 1).toUpperCase();
            g.drawString(letter, 10, 22);
            g.dispose();
            iconLabel.setIcon(new ImageIcon(placeholder));
        }

        row.add(iconLabel, BorderLayout.WEST);

        // Name + rate + progress bar
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        JLabel nameLabel = new JLabel();
        nameLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (obtained)
        {
            nameLabel.setText("\u2713 " + drop.getItemName());
            nameLabel.setForeground(OBTAINED_COLOR);
        }
        else
        {
            nameLabel.setText(drop.getItemName());
            nameLabel.setForeground(Color.WHITE);
        }
        centerPanel.add(nameLabel);

        String rateText = drop.getRarityDisplay() != null
            ? drop.getRarityDisplay()
            : DropChanceCalculator.formatDropRate(drop.getDropRate());
        String kcText = kc > 0
            ? " \u2022 " + String.format("%,d", kc) + " kc"
            : "";
        JLabel infoLabel = new JLabel(rateText + kcText);
        infoLabel.setFont(FontManager.getRunescapeSmallFont());
        infoLabel.setForeground(LOW_CHANCE_COLOR);
        infoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        centerPanel.add(infoLabel);

        // Progress bar - full width
        JProgressBar bar = new JProgressBar(0, 10000);
        bar.setValue((int) (chance * 10000));
        bar.setStringPainted(false);
        bar.setPreferredSize(new Dimension(0, 8));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        bar.setBackground(new Color(30, 30, 30));
        bar.setForeground(chanceColor);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);

        centerPanel.add(Box.createVerticalStrut(2));
        centerPanel.add(bar);
        centerPanel.add(Box.createVerticalStrut(3));

        // Percentage below the bar
        JLabel pctLabel = new JLabel(chanceStr);
        pctLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        pctLabel.setForeground(chanceColor);
        pctLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        centerPanel.add(pctLabel);

        row.add(centerPanel, BorderLayout.CENTER);

        row.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseEntered(java.awt.event.MouseEvent e) { row.setBackground(ITEM_BG_HOVER); }
            public void mouseExited(java.awt.event.MouseEvent e) { row.setBackground(ITEM_BG_COLOR); }
        });

        return row;
    }

    public void onCollectionLogSynced(int totalSyncedPages)
    {
        SwingUtilities.invokeLater(() ->
        {
            if (currentFightMonster != null)
            {
                MonsterDropData data = wikiDropFetcher.getDropData(currentFightMonster);
                populateDrops(currentFightMonster, data, currentDropsPanel,
                    currentMonsterTitle, currentKcLabel, currentStatusLabel);
            }
            if (searchedMonster != null)
            {
                MonsterDropData data = wikiDropFetcher.getDropData(searchedMonster);
                populateDrops(searchedMonster, data, searchDropsPanel,
                    searchMonsterTitle, searchKcLabel, searchStatusLabel);
            }
            refreshSyncTab();
        });
    }
}
