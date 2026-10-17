package com.droppy;

import java.util.Locale;
import java.util.Set;

/**
 * Relates a wiki table rate to the KC counted by Dry Drop Tracker. A reward-table weight
 * is not a probability per completion unless the preceding rolls are known.
 */
public final class DropRateModel
{
    private static final Set<String> CONDITIONAL_SOURCES = Set.of(
        "barrows chests",
        "barbarian assault",
        "branda the fire queen",
        "callisto and artio",
        "chambers of xeric",
        "colosseum",
        "coffin (hallowed sepulchre)",
        "creature creation",
        "dagannoth kings",
        "eldric the ice king",
        "fishing trawler",
        "grand gold chest",
        "hallowed sepulchre",
        "nex",
        "nightmare",
        "moons of peril",
        "phosani's nightmare",
        "pyramid plunder",
        "royal titans",
        "shades of mort'ton",
        "shared treasure trail rewards",
        "slayer",
        "soul wars",
        "sol heredit",
        "the gauntlet",
        "the nightmare",
        "theatre of blood",
        "tombs of amascut",
        "tzhaar",
        "venenatis and spindel",
        "vet'ion and calvar'ion",
        "wintertodt",
        "zalcano"
    );

    private DropRateModel()
    {
    }

    public static boolean supportsKcChance(String source)
    {
        if (source == null)
        {
            return false;
        }

        String normalized = source.toLowerCase(Locale.ROOT).trim();
        return !CONDITIONAL_SOURCES.contains(normalized)
            && !normalized.contains("treasure trails")
            && !normalized.startsWith("clue scroll (")
            && !normalized.startsWith("reward casket (")
            && !normalized.contains("wintertodt")
            && !normalized.equals("hallowed sack")
            && !normalized.equals("spoils of war")
            && !normalized.startsWith("sarcophagus (pyramid plunder)")
            && !normalized.startsWith("gold key ")
            && !normalized.startsWith("chambers of xeric")
            && !normalized.startsWith("theatre of blood")
            && !normalized.startsWith("tombs of amascut")
            && !normalized.equals("barrows")
            && !normalized.startsWith("barrows chest")
            && !normalized.startsWith("lunar chest")
            && !normalized.contains("gauntlet");
    }

    public static double perKillRate(String source, DropEntry drop)
    {
        if (!supportsKcChance(source, drop))
        {
            return Double.NaN;
        }

        if (isAmoxliatlFrozenTear(source, drop))
        {
            return 1.0;
        }

        double perRollRate = drop.getDropRate();
        if (drop.getRollsPerKill() > 1 && perRollRate > 0 && perRollRate < 1)
        {
            return -Math.expm1(drop.getRollsPerKill() * Math.log1p(-perRollRate));
        }
        return perRollRate;
    }

    public static boolean supportsKcChance(String source, DropEntry drop)
    {
        if (!supportsKcChance(source) || drop == null
            || drop.getDropRate() <= 0 || drop.getDropRate() > 1)
        {
            return false;
        }

        String item = drop.getItemName().toLowerCase(Locale.ROOT);
        String normalized = source.toLowerCase(Locale.ROOT).trim();
        // The 50th Vorkath head is guaranteed. Vestiges require three hidden
        // progress rolls, so a single independent-roll formula is misleading.
        return !(normalized.equals("vorkath") && item.equals("vorkath's head"))
            && !((normalized.equals("vardorvis") || normalized.equals("duke sucellus")
                || normalized.equals("the leviathan") || normalized.equals("leviathan")
                || normalized.equals("the whisperer") || normalized.equals("whisperer"))
                && item.endsWith(" vestige"));
    }

    public static String rateLabel(String source, DropEntry drop)
    {
        if (drop.getDropRate() <= 0)
        {
            return "Rate unavailable";
        }
        if (isAmoxliatlFrozenTear(source, drop))
        {
            return "Always (quantity varies)";
        }

        if (source != null)
        {
            String normalized = source.toLowerCase(Locale.ROOT);
            if (normalized.contains("challenge mode")
                || normalized.contains("hard mode")
                || normalized.contains("expert mode"))
            {
                return "Rate varies by mode";
            }
            if (normalized.equals("phosani's nightmare")
                || normalized.equals("reward chest (the gauntlet)"))
            {
                return "Rate varies by variant";
            }
        }

        String rate = drop.getRarityDisplay() != null
            ? drop.getRarityDisplay()
            : DropChanceCalculator.formatDropRate(drop.getDropRate());

        if (source != null && source.equalsIgnoreCase("Vorkath")
            && drop.getItemName().equalsIgnoreCase("Vorkath's head"))
        {
            return rate + " (guaranteed on kill 50)";
        }
        if (drop.getItemName().toLowerCase(Locale.ROOT).endsWith(" vestige")
            && !supportsKcChance(source, drop))
        {
            return rate + " (three hidden rolls)";
        }
        if (source != null && source.toLowerCase(Locale.ROOT).contains("gauntlet"))
        {
            return rate + " per completed run";
        }
        if (supportsKcChance(source, drop) && drop.getRollsPerKill() > 1)
        {
            return drop.getRollsPerKill() + " × " + rate + " per kill";
        }
        if (!supportsKcChance(source, drop))
        {
            return rate + " (conditional)";
        }
        return rate;
    }

    private static boolean isAmoxliatlFrozenTear(String source, DropEntry drop)
    {
        // The wiki lists three mutually exclusive quantities totaling 10/10.
        return source != null && source.equalsIgnoreCase("Amoxliatl")
            && drop.getItemName().equalsIgnoreCase("Frozen tear");
    }

}
