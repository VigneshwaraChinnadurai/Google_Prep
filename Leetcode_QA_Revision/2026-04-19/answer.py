from typing import List

class Solution:
    def maxDistance(self, nums1: List[int], nums2: List[int]) -> int:
        """
        Finds the maximum distance between a valid pair of indices (i, j).

        A pair (i, j) is valid if i <= j and nums1[i] <= nums2[j].
        The distance is j - i.
        """
        i, j = 0, 0
        n1, n2 = len(nums1), len(nums2)
        max_dist = 0

        while i < n1 and j < n2:
            # We are looking for a pair (i, j) such that i <= j and nums1[i] <= nums2[j].
            
            if nums1[i] <= nums2[j]:
                # This is a potential valid pair. The condition i <= j might not hold
                # if the 'else' block caused i to increment past j.
                # However, if i > j, the distance j - i will be negative.
                # Since max_dist is initialized to 0 and we only take the max,
                # these invalid pairs (where i > j) will not affect the result.
                # A positive distance is only recorded when j > i, which satisfies i <= j.
                max_dist = max(max_dist, j - i)
                
                # For the current i, we have found a valid j. To maximize the distance j - i,
                # we should try to find an even larger j. So, we advance the j pointer.
                j += 1
            else: # nums1[i] > nums2[j]
                # The value at nums1[i] is too large for nums2[j].
                # Since nums2 is a non-increasing array, nums2[k] will also be too small
                # for any k > j.
                # Therefore, we must advance the i pointer to find a smaller value in nums1.
                i += 1
        
        return max_dist