package com.droppy;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("droppy")
public interface DroppyConfig extends Config
{
    @ConfigItem(
        keyName = "showOnlyUnobtained",
        name = "Show only unobtained",
        description = "Only show drops you haven't received yet",
        position = 1
    )
    default boolean showOnlyUnobtained()
    {
        return false;
    }

    @ConfigItem(
        keyName = "highlightThreshold",
        name = "Highlight threshold (%)",
        description = "Highlight drops where your chance exceeds this percentage (0 to disable)",
        position = 3
    )
    default int highlightThreshold()
    {
        return 50;
    }

    @ConfigItem(
        keyName = "trackKcFromChat",
        name = "Track KC from chat",
        description = "Automatically track kill counts from game chat messages",
        position = 4
    )
    default boolean trackKcFromChat()
    {
        return true;
    }

    @ConfigItem(
        keyName = "autoDetectCollectionLog",
        name = "Auto-detect collection log",
        description = "Automatically detect new collection log entries from chat notifications",
        position = 5
    )
    default boolean autoDetectCollectionLog()
    {
        return true;
    }
}
