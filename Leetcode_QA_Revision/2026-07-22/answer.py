import math

class SegTree:
    def __init__(self, size, op, identity):
        self.size = size
        self.op = op
        self.identity = identity
        self.tree = [identity] * (2 * size)

    def update(self, idx, val):
        idx += self.size
        self.tree[idx] = self.op(self.tree[idx], val)
        while idx > 1:
            idx //= 2
            self.tree[idx] = self.op(self.tree[2 * idx], self.tree[2 * idx + 1])

    def query(self, l, r): # range [l, r)
        res = self.identity
        l += self.size
        r += self.size
        while l < r:
            if l % 2 == 1:
                res = self.op(res, self.tree[l])
                l += 1
            if r % 2 == 1:
                r -= 1
                res = self.op(res, self.tree[r])
            l //= 2
            r //= 2
        return res

class Solution:
    def maxActiveSectionsAfterTrade(self, s: str, queries: list[list[int]]) -> list[int]:
        n = len(s)
        q = len(queries)
        
        total_ones_in_s = s.count('1')

        p1 = [0] * (n + 1)
        for i in range(n):
            p1[i+1] = p1[i] + (1 if s[i] == '1' else 0)

        run0_r = [0] * (n + 1)
        for i in range(n - 1, -1, -1):
            if s[i] == '0':
                run0_r[i] = 1 + run0_r[i+1]
        
        run0_l = [0] * (n + 1)
        for i in range(n):
            if s[i] == '0':
                run0_l[i+1] = 1 + run0_l[i]

        run1_r = [0] * (n + 1)
        for i in range(n - 1, -1, -1):
            if s[i] == '1':
                run1_r[i] = 1 + run1_r[i+1]

        sell_blocks = []
        buy_blocks = []
        gain2_blocks = []

        i = 0
        while i < n:
            char = s[i]
            if char == '1':
                length = run1_r[i]
                start, end = i, i + length - 1
                if start > 0 and end < n - 1 and s[start-1] == '0' and s[end+1] == '0':
                    sell_blocks.append((end + 1, start - 1, length))
                    
                    start_m = start - run0_l[start]
                    end_m = end + run0_r[end+1]
                    
                    left_ok = (start_m == 0 or s[start_m-1] == '1')
                    right_ok = (end_m == n-1 or s[end_m+1] == '1')
                    
                    if left_ok and right_ok:
                        gain = run0_l[start] + run0_r[end+1]
                        gain2_blocks.append((end_m, start_m, gain))
                i += length
            else: # char == '0'
                length = run0_r[i]
                start, end = i, i + length - 1
                if start > 0 and end < n - 1 and s[start-1] == '1' and s[end+1] == '1':
                    buy_blocks.append((end + 1, start - 1, length))
                i += length

        queries_by_r = [[] for _ in range(n)]
        for i, (l, r) in enumerate(queries):
            queries_by_r[r].append((l, i))

        sell_blocks_by_end = [[] for _ in range(n)]
        for end_p, start_p, length in sell_blocks:
            sell_blocks_by_end[end_p].append((start_p, length))

        buy_blocks_by_end = [[] for _ in range(n)]
        for end_p, start_p, length in buy_blocks:
            buy_blocks_by_end[end_p].append((start_p, length))
            
        gain2_blocks_by_end = [[] for _ in range(n)]
        for end_m, start_m, gain in gain2_blocks:
            gain2_blocks_by_end[end_m].append((start_m, gain))

        min_l1_res = [math.inf] * q
        max_l0_internal_res = [0] * q
        gain2_res = [0] * q

        st_min_l1 = SegTree(n, min, math.inf)
        st_max_l0 = SegTree(n, max, 0)
        st_gain2 = SegTree(n, max, 0)

        for r in range(n):
            for start_p, length in sell_blocks_by_end[r]:
                st_min_l1.update(start_p, length)
            for start_p, length in buy_blocks_by_end[r]:
                st_max_l0.update(start_p, length)
            for start_m, gain in gain2_blocks_by_end[r]:
                st_gain2.update(start_m, gain)

            for l, q_idx in queries_by_r[r]:
                min_l1_res[q_idx] = st_min_l1.query(l, r + 1)
                max_l0_internal_res[q_idx] = st_max_l0.query(l, r + 1)
                gain2_res[q_idx] = st_gain2.query(l, r + 1)

        ans = [0] * q
        for i in range(q):
            l, r = queries[i]
            
            min_l1 = min_l1_res[i]
            if min_l1 == math.inf:
                ans[i] = total_ones_in_s
                continue

            max_l0_internal = max_l0_internal_res[i]
            
            max_l0_boundary = 0
            if p1[r+1] - p1[l] == 0:
                max_l0_boundary = r - l + 1
            else:
                if s[l] == '0':
                    pref0_len = run0_r[l]
                    if l + pref0_len <= r:
                        max_l0_boundary = max(max_l0_boundary, pref0_len)
                if s[r] == '0':
                    suff0_len = run0_l[r+1]
                    if r - suff0_len >= l:
                        max_l0_boundary = max(max_l0_boundary, suff0_len)

            max_l0 = max(max_l0_internal, max_l0_boundary)
            gain1 = max(0, max_l0 - min_l1)

            gain2 = gain2_res[i]

            max_gain = max(gain1, gain2)
            ans[i] = total_ones_in_s + max_gain
            
        return ans