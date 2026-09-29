class Solution:
    def hasValidPath(self, grid: list[list[str]]) -> bool:
        m, n = len(grid), len(grid[0])
        total = m + n - 1  # path length
        if total % 2 == 1:
            return False  # odd length can't be valid

        # dp[r][c] = set of possible open-paren balances (counts of '(' - ')')
        # Balance must stay >= 0 at all times and end at 0.
        # Max possible balance at step k is k (all '(' so far), min is 0.

        # Use bitset/set of reachable balances per cell
        INF = total // 2 + 1

        # dp[r][c] = frozenset or bool array of reachable balances
        # Use a 2D array of sets
        dp = [[None] * n for _ in range(m)]

        # Start
        val = 1 if grid[0][0] == '(' else -1
        if val == -1:
            return False  # starts with ')', balance goes negative
        dp[0][0] = {1}

        for r in range(m):
            for c in range(n):
                if r == 0 and c == 0:
                    continue
                v = 1 if grid[r][c] == '(' else -1
                reachable = set()
                if r > 0 and dp[r-1][c] is not None:
                    for bal in dp[r-1][c]:
                        nb = bal + v
                        if nb >= 0:
                            reachable.add(nb)
                if c > 0 and dp[r][c-1] is not None:
                    for bal in dp[r][c-1]:
                        nb = bal + v
                        if nb >= 0:
                            reachable.add(nb)
                dp[r][c] = reachable if reachable else None

        return dp[m-1][n-1] is not None and 0 in dp[m-1][n-1]