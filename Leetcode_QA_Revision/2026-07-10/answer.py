import math

class Solution:
    def pathExistenceQueries(self, n: int, nums: list[int], maxDiff: int, queries: list[list[int]]) -> list[int]:
        if n == 0:
            return []

        p = sorted([(nums[i], i) for i in range(n)])
        
        pos = {original_idx: i for i, (_, original_idx) in enumerate(p)}

        j = [-1] * n
        right = 0
        for i in range(n):
            while right < n and p[right][0] - p[i][0] <= maxDiff:
                right += 1
            j[i] = right - 1

        comp_p = [-1] * n
        comp_id_counter = 0
        max_j = -1
        for i in range(n):
            if i > max_j:
                comp_id_counter += 1
            comp_p[i] = comp_id_counter
            max_j = max(max_j, j[i])
        
        comp = [-1] * n
        for i in range(n):
            comp[i] = comp_p[pos[i]]

        LOGN = n.bit_length()
        up = [[0] * LOGN for _ in range(n)]
        
        R = [-1] * n
        max_reach = -1
        for i in range(n):
            max_reach = max(max_reach, j[i])
            R[i] = max_reach
        
        for i in range(n):
            up[i][0] = R[i]

        for k in range(1, LOGN):
            for i in range(n):
                prev_reach = up[i][k-1]
                up[i][k] = up[prev_reach][k-1]

        ans = []
        for u, v in queries:
            if u == v:
                ans.append(0)
                continue

            if comp[u] != comp[v]:
                ans.append(-1)
                continue

            pos_u = pos[u]
            pos_v = pos[v]
            
            a = min(pos_u, pos_v)
            b = max(pos_u, pos_v)

            if R[a] >= b:
                ans.append(1)
                continue

            dist = 0
            curr = a
            for k in range(LOGN - 1, -1, -1):
                if up[curr][k] < b:
                    dist += (1 << k)
                    curr = up[curr][k]
            
            dist += 1
            ans.append(dist)
            
        return ans