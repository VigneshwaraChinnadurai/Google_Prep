class Solution:
    def maxRotateFunction(self, nums: list[int]) -> int:
        n = len(nums)
        if n <= 1:
            return 0

        # Calculate the sum of array elements and F(0)
        s = sum(nums)
        f_current = sum(i * num for i, num in enumerate(nums))
        
        max_val = f_current

        # Iterate from k = 1 to n-1 to find F(1), F(2), ..., F(n-1)
        # using the recurrence relation F(k) = F(k-1) + S - n * nums[n-k]
        for k in range(1, n):
            f_current += s - n * nums[n - k]
            max_val = max(max_val, f_current)

        return max_val