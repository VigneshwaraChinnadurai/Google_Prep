class Solution:
    def maximumJumps(self, nums: List[int], target: int) -> int:
        n = len(nums)
        # dp[i] will store the maximum number of jumps to reach index i.
        # Initialize with -1 to indicate that an index is not reachable.
        dp = [-1] * n
        
        # Base case: We start at index 0 with 0 jumps.
        dp[0] = 0
        
        # Iterate from the second index to the last to compute dp values.
        for i in range(1, n):
            # To compute dp[i], we look at all previous indices j.
            for j in range(i):
                # A path to i from j is possible only if j is reachable (dp[j] != -1)
                # and the jump condition is met.
                # The condition -target <= nums[j] - nums[i] <= target is equivalent to
                # abs(nums[i] - nums[j]) <= target.
                if dp[j] != -1 and abs(nums[i] - nums[j]) <= target:
                    # If we can jump from j to i, the number of jumps to reach i via j
                    # is dp[j] + 1. We update dp[i] with the maximum value found so far.
                    dp[i] = max(dp[i], dp[j] + 1)
                        
        # The answer is the maximum number of jumps to reach the last index.
        return dp[n-1]