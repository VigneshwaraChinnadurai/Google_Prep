class Solution:
    def maximumSaleItems(self, items: List[List[int]], budget: int) -> int:
        n = len(items)
        cnt = [0] * (n + 1)
        for f, _ in items:
            cnt[f] += 1
        mult = [0] * (n + 1)
        for f in range(1, n + 1):
            s = 0
            for m in range(f, n + 1, f):
                s += cnt[m]
            mult[f] = s
        pmin = min(p for _, p in items)
        order = sorted(range(n), key=lambda i: items[i][1])
        r = budget
        units = 0
        for i in order:
            f, p = items[i]
            if p > 2 * pmin:
                break
            g = mult[f] - 1
            if g <= 0:
                continue
            t = min(g, r // p)
            r -= t * p
            units += t
            if t < g:
                break
        return 2 * units + r // pmin