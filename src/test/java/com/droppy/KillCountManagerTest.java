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
}
