class Solution:
    def maxSelectedElements(self, nums: List[int]) -> int:
        nums = sorted(nums)
        # dp maps a final value to the longest consecutive run ending at that value
        dp = {}
        best = 1
        for x in nums:
            # process x+1 first so the same element is not used twice
            dp[x + 1] = dp.get(x, 0) + 1
            dp[x] = dp.get(x - 1, 0) + 1
            best = max(best, dp[x], dp[x + 1])
        return best