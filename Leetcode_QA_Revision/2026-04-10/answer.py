import collections
from typing import List

class Solution:
    def minimumDistance(self, nums: List[int]) -> int:
        # Step 1: Group indices by their corresponding value.
        # A hash map (defaultdict) is used where keys are the numbers
        # and values are lists of indices where those numbers appear.
        positions = collections.defaultdict(list)
        for i, num in enumerate(nums):
            positions[num].append(i)
            
        min_full_dist = float('inf')
        
        # Step 2: Iterate through each group of indices.
        for indices in positions.values():
            # A "good tuple" requires three equal elements, so we need at least
            # three occurrences of a number.
            if len(indices) < 3:
                continue
            
            # Step 3: Find the minimum distance for the current number.
            # The distance formula for three indices i, j, k simplifies.
            # If sorted as i' < j' < k', distance = 2 * (k' - i').
            # To minimize this, we must minimize the span (k' - i').
            # For a sorted list of indices, the minimum span for a triplet
            # is found by checking a sliding window of three consecutive indices.
            # The `indices` list is already sorted as it's populated sequentially.
            for i in range(len(indices) - 2):
                # The triplet of indices is (indices[i], indices[i+1], indices[i+2]).
                # Smallest index is indices[i], largest is indices[i+2].
                current_dist = 2 * (indices[i+2] - indices[i])
                min_full_dist = min(min_full_dist, current_dist)
                
        # Step 4: Return the result.
        # If min_full_dist was never updated, no good tuple was found.
        if min_full_dist == float('inf'):
            return -1
        else:
            return min_full_dist