package daomephsta.fabriclipse.mixin;

import java.util.Objects;

import daomephsta.fabriclipse.Fabriclipse;

public class Quantifier
{
    private final int lowerBound,
                      upperBound,
                      minimumMatches;
    private int index = 1; //1-indexed, not 0-indexed
    private int matches = 0;

    private Quantifier(int lowerOrdinal, int upperOrdinal, int minimumMatches)
    {
        this.lowerBound = lowerOrdinal;
        this.upperBound = upperOrdinal;
        this.minimumMatches = minimumMatches;
    }

    private Quantifier(int lowerOrdinal, int upperOrdinal)
    {
        this(lowerOrdinal, upperOrdinal, 0);
    }

    static Quantifier parse(String quantifier)
    {
        if (quantifier == null) // Match first
            return new Quantifier(1, 1);
        else if (quantifier.equals("*")) // Match all
            return new Quantifier(1, Integer.MAX_VALUE);
        else if (quantifier.equals("+")) // Match all, error if < 1 match
            return new Quantifier(1, Integer.MAX_VALUE, 1);
        else if (quantifier.startsWith("{"))
        {
            String[] bounds = quantifier.substring(1, quantifier.length() - 1).split(",");
            if (bounds.length == 1)
            {
                // Match exact index
                int exact = Integer.parseInt(bounds[0]);
                return new Quantifier(exact, exact);
            }
            // Match exact lowerBound <= index <= upperBound
            int lowerOrdinal = bounds[0].isEmpty() ? 1 : Integer.parseInt(bounds[0]);
            int upperOrdinal = bounds[1].isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(bounds[1]);
            return new Quantifier(lowerOrdinal, upperOrdinal, lowerOrdinal);
        }
        else
            throw new IllegalArgumentException("Invalid quantifier " + quantifier);
    }

    public boolean matches()
    {
        if (lowerBound <= index && index <= upperBound)
        {
            matches += 1;
            index += 1;
            return true;
        }
        index += 1;
        return false;
    }

    public void assertSatisfied(String context)
    {
        if (matches < minimumMatches)
        {
            Fabriclipse.LOGGER.error("matches (" + matches +
                ") < minimumMatches (" + minimumMatches + "): " + context);
        }
        else if (matches == 0)
            Fabriclipse.LOGGER.warn("No matches: " + context);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(lowerBound, minimumMatches, upperBound);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) return true;
        if (!(obj instanceof Quantifier other)) return false;
        return lowerBound == other.lowerBound && minimumMatches == other.minimumMatches && upperBound == other.upperBound;
    }
}