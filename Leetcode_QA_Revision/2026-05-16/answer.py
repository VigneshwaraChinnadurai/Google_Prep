class Solution:
    def findMin(self, nums: list[int]) -> int:
        """
        Finds the minimum element in a rotated sorted array that may contain duplicates.
        """
        left, right = 0, len(nums) - 1

        while left < right:
            mid = left + (right - left) // 2

            if nums[mid] < nums[right]:
                # This means the segment from mid to right is sorted.
                # The minimum element must be in the left part, including mid.
                right = mid
            elif nums[mid] > nums[right]:
                # This means the pivot (the smallest element) is in the right part.
                # The segment from left to mid is sorted, but these values are
                # greater than nums[right], so we can discard them.
                left = mid + 1
            else:  # nums[mid] == nums[right]
                # This is the ambiguous case. We cannot determine which half
                # contains the minimum. For example, in [3, 3, 1, 3], the minimum
                # is on the right of mid. In [3, 1, 3, 3], it's on the left.
                # In this situation, we can safely discard the element at `right`
                # because its value is duplicated at `mid`, so we won't lose the
                # minimum candidate. We shrink the search space by one.
                right -= 1
        
        # The loop terminates when left == right, which points to the minimum element.
        return nums[left]