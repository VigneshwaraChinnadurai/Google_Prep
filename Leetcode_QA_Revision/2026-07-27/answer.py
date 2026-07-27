class Solution:
    def maxProduct(self, nums: list[int]) -> int:
        """
        Finds the maximum product of (nums[i]-1)*(nums[j]-1) for two different indices i and j.

        The problem is equivalent to finding the two largest elements in the array,
        as the function (x-1) is monotonically increasing for x >= 1, and all numbers
        in the input array are >= 1.
        Let the two largest elements be max1 and max2. The result will be (max1-1)*(max2-1).

        This can be solved efficiently in a single pass through the array.
        We maintain two variables, `largest` and `second_largest`, and update them
        as we iterate through the numbers.
        """
        
        # Initialize largest and second_largest.
        # Since constraints are 1 <= nums[i], initializing with a value
        # smaller than 1 (e.g., 0) is safe.
        largest = 0
        second_largest = 0
        
        for num in nums:
            if num >= largest:
                # The current number is the new largest.
                # The old largest becomes the second largest.
                second_largest = largest
                largest = num
            elif num > second_largest:
                # The current number is not the largest, but it's larger
                # than the current second largest.
                second_largest = num
                
        return (largest - 1) * (second_largest - 1)