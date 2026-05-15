class Solution:
    def findMin(self, nums: List[int]) -> int:
        """
        Finds the minimum element in a rotated sorted array using binary search.
        """
        left, right = 0, len(nums) - 1

        # The binary search loop continues as long as the search space has more than one element.
        # The invariant is that the minimum element is always within the [left, right] window.
        while left < right:
            # Calculate the middle index.
            mid = left + (right - left) // 2

            # The core of the algorithm is to compare the middle element with the rightmost element.
            # This comparison tells us which side of the array the pivot (minimum element) is on.

            if nums[mid] > nums[right]:
                # If nums[mid] is greater than nums[right], it implies that the segment
                # from left to mid is part of the larger-valued portion of the original sorted array.
                # The "inflection point" or the minimum value must be to the right of mid.
                # Example: [4, 5, 6, 7, 0, 1, 2]. mid=3 (val 7), right=6 (val 2). 7 > 2.
                # The minimum (0) is in the range [mid+1, right].
                left = mid + 1
            else: # nums[mid] <= nums[right]
                # If nums[mid] is less than or equal to nums[right], it implies that the segment
                # from mid to right is sorted in ascending order.
                # The minimum value could be nums[mid] itself or an element to its left.
                # We can safely discard the elements to the right of mid.
                # Example: [5, 6, 1, 2, 3, 4]. mid=2 (val 1), right=5 (val 4). 1 < 4.
                # The minimum (1) is in the range [left, mid].
                right = mid
        
        # When the loop terminates, left and right pointers converge to the same index.
        # This index holds the minimum element in the array.
        return nums[left]