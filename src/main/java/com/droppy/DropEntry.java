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

    @Override
    public String toString()
    {
        return itemName + " (" + (rarityDisplay != null ? rarityDisplay : "?") + ")";
    }
}
