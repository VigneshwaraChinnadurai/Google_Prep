class Solution:
    def maximumSaleItems(self, items: List[List[int]], budget: int) -> int:
        n = len(items)
        dp = [0] * (budget + 1)  # dp[b] = max copies with total spend at most b
        pmin = min(p for _, p in items)
        for i in range(n):
            fi, pi = items[i]
            g = 0
            for j in range(n):
                if j != i and items[j][0] % fi == 0:
                    g += 1
            val = 1 + g
            for b in range(budget, pi - 1, -1):
                cand = dp[b - pi] + val
                if cand > dp[b]:
                    dp[b] = cand
        # unlimited extra copies of the cheapest item, one copy each
        for b in range(pmin, budget + 1):
            cand = dp[b - pmin] + 1
            if cand > dp[b]:
                dp[b] = cand
        return dp[budget]