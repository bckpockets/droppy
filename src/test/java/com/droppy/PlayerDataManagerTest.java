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

    @Test
    public void legacyLootSourceHistoryMovesToCollectionLogName()
    {
        PlayerDataManager data = new PlayerDataManager(null, new Gson());
        data.setKillCount("Lunar Chest", 40);
        data.recordCollectionLogItem("Blood moon helm", "Lunar Chest");
        data.setKillCount("Lunar Chest", 45);

        assertTrue(data.migrateLegacySourceHistory());
        assertEquals(45, data.getKillCount("Moons of Peril"));
        assertEquals(40, data.getItemDropKc("Moons of Peril", "Blood moon helm"));
        assertEquals(5, data.getKcSinceLastDrop("Moons of Peril", "Blood moon helm"));
        assertEquals(0, data.getKillCount("Lunar Chest"));
    }
}
