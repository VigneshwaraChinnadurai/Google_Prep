import math

class Solution:
    def gcdSum(self, nums: list[int]) -> int:
        n = len(nums)
        if n <= 1:
            return 0

        # Step 1: Construct the prefixGcd array.
        prefixGcd = []
        current_max = 0
        for num in nums:
            current_max = max(current_max, num)
            prefixGcd.append(math.gcd(num, current_max))

        # Step 2: Sort the prefixGcd array.
        prefixGcd.sort()

        # Step 3: Form pairs and sum their GCDs using a two-pointer approach.
        total_sum = 0
        left, right = 0, n - 1
        while left < right:
            total_sum += math.gcd(prefixGcd[left], prefixGcd[right])
            left += 1
            right -= 1
        
        return total_sum