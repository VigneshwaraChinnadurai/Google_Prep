class Solution:
    def countNonDecreasingSubarrays(self, nums: List[int], k: int) -> int:
        from collections import deque
        n = len(nums)
        pre = [0] * (n + 1)
        for i in range(n):
            pre[i + 1] = pre[i] + nums[i]
        dq = deque()  # segments [value, start, end], front = leftmost
        sumv = 0      # sum of value * length over all segments in the window
        r = n - 1
        ans = 0
        for l in range(n - 1, -1, -1):
            v = nums[l]
            end = l
            while dq and dq[0][0] <= v:
                seg = dq.popleft()
                sumv -= seg[0] * (seg[2] - seg[1] + 1)
                end = seg[2]
            dq.appendleft([v, l, end])
            sumv += v * (end - l + 1)
            while sumv - (pre[r + 1] - pre[l]) > k:
                seg = dq[-1]
                sumv -= seg[0]
                seg[2] -= 1
                if seg[2] < seg[1]:
                    dq.pop()
                r -= 1
            ans += r - l + 1
        return ans