package com.droppy;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KillCountManagerTest
{
    @Test
    public void gauntletCompletionsUseSeparateModeCounts()
    {
        PlayerDataManager data = new PlayerDataManager(null, new Gson());
        KillCountManager manager = new KillCountManager(data, null);

        assertEquals("The Gauntlet",
            manager.handleChatMessage("Your Gauntlet completion count is: 21."));
        assertEquals("The Corrupted Gauntlet",
            manager.handleChatMessage("Your Corrupted Gauntlet completion count is: 45."));
        assertEquals(21, data.getKillCount("The Gauntlet"));
        assertEquals(45, data.getKillCount("The Corrupted Gauntlet"));
        assertEquals(0, data.getKillCount("Gauntlet completion"));
    }

    @Test
    public void chatCountsFillCollectionLogSourcesWithDifferentGameNames()
    {
        PlayerDataManager data = new PlayerDataManager(null, new Gson());
        KillCountManager manager = new KillCountManager(data, null);

        assertEquals("Barrows Chests",
            manager.handleChatMessage("Your Barrows chest count is: 1,234."));
        assertEquals(1234, manager.getKillCount("Barrows Chests"));

        assertEquals("Moons of Peril",
            manager.handleChatMessage("Your Moons of Peril completion count is: 28."));
        manager.handleLootReceived("Lunar Chest");
        assertEquals(28, manager.getKillCount("Moons of Peril"));

        assertEquals("Leviathan",
            manager.handleChatMessage("Your kill count for The Leviathan is: 17."));
        assertEquals(17, manager.getKillCount("Leviathan"));
    }

    @Test
    public void chatParsesGameCountFormsAcrossCatalogue()
    {
        PlayerDataManager data = new PlayerDataManager(null, new Gson());
        KillCountManager manager = new KillCountManager(data, null, new WikiDropFetcher(new Gson()));

        assertEquals("Alchemical Hydra",
            manager.handleChatMessage("Your Alchemical Hydra kill count is: <col=ff0000>1,234</col>."));
        assertEquals(1234, manager.getKillCount("Alchemical Hydra"));

        assertEquals("Chambers of Xeric",
            manager.handleChatMessage("Your completion count for Chambers of Xeric is: 12."));
        assertEquals(12, manager.getKillCount("Chambers of Xeric"));

        assertEquals("Guardians of the Rift",
            manager.handleChatMessage("Amount of Rifts you have closed: 25."));
        assertEquals(25, manager.getKillCount("Guardians of the Rift"));

        assertEquals("Hunter Guild",
            manager.handleChatMessage("You have completed @mes_hl_red@4</col> rumours for the Hunter Guild."));
        assertEquals(4, manager.getKillCount("Hunter Guild"));
    }

    @Test
    public void differentCountersAreNotMergedIntoCollectionLogPage()
    {
        PlayerDataManager data = new PlayerDataManager(null, new Gson());
        KillCountManager manager = new KillCountManager(data, null, new WikiDropFetcher(new Gson()));

        assertEquals("Phosani's Nightmare",
            manager.handleChatMessage("Your Phosani's Nightmare kill count is: 72."));
        assertEquals(0, manager.getKillCount("Nightmare"));

        assertEquals("Colossal Wyrm Agility Course (Advanced)",
            manager.handleChatMessage("Your Colossal Wyrm Agility Course (Advanced) lap count is: 63."));
        assertEquals(0, manager.getKillCount("Colossal Wyrm Agility"));

        assertEquals("Tombs of Amascut: Expert Mode",
            manager.handleChatMessage("Your Tombs of Amascut: Expert Mode completion count is: 8."));
        assertEquals(0, manager.getKillCount("Tombs of Amascut"));
    }
}
