import collections
from typing import List

class Solution:
    def minimumDistance(self, nums: List[int]) -> int:
        # Create a hash map to store the indices for each number.
        # The key is the number, and the value is a list of its indices.
        positions = collections.defaultdict(list)
        for i, num in enumerate(nums):
            positions[num].append(i)

        min_dist = float('inf')

        # Iterate through each number that appeared in the array.
        for num in positions:
            indices = positions[num]
            
            # A "good tuple" requires at least 3 equal elements.
            if len(indices) < 3:
                continue
            
            # The `indices` list is already sorted because we iterate through `nums`
            # in order. We use a sliding window of size 3 to find the minimum span.
            # The distance formula simplifies to 2 * (max_index - min_index) for a triplet.
            # To minimize this, we check consecutive triplets of indices.
            for i in range(len(indices) - 2):
                # For the triplet of indices (indices[i], indices[i+1], indices[i+2]),
                # the smallest is indices[i] and the largest is indices[i+2].
                dist = 2 * (indices[i+2] - indices[i])
                min_dist = min(min_dist, dist)

        # If min_dist was never updated, no good tuple was found.
        return -1 if min_dist == float('inf') else min_dist