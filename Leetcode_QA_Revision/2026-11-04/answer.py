class Solution:
    def minOperations(self, nums: List[int], x: int, k: int) -> int:
        n = len(nums)
        vals = sorted(set(nums))
        m = len(vals)
        rank = {v: i + 1 for i, v in enumerate(vals)}
        cnt = [0] * (m + 1)
        sm = [0] * (m + 1)

        def upd(i, dc, ds):
            while i <= m:
                cnt[i] += dc
                sm[i] += ds
                i += i & -i

        top = 1
        while top * 2 <= m:
            top *= 2

        half = (x + 1) // 2
        nw = n - x + 1
        cost = [0] * nw
        total = 0
        for i in range(n):
            upd(rank[nums[i]], 1, nums[i])
            total += nums[i]
            if i >= x:
                upd(rank[nums[i - x]], -1, -nums[i - x])
                total -= nums[i - x]
            if i >= x - 1:
                rem = half
                pos = 0
                acc = 0
                pw = top
                while pw:
                    nxt = pos + pw
                    if nxt <= m and cnt[nxt] < rem:
                        pos = nxt
                        rem -= cnt[nxt]
                        acc += sm[nxt]
                    pw >>= 1
                med = vals[pos]  # index pos (0-based) == 1-based pos+1
                s_left = acc + rem * med
                s_right = total - s_left
                cost[i - x + 1] = (med * half - s_left) + (s_right - med * (x - half))

        INF = float('inf')
        prev = [0] * (n + 1)
        for j in range(1, k + 1):
            cur = [INF] * (n + 1)
            for i in range(j * x, n + 1):
                best = cur[i - 1]
                c = prev[i - x] + cost[i - x]
                if c < best:
                    best = c
                cur[i] = best
            prev = cur
        return prev[n]