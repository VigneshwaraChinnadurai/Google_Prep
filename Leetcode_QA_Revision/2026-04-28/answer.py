from typing import List

class Solution:
    def minOperations(self, grid: List[List[int]], x: int) -> int:
        # Step 1: Flatten the 2D grid into a 1D list.
        nums = [num for row in grid for num in row]
        
        # Step 2: Check for possibility.
        # For any two numbers 'a' and 'b' to be made equal by adding/subtracting x,
        # their difference (a - b) must be a multiple of x.
        # This is equivalent to all numbers having the same remainder when divided by x.
        remainder = nums[0] % x
        if any(num % x != remainder for num in nums):
            return -1
            
        # Step 3: Find the optimal target value.
        # The total number of operations is sum(|num - target| / x).
        # To minimize this, we must minimize sum(|num - target|).
        # This sum is minimized when the target is the median of the numbers.
        nums.sort()
        median = nums[len(nums) // 2]
        
        # Step 4: Calculate the total operations.
        # Since we've confirmed (num - median) is a multiple of x, we can use integer division.
        return sum(abs(num - median) // x for num in nums)