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
}
