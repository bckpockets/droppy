package com.droppy;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PlayerDataManagerTest
{
    @Test
    public void repeatDropResetsOnlyThatItemsCounter()
    {
        PlayerDataManager data = new PlayerDataManager(null, new Gson());
        data.setKillCount("Zulrah", 100);
        data.recordCollectionLogItem("Magic fang", "Zulrah");
        data.recordCollectionLogItem("Tanzanite fang", "Zulrah");
        data.setKillCount("Zulrah", 110);

        data.recordCollectionLogItem("Magic fang", "Zulrah");

        assertTrue(data.hasItem("Magic fang"));
        assertEquals(110, data.getItemDropKc("Zulrah", "Magic fang"));
        assertEquals(0, data.getKcSinceLastDrop("Zulrah", "Magic fang"));
        assertEquals(10, data.getKcSinceLastDrop("Zulrah", "Tanzanite fang"));
    }

    @Test
    public void staleKillCountDoesNotMoveProgressBackwards()
    {
        PlayerDataManager data = new PlayerDataManager(null, new Gson());
        data.setKillCount("Vorkath", 100);
        data.recordCollectionLogItem("Vorkath's head", "Vorkath");
        data.setKillCount("Vorkath", 110);
        data.setKillCount("Vorkath", 105);

        assertEquals(110, data.getKillCount("Vorkath"));
        assertEquals(10, data.getKcSinceLastDrop("Vorkath", "Vorkath's head"));
    }
}
