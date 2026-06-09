class Solution:
    def maxTotalValue(self, nums: List[int], k: int) -> int:
        # The constraints state 1 <= nums.length, so nums is never empty.
        # This means max() and min() will not be called on an empty sequence.
        
        max_val = max(nums)
        min_val = min(nums)
        
        # The maximum value for a single subarray is the difference between the
        # global maximum and global minimum of the entire array.
        max_single_subarray_value = max_val - min_val
        
        # Since we can choose the same subarray multiple times, the optimal
        # strategy is to pick the subarray with the maximum possible value
        # k times.
        return k * max_single_subarray_value