class Solution:
    def maximumProduct(self, nums: List[int]) -> int:
        # Sort the array to easily find the smallest and largest elements.
        nums.sort()
        n = len(nums)
        
        # The maximum product can be one of two candidates:
        # 1. The product of the three largest numbers.
        #    This handles cases with all positive numbers or cases where the
        #    product of two smallest negatives isn't large enough.
        #    e.g., [-3, -2, -1, 5, 6] -> 6*5*(-1) = -30
        #    e.g., [1, 2, 3, 4] -> 4*3*2 = 24
        candidate1 = nums[n-1] * nums[n-2] * nums[n-3]
        
        # 2. The product of the two smallest (most negative) numbers and the largest number.
        #    This handles cases where two large negative numbers multiply to a large
        #    positive number.
        #    e.g., [-100, -50, 1, 2, 3] -> (-100)*(-50)*3 = 15000
        candidate2 = nums[0] * nums[1] * nums[n-1]
        
        return max(candidate1, candidate2)