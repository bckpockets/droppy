package com.droppy;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
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
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The side panel for the Droppy plugin.
 * Shows a search bar, monster list, and per-item drop chance percentages.
 */
@Slf4j
public class DroppyPanel extends PluginPanel
{
    private static final Color BACKGROUND_COLOR = ColorScheme.DARK_GRAY_COLOR;
    private static final Color HEADER_COLOR = new Color(30, 30, 30);
    private static final Color ITEM_BG_COLOR = new Color(45, 45, 45);
    private static final Color ITEM_BG_HOVER = new Color(55, 55, 55);
    private static final Color OBTAINED_COLOR = new Color(0, 180, 0);
    private static final Color HIGH_CHANCE_COLOR = new Color(255, 200, 0);
    private static final Color LOW_CHANCE_COLOR = new Color(180, 180, 180);
    private static final Color VERY_HIGH_CHANCE_COLOR = new Color(255, 80, 80);

    private final DroppyConfig config;
    private final WikiDropFetcher wikiDropFetcher;
    private final PlayerDataManager playerDataManager;

    private JTextField searchField;
    private JPanel searchResultsPanel;
    private JPanel dropsPanel;
    private JPanel contentPanel;
    private JLabel statusLabel;
    private JLabel monsterTitleLabel;
    private JLabel kcLabel;
    private String currentMonster;

    public DroppyPanel(DroppyConfig config, WikiDropFetcher wikiDropFetcher,
                       PlayerDataManager playerDataManager)
    {
        this.config = config;
        this.wikiDropFetcher = wikiDropFetcher;
        this.playerDataManager = playerDataManager;

        setLayout(new BorderLayout());
        setBackground(BACKGROUND_COLOR);
        setBorder(new EmptyBorder(0, 0, 0, 0));

        buildPanel();
    }

