from typing import List

class Solution:
    def maximumElementAfterDecrementingAndRearranging(self, arr: List[int]) -> int:
        """
        Calculates the maximum possible element in an array after applying
        decreasing and rearranging operations to satisfy certain conditions.
        """
        
        # The rearrangement operation allows us to reorder the array elements
        # in any way. To simplify the problem and build towards a maximal element,
        # it's best to process the numbers in a sorted order.
        arr.sort()
        
        # The first condition is that the first element of the array must be 1.
        # Since all elements in the input are positive integers, we can always
        # take the smallest element (arr[0] after sorting) and decrease it to 1.
        arr[0] = 1
        
        # The second condition is that the absolute difference between any two
        # adjacent elements must be at most 1 (i.e., abs(arr[i] - arr[i-1]) <= 1).
        # To maximize the last element (and thus the overall maximum), we should
        # make each element as large as possible while satisfying the conditions.
        #
        # After sorting and setting arr[0] = 1, we iterate through the array.
        # For each element arr[i], we know arr[i] >= arr[i-1] from the sort.
        # The condition abs(arr[i] - arr[i-1]) <= 1 simplifies to arr[i] - arr[i-1] <= 1,
        # or arr[i] <= arr[i-1] + 1.
        #
        # We can decrease arr[i], but we cannot increase it. So, the new value for
        # arr[i] is upper-bounded by its current value.
        # Combining these, the new arr[i] must be at most min(arr[i], arr[i-1] + 1).
        # To maximize the final result, we greedily choose the largest possible value.
        for i in range(1, len(arr)):
            arr[i] = min(arr[i], arr[i-1] + 1)
            
        # After this process, we have constructed a valid, non-decreasing array
        # where each element is maximized. The maximum element in this array
        # will be the last one.
        return arr[-1]