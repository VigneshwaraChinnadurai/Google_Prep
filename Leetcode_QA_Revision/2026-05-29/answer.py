class Solution:
    def minElement(self, nums: List[int]) -> int:
        
        def get_digit_sum(n: int) -> int:
            """Helper function to calculate the sum of digits of a positive integer."""
            s = 0
            # Since constraints are 1 <= nums[i], n will always be > 0 initially.
            while n > 0:
                s += n % 10
                n //= 10
            return s
            
        # Use a generator expression to compute the digit sum for each number
        # and then find the minimum of these sums. This is memory-efficient.
        return min(get_digit_sum(num) for num in nums)