    private void buildPanel()
    {
        // Header panel with title
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBackground(HEADER_COLOR);
        headerPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel titleLabel = new JLabel("Droppy");
        titleLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.add(titleLabel);

        JLabel subtitleLabel = new JLabel("Drop Chance Calculator");
        subtitleLabel.setFont(FontManager.getRunescapeSmallFont());
        subtitleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.add(subtitleLabel);

        headerPanel.add(Box.createVerticalStrut(8));

        // Search field
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
                        loadMonster(query);
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent e)
            {
                String query = searchField.getText().trim();
                if (query.length() >= 2 && e.getKeyCode() != KeyEvent.VK_ENTER)
                {
                    searchMonsters(query);
                }
                else if (query.isEmpty())
                {
                    searchResultsPanel.removeAll();
                    searchResultsPanel.revalidate();
                    searchResultsPanel.repaint();
                }
            }
        });
        headerPanel.add(searchField);

        // Search results dropdown
        searchResultsPanel = new JPanel();
        searchResultsPanel.setLayout(new BoxLayout(searchResultsPanel, BoxLayout.Y_AXIS));
        searchResultsPanel.setBackground(ITEM_BG_COLOR);
        searchResultsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.add(searchResultsPanel);

        add(headerPanel, BorderLayout.NORTH);

        // Main content area
        contentPanel = new JPanel();
        contentPanel.setLayout(new BorderLayout());
        contentPanel.setBackground(BACKGROUND_COLOR);

        // Monster info header (shown when monster is selected)
        JPanel monsterInfoPanel = new JPanel();
        monsterInfoPanel.setLayout(new BoxLayout(monsterInfoPanel, BoxLayout.Y_AXIS));
        monsterInfoPanel.setBackground(HEADER_COLOR);
        monsterInfoPanel.setBorder(new EmptyBorder(8, 10, 8, 10));

        monsterTitleLabel = new JLabel("Select a monster");
        monsterTitleLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));
        monsterTitleLabel.setForeground(Color.WHITE);
        monsterTitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        monsterInfoPanel.add(monsterTitleLabel);

        kcLabel = new JLabel("");
        kcLabel.setFont(FontManager.getRunescapeSmallFont());
        kcLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        kcLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        monsterInfoPanel.add(kcLabel);

        contentPanel.add(monsterInfoPanel, BorderLayout.NORTH);

        // Drops list
        dropsPanel = new JPanel();
        dropsPanel.setLayout(new BoxLayout(dropsPanel, BoxLayout.Y_AXIS));
        dropsPanel.setBackground(BACKGROUND_COLOR);

        JScrollPane scrollPane = new JScrollPane(dropsPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(BACKGROUND_COLOR);
        contentPanel.add(scrollPane, BorderLayout.CENTER);

        // Status label at bottom
        statusLabel = new JLabel("Search for a monster to begin", SwingConstants.CENTER);
        statusLabel.setFont(FontManager.getRunescapeSmallFont());
        statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        statusLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
        contentPanel.add(statusLabel, BorderLayout.SOUTH);

        add(contentPanel, BorderLayout.CENTER);
    }

    /**
     * Searches for monsters matching the query and shows results.
     */
    private void searchMonsters(String query)
    {
        new Thread(() ->
        {
            List<String> results = wikiDropFetcher.searchMonsters(query);
            SwingUtilities.invokeLater(() ->
            {
                searchResultsPanel.removeAll();

                for (String result : results)
                {
                    JButton resultButton = new JButton(result);
                    resultButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
                    resultButton.setAlignmentX(Component.LEFT_ALIGNMENT);
                    resultButton.setBackground(ITEM_BG_COLOR);
                    resultButton.setForeground(Color.WHITE);
                    resultButton.setBorderPainted(false);
                    resultButton.setFocusPainted(false);
                    resultButton.setHorizontalAlignment(SwingConstants.LEFT);
                    resultButton.setFont(FontManager.getRunescapeSmallFont());
                    resultButton.addActionListener(e ->
                    {
                        searchField.setText(result);
                        searchResultsPanel.removeAll();
                        searchResultsPanel.revalidate();
                        searchResultsPanel.repaint();
                        loadMonster(result);
                    });
                    resultButton.addMouseListener(new java.awt.event.MouseAdapter()
                    {
                        public void mouseEntered(java.awt.event.MouseEvent evt)
                        {
                            resultButton.setBackground(ITEM_BG_HOVER);
                        }

                        public void mouseExited(java.awt.event.MouseEvent evt)
                        {
                            resultButton.setBackground(ITEM_BG_COLOR);
                        }
                    });
                    searchResultsPanel.add(resultButton);
                }

                searchResultsPanel.revalidate();
                searchResultsPanel.repaint();
            });
        }).start();
    }

    /**
     * Loads drop data for a monster from the wiki and displays it.
     */
    public void loadMonster(String monsterName)
    {
        currentMonster = monsterName;
        statusLabel.setText("Loading drops for " + monsterName + "...");
        dropsPanel.removeAll();
        dropsPanel.revalidate();
        dropsPanel.repaint();

        new Thread(() ->
        {
            MonsterDropData data = wikiDropFetcher.fetchMonsterDrops(monsterName);
            SwingUtilities.invokeLater(() -> displayMonsterData(monsterName, data));
        }).start();
    }

    /**
     * Displays the drop data for a monster in the panel.
     */
    private void displayMonsterData(String monsterName, MonsterDropData data)
    {
        dropsPanel.removeAll();

        if (data == null || data.getDrops().isEmpty())
        {
            statusLabel.setText("No drop data found for " + monsterName);
            monsterTitleLabel.setText(monsterName);
            kcLabel.setText("");
            dropsPanel.revalidate();
            dropsPanel.repaint();
            return;
        }

        // Update header
        monsterTitleLabel.setText(data.getMonsterName());
        int totalKc = playerDataManager.getKillCount(monsterName);
        int kcSinceDrop = playerDataManager.getKcSinceLastDrop(monsterName);
        if (totalKc > 0)
        {
            kcLabel.setText("Total KC: " + String.format("%,d", totalKc)
                + "  |  KC since drop: " + String.format("%,d", kcSinceDrop));
        }
        else
        {
            kcLabel.setText("No KC tracked yet");
        }

        statusLabel.setText(data.getDrops().size() + " drops loaded");

        // Add drop entries
        for (DropEntry drop : data.getDrops())
        {
            if (config.showOnlyUnobtained() && playerDataManager.hasItem(drop.getItemName()))
            {
                continue;
            }

            JPanel dropPanel = createDropItemPanel(monsterName, drop);
            dropsPanel.add(dropPanel);
            dropsPanel.add(Box.createVerticalStrut(2));
        }

        dropsPanel.revalidate();
        dropsPanel.repaint();
    }

    /**
     * Creates a panel for a single drop item showing its name, rate, and chance.
     */
    private JPanel createDropItemPanel(String monsterName, DropEntry drop)
    {
        boolean obtained = playerDataManager.hasItem(drop.getItemName());
        int kc = playerDataManager.getKcSinceLastDrop(monsterName, drop.getItemName());
        double chance = DropChanceCalculator.calculateChance(drop.getDropRate(), kc);
        String chanceStr = DropChanceCalculator.formatPercent(chance);

        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBackground(ITEM_BG_COLOR);
        panel.setBorder(new EmptyBorder(6, 8, 6, 8));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 0);

        // Row 1: Item name + obtained indicator
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;

        JLabel nameLabel = new JLabel(drop.getItemName());
        nameLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        if (obtained)
        {
            nameLabel.setForeground(OBTAINED_COLOR);
            nameLabel.setText("\u2713 " + drop.getItemName());
        }
        else
        {
            nameLabel.setForeground(Color.WHITE);
        }
        panel.add(nameLabel, gbc);

        // Row 1 right: Drop rate
        if (config.showDropRate())
        {
            gbc.gridx = 1;
            gbc.weightx = 0;
            gbc.anchor = GridBagConstraints.EAST;

            JLabel rateLabel = new JLabel(DropChanceCalculator.formatDropRate(drop.getDropRate()));
            rateLabel.setFont(FontManager.getRunescapeSmallFont());
            rateLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            panel.add(rateLabel, gbc);
        }

        // Row 2: Progress bar with percentage
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(3, 0, 0, 0);

        JPanel progressPanel = new JPanel(new BorderLayout(5, 0));
        progressPanel.setOpaque(false);

        JProgressBar progressBar = new JProgressBar(0, 10000);
        progressBar.setValue((int) (chance * 10000));
        progressBar.setStringPainted(false);
        progressBar.setPreferredSize(new Dimension(0, 12));
        progressBar.setBackground(new Color(30, 30, 30));

        // Color based on chance level
        Color barColor;
        if (obtained)
        {
            barColor = OBTAINED_COLOR;
        }
        else if (chance >= 0.9)
        {
            barColor = VERY_HIGH_CHANCE_COLOR;
        }
        else if (chance >= (config.highlightThreshold() / 100.0) && config.highlightThreshold() > 0)
        {
            barColor = HIGH_CHANCE_COLOR;
        }
        else
        {
            barColor = new Color(70, 130, 230);
        }
        progressBar.setForeground(barColor);

        progressPanel.add(progressBar, BorderLayout.CENTER);

        // Chance percentage label
        JLabel chanceLabel = new JLabel(chanceStr);
        chanceLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        chanceLabel.setForeground(barColor);
        chanceLabel.setPreferredSize(new Dimension(55, 12));
        chanceLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        progressPanel.add(chanceLabel, BorderLayout.EAST);

        panel.add(progressPanel, gbc);

        // Row 3: KC info
        gbc.gridy = 2;
        gbc.insets = new Insets(2, 0, 0, 0);

        JLabel kcInfoLabel = new JLabel();
        kcInfoLabel.setFont(FontManager.getRunescapeSmallFont());
        kcInfoLabel.setForeground(LOW_CHANCE_COLOR);

        if (kc > 0)
        {
            String expected = DropChanceCalculator.expectedKills(drop.getDropRate());
            kcInfoLabel.setText(String.format("%,d kc (expected: %s)", kc, expected));
        }
        else
        {
            kcInfoLabel.setText("No kills tracked");
        }
        panel.add(kcInfoLabel, gbc);

        // Hover effect
        panel.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseEntered(java.awt.event.MouseEvent evt)
            {
                panel.setBackground(ITEM_BG_HOVER);
            }

            public void mouseExited(java.awt.event.MouseEvent evt)
            {
                panel.setBackground(ITEM_BG_COLOR);
            }
        });

        return panel;
    }

    /**
     * Refreshes the display for the current monster (e.g., after KC update).
     */
    public void refresh()
    {
        if (currentMonster != null)
        {
            loadMonster(currentMonster);
        }
    }

    /**
     * Gets the currently displayed monster name.
     */
    public String getCurrentMonster()
    {
        return currentMonster;
    }
}
