class Solution:
    def minimumCost(self, cost1: int, cost2: int, costBoth: int, need1: int, need2: int) -> int:
        lo, hi = min(need1, need2), max(need1, need2)
        # Option A: no type 3 items
        a = cost1 * need1 + cost2 * need2
        # Option B: type 3 covers the smaller need, singles cover the rest
        if need1 <= need2:
            b = costBoth * lo + cost2 * (hi - lo)
        else:
            b = costBoth * lo + cost1 * (hi - lo)
        # Option C: type 3 covers the larger need on both sides
        c = costBoth * hi
        return min(a, b, c)