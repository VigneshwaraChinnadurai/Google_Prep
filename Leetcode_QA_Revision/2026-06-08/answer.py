from typing import List

class Solution:
    def pivotArray(self, nums: List[int], pivot: int) -> List[int]:
        less = []
        equal = []
        greater = []

        # In a single pass, categorize each number from the input array
        # into one of three lists based on its value relative to the pivot.
        # Iterating from left to right and appending ensures that the
        # original relative order within each category is preserved.
        for num in nums:
            if num < pivot:
                less.append(num)
            elif num == pivot:
                equal.append(num)
            else:  # num > pivot
                greater.append(num)

        # Concatenate the three lists in the required order to form the final result.
        return less + equal + greater