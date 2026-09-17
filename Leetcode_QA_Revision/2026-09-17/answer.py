class Solution:
    def minSumOfLengths(self, arr: list[int], target: int) -> int:
        n = len(arr)
        INF = float('inf')

        # best[i] = length of shortest valid subarray ending at or before index i
        best = [INF] * n

        # Sliding window to find all subarrays with sum == target
        # Since arr[i] >= 1, window sum is monotone as we expand/contract
        prefix_sum = {0: -1}  # prefix_sum[s] = last index where prefix sum == s
        curr_sum = 0
        ans = INF
        min_len_so_far = INF

        for i in range(n):
            curr_sum += arr[i]
            # Check if there's a subarray ending at i with sum == target
            if curr_sum - target in prefix_sum:
                start = prefix_sum[curr_sum - target] + 1
                length = i - start + 1
                # Check if there's a best subarray entirely before 'start'
                if start > 0 and best[start - 1] != INF:
                    ans = min(ans, best[start - 1] + length)
                min_len_so_far = min(min_len_so_far, length)
            best[i] = min_len_so_far
            prefix_sum[curr_sum] = i

        return ans if ans != INF else -1