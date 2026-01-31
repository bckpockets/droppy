package com.droppy;

/**
 * Calculates drop chance probabilities using binomial probability.
 *
 * The core formula is: P(at least 1 drop in N kills) = 1 - (1 - dropRate)^N
 *
 * This gives the cumulative probability of having received at least one of a given
 * item after N kills of the monster.
 */
public class DropChanceCalculator
{
    /**
     * Calculates the probability of getting at least one drop after N kills.
     *
     * @param dropRate The per-kill probability of the drop (e.g., 1/512 = 0.001953125)
     * @param killCount The number of kills without the drop
     * @return The probability as a value between 0.0 and 1.0
     */
    public static double calculateChance(double dropRate, int killCount)
    {
        if (dropRate <= 0 || killCount <= 0)
        {
            return 0.0;
        }

        if (dropRate >= 1.0)
        {
            return 1.0;
        }

        // P = 1 - (1 - r)^n
        return 1.0 - Math.pow(1.0 - dropRate, killCount);
    }

    /**
     * Formats a probability as a human-readable percentage string.
     *
     * @param probability The probability (0.0 to 1.0)
     * @return Formatted string like "85.23%"
     */
    public static String formatPercent(double probability)
    {
        if (probability <= 0)
        {
            return "0.00%";
        }
        if (probability >= 1.0)
        {
            return "99.99%";
        }

        double percent = probability * 100.0;

        if (percent < 0.01)
        {
            return "<0.01%";
        }

        return String.format("%.2f%%", percent);
    }

    /**
     * Calculates the number of kills needed to reach a given probability threshold.
     *
     * @param dropRate The per-kill probability
     * @param targetProbability The desired cumulative probability (e.g., 0.5 for 50%)
     * @return The number of kills needed, or -1 if invalid input
     */
    public static int killsForProbability(double dropRate, double targetProbability)
    {
        if (dropRate <= 0 || dropRate >= 1.0 || targetProbability <= 0 || targetProbability >= 1.0)
        {
            return -1;
        }

        // n = log(1 - P) / log(1 - r)
        return (int) Math.ceil(Math.log(1.0 - targetProbability) / Math.log(1.0 - dropRate));
    }

    /**
     * Returns the expected number of kills for a drop (1/rate).
     *
     * @param dropRate The per-kill probability
     * @return Expected kills as a string like "512 kc"
     */
    public static String expectedKills(double dropRate)
    {
        if (dropRate <= 0)
        {
            return "N/A";
        }
        return String.format("%,d kc", Math.round(1.0 / dropRate));
    }

    /**
     * Returns the drop rate formatted as a fraction string.
     *
     * @param dropRate The per-kill probability
     * @return Fraction string like "1/512"
     */
    public static String formatDropRate(double dropRate)
    {
        if (dropRate <= 0)
        {
            return "N/A";
        }
        if (dropRate >= 1.0)
        {
            return "Always";
        }

        long denominator = Math.round(1.0 / dropRate);
        return "1/" + String.format("%,d", denominator);
    }
}
