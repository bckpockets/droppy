package com.droppy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DropChanceCalculatorTest
{
    @Test
    public void extremelyRareChanceDoesNotRoundToZero()
    {
        assertEquals(1e-15, DropChanceCalculator.calculateChance(1e-18, 1000), 1e-27);
    }

    @Test
    public void nearCertainChanceIsNotDisplayedAsCertain()
    {
        assertEquals(">99.99%", DropChanceCalculator.formatPercent(0.99999));
    }
}
