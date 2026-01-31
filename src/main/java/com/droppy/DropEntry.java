package com.droppy;

/**
 * Represents a single drop from a monster's drop table.
 */
public class DropEntry
{
    private final String itemName;
    private final double dropRate;
    private final int itemId;
    private final boolean isCollectionLog;

    public DropEntry(String itemName, double dropRate, int itemId, boolean isCollectionLog)
    {
        this.itemName = itemName;
        this.dropRate = dropRate;
        this.itemId = itemId;
        this.isCollectionLog = isCollectionLog;
    }

    public String getItemName()
    {
        return itemName;
    }

    public double getDropRate()
    {
        return dropRate;
    }

    public int getItemId()
    {
        return itemId;
    }

    public boolean isCollectionLog()
    {
        return isCollectionLog;
    }

    @Override
    public String toString()
    {
        return itemName + " (1/" + (dropRate > 0 ? Math.round(1.0 / dropRate) : "?") + ")";
    }
}
