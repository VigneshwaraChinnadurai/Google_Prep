from typing import List

class Solution:
    def minMirrorPairDistance(self, nums: List[int]) -> int:
        """
        Finds the minimum absolute distance between indices of any mirror pair.
        A mirror pair (i, j) with i < j satisfies reverse(nums[i]) == nums[j].
        """

        def reverse_integer(n: int) -> int:
            """
            Reverses the digits of an integer. Leading zeros are dropped.
            Example: reverse_integer(120) returns 21.
            """
            return int(str(n)[::-1])

        # This dictionary stores the reversed value of a number seen so far
        # and the most recent index at which its original form appeared.
        # The format is {reverse(nums[i]): i}
        reversed_num_to_index = {}
        
        min_dist = float('inf')

        # We iterate through the array with index j. For each nums[j],
        # we look for a previous index i (i < j) such that
        # reverse(nums[i]) == nums[j].
        for j, current_num in enumerate(nums):
            # Check if current_num is a reversed version of a number we've seen before.
            # If `current_num` is a key in our map, it means there exists an index `i`
            # where `reverse(nums[i]) == current_num`.
            if current_num in reversed_num_to_index:
                i = reversed_num_to_index[current_num]
                # We have found a mirror pair (i, j).
                # Calculate the distance and update the minimum.
                distance = j - i
                min_dist = min(min_dist, distance)

            # After checking, we update the map with the information from the current element.
            # We calculate the reverse of the current number and store its index `j`.
            # If this reversed number is already a key, we update its index to `j`.
            # This is crucial because we want the largest possible `i` for any future
            # match `k`, to minimize the distance `k - i`.
            reversed_current = reverse_integer(current_num)
            reversed_num_to_index[reversed_current] = j

        # If min_dist was never updated, no mirror pair was found.
        return min_dist if min_dist != float('inf') else -1