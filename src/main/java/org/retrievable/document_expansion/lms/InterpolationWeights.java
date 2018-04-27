package org.retrievable.document_expansion.lms;

import java.util.ArrayList;
import java.util.List;

public class InterpolationWeights {

    /**
     * Get every possible combination of linear interpolation weights, summing to 1.0, for a given number of probability
     * distributions.
     *
     * @param n The length of each combination of weights.
     * @return A list of lists, where each nested list is a unique combination of interpolation weights.
     */
    public static List<List<Double>> weights(int n) {
        // To avoid floating point errors, we get all combinations adding to 10...
        List<List<Integer>> combinations = weights(n, 10, new ArrayList<>());

        // ...but then we divide each number by 10 to get the desired range of 0-1
        List<List<Double>> probabilities = new ArrayList<>();
        for (List<Integer> combination : combinations) {
            List<Double> probs = new ArrayList<>();
            for (int x : combination) {
                probs.add(x / 10.0);
            }
            probabilities.add(probs);
        }

        return probabilities;
    }

    /**
     * Due to floating point issues, the bulk of the logic is done with integers and later converted to doubles.
     *
     * @param n The length of each combination of weights.
     * @param addTo The weight remaining. Initially this should be set to 10 (=1.0), but it will decrease with each recursive call as probability mass is used by other models.
     * @param soFar The list of weights at this point in the recursive calls. Allows us to accumulate each combination into a single list. Should initially be an empty list.
     * @return A list of lists, where each nested list is a unique combination of weights summing to 10.
     */
    private static List<List<Integer>> weights(int n, int addTo, List<Integer> soFar) {
        // Will be used to store all combinations determined at this level.
        List<List<Integer>> combinations = new ArrayList<>();

        // If n == 1, we just need to attribute any remaining weight to the final number and return the completed list.
        if (n == 1) {
            // Since we ultimately want all numbers to sum to 10, attribute whatever's left to the final number
            soFar.add(10 - sum(soFar));

            // We have to use a list of lists to maintain a consistent return type. This list will only contain a single
            // combination list.
            List<List<Integer>> container = new ArrayList<>();
            container.add(soFar);

            // This will return to the n=2 level.
            return container;
        }

        for (int x = 0; x <= addTo; x++) {
            // Create a new soFar starting from this point so that we don't end up with one massive soFar list. If we didn't
            // do this, every iteration of this loop would modify the same list instead of starting its own fresh copy.
            List<Integer> soFarFromHere = new ArrayList<>(soFar);

            // Update soFarFromHere with the current value.
            soFarFromHere.add(x);

            /*
             * Get the recursive results from one level down. At n=2, these are the individual combinations returned in the
             * n=1 block above. At each level up (n>2), this consists of all the combinations from the levels below it at
             * the current value of x for that level. So, e.g., when n=3 and x=2, collected contains the combinations where
             * the third digit (n=3) is equal to 2 (x=2).
             */
            List<List<Integer>> collected = weights(n - 1, addTo - x, soFarFromHere);

            // Store all of the combinations created at lower levels for this value of x. We will do this for all values of
            // x, at which point this for loop exits because we have iterated through all possible values at level n.
            combinations.addAll(collected);
        }

        // Return all of the combinations created starting at this point (this value of n). If we are at the top-level value
        // of n, this will return the full list of combinations. If we are at a lower level, this will be received by the
        // "collected" variable and represent all combinations for a given value (x) of n+1.
        return combinations;
    }

    // For legibility
    private static int sum(List<Integer> nums) {
        return nums.stream().mapToInt(Integer::intValue).sum();
    }

}
