package com.droppy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DropRateModelTest
{
    @Test
    public void raidRewardWeightIsNotTreatedAsChancePerRaid()
    {
        DropEntry fang = new DropEntry("Osmumten's fang", 7.0 / 24.0, -1, "7/24");

        assertFalse(DropRateModel.supportsKcChance("Tombs of Amascut"));
        assertFalse(DropRateModel.supportsKcChance("Chambers of Xeric: Challenge Mode"));
        assertFalse(DropRateModel.supportsKcChance("Pyramid Plunder"));
        assertFalse(DropRateModel.supportsKcChance("The Gauntlet"));
        assertFalse(DropRateModel.supportsKcChance("Reward Chest (The Gauntlet)"));
        assertFalse(DropRateModel.supportsKcChance("Zalcano"));
        assertFalse(DropRateModel.supportsKcChance("Barbarian Assault"));
        assertFalse(DropRateModel.supportsKcChance("Fishing Trawler"));
        assertFalse(DropRateModel.supportsKcChance("Clue scroll (easy)"));
        assertFalse(DropRateModel.supportsKcChance("Reward casket (easy)"));
        assertFalse(DropRateModel.supportsKcChance("Hallowed sack"));
        assertFalse(DropRateModel.supportsKcChance("Spoils of war"));
        assertFalse(DropRateModel.supportsKcChance("Gold key red"));
        assertTrue(Double.isNaN(DropRateModel.perKillRate("Tombs of Amascut", fang)));
        assertEquals("7/24 (conditional)", DropRateModel.rateLabel("Tombs of Amascut", fang));
        assertEquals("Rate varies by mode", DropRateModel.rateLabel("Chambers of Xeric: Challenge Mode", fang));
        assertEquals("Rate varies by variant", DropRateModel.rateLabel("Reward Chest (The Gauntlet)", fang));
    }

    @Test
    public void zulrahUniqueUsesBothLootRolls()
    {
        DropEntry fang = new DropEntry("Tanzanite fang", 1.0 / 1024.0, -1, "1/1,024", 2);
        double expected = 1.0 - Math.pow(1023.0 / 1024.0, 2);

        assertEquals(expected, DropRateModel.perKillRate("Zulrah", fang), 1e-15);
        assertEquals("2 × 1/1,024 per kill", DropRateModel.rateLabel("Zulrah", fang));
    }

    @Test
    public void guaranteedHeadAndHiddenVestigeDoNotUseIndependentRollChance()
    {
        DropEntry head = new DropEntry("Vorkath's head", 1.0 / 50.0, -1, "1/50");
        DropEntry vestige = new DropEntry("Ultor vestige", 1.0 / 1088.0, -1, "1/1,088");

        assertTrue(DropRateModel.supportsKcChance("Vorkath"));
        assertFalse(DropRateModel.supportsKcChance("Vorkath", head));
        assertTrue(Double.isNaN(DropRateModel.perKillRate("Vorkath", head)));
        assertEquals("1/50 (guaranteed on kill 50)", DropRateModel.rateLabel("Vorkath", head));
        assertFalse(DropRateModel.supportsKcChance("Vardorvis", vestige));
        assertTrue(Double.isNaN(DropRateModel.perKillRate("Vardorvis", vestige)));
        assertEquals("1/1,088 (three hidden rolls)", DropRateModel.rateLabel("Vardorvis", vestige));
    }

    @Test
    public void amoxliatlFrozenTearsAreGuaranteedAcrossQuantityVariants()
    {
        DropEntry tears = new DropEntry("Frozen tear", 0.6, -1, "6/10");

        assertEquals(1.0, DropRateModel.perKillRate("Amoxliatl", tears), 0);
        assertEquals("Always (quantity varies)", DropRateModel.rateLabel("Amoxliatl", tears));
    }
}
