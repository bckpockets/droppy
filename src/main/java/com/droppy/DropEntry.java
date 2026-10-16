package com.droppy;

import lombok.Value;

@Value
public class DropEntry
{
    String itemName;
    double dropRate;
    int itemId;
    // Original wiki fraction string (e.g. "3/128")
    String rarityDisplay;
    int rollsPerKill;

    public DropEntry(String itemName, double dropRate, int itemId, String rarityDisplay)
    {
        this(itemName, dropRate, itemId, rarityDisplay, 1);
    }

    public DropEntry(String itemName, double dropRate, int itemId,
                     String rarityDisplay, int rollsPerKill)
    {
        this.itemName = itemName;
        this.dropRate = dropRate;
        this.itemId = itemId;
        this.rarityDisplay = rarityDisplay;
        this.rollsPerKill = Math.max(1, rollsPerKill);
    }

    @Override
    public String toString()
    {
        return itemName + " (" + (rarityDisplay != null ? rarityDisplay : "?") + ")";
    }
}
