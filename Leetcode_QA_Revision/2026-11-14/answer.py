class Solution:
    def minimumSumSubarray(self, nums: List[int], l: int, r: int) -> int:
        n = len(nums)
        pre = [0] * (n + 1)
        for i in range(n):
            pre[i + 1] = pre[i] + nums[i]
        best = float('inf')
        for i in range(n):
            for length in range(l, r + 1):
                j = i + length
                if j > n:
                    break
                s = pre[j] - pre[i]
                if 0 < s < best:
                    best = s
        return -1 if best == float('inf') else best