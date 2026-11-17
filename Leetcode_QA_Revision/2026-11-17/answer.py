class Solution:
    def minimumChanges(self, s: str, k: int) -> int:
        n = len(s)
        INF = float('inf')
        divs = [[] for _ in range(n + 1)]
        for L in range(2, n + 1):
            divs[L] = [d for d in range(1, L) if L % d == 0]
        cost = [[INF] * n for _ in range(n)]
        for i in range(n):
            for j in range(i + 1, n):
                L = j - i + 1
                best = INF
                for d in divs[L]:
                    m = L // d
                    c = 0
                    for r in range(d):
                        base = i + r
                        for t in range(m // 2):
                            if s[base + t * d] != s[base + (m - 1 - t) * d]:
                                c += 1
                    if c < best:
                        best = c
                cost[i][j] = best
        prev = [INF] * (n + 1)
        prev[0] = 0
        for p in range(1, k + 1):
            cur = [INF] * (n + 1)
            for j in range(p, n + 1):
                b = INF
                for i in range(p - 1, j):
                    v = prev[i] + cost[i][j - 1]
                    if v < b:
                        b = v
                cur[j] = b
            prev = cur
        return prev[n]