class SegTree:
    def __init__(self, size):
        self.size = size
        self.tree = [0] * (4 * size)

    def _update(self, node, start, end, idx, val):
        if start == end:
            self.tree[node] = val
            return
        mid = (start + end) // 2
        if start <= idx <= mid:
            self._update(2 * node, start, mid, idx, val)
        else:
            self._update(2 * node + 1, mid + 1, end, idx, val)
        self.tree[node] = max(self.tree[2 * node], self.tree[2 * node + 1])

    def update(self, idx, val):
        self._update(1, 0, self.size - 1, idx, val)

    def _query(self, node, start, end, l, r):
        if r < start or end < l:
            return 0
        if l <= start and end <= r:
            return self.tree[node]
        mid = (start + end) // 2
        p1 = self._query(2 * node, start, mid, l, r)
        p2 = self._query(2 * node + 1, mid + 1, end, l, r)
        return max(p1, p2)

    def query(self, l, r):
        if l > r:
            return 0
        return self._query(1, 0, self.size - 1, l, r)

    def _find_prev(self, node, start, end, r):
        if r < start or self.tree[node] == 0:
            return -1
        if start == end:
            return start
        mid = (start + end) // 2
        res = self._find_prev(2 * node + 1, mid + 1, end, r)
        if res != -1:
            return res
        return self._find_prev(2 * node, start, mid, r)

    def find_prev(self, r):
        if r < 0:
            return -1
        return self._find_prev(1, 0, self.size - 1, r)

    def _find_next(self, node, start, end, l):
        if l > end or self.tree[node] == 0:
            return -1
        if start == end:
            return start
        mid = (start + end) // 2
        res = self._find_next(2 * node, start, mid, l)
        if res != -1:
            return res
        return self._find_next(2 * node + 1, mid + 1, end, l)

    def find_next(self, l):
        if l >= self.size:
            return -1
        return self._find_next(1, 0, self.size - 1, l)

class Solution:
    def getResults(self, queries: list[list[int]]) -> list[bool]:
        # The maximum coordinate is at most 5 * 10^4.
        # We need to handle coordinates from 0 up to this max.
        M = 50001

        obs_tree = SegTree(M)
        gap_tree = SegTree(M)

        # Add a conceptual obstacle at 0.
        obs_tree.update(0, 1)

        results = []
        for q in queries:
            if q[0] == 1:
                x_new = q[1]
                
                p_prev = obs_tree.find_prev(x_new - 1)
                p_next = obs_tree.find_next(x_new + 1)
                
                # Update gaps
                if p_next != -1:
                    # The old gap p_next - p_prev is replaced by two new gaps.
                    # The new gap ending at p_next is p_next - x_new.
                    gap_tree.update(p_next, p_next - x_new)
                
                # The new gap ending at x_new is x_new - p_prev.
                gap_tree.update(x_new, x_new - p_prev)
                
                # Add the new obstacle
                obs_tree.update(x_new, 1)

            else:  # type 2
                x, sz = q[1], q[2]
                
                if sz > x:
                    results.append(False)
                    continue

                # Find the largest obstacle at or before x
                p_m = obs_tree.find_prev(x)
                
                # Check the gap from the last obstacle to x
                gap_at_end = x - p_m
                if gap_at_end >= sz:
                    results.append(True)
                    continue
                
                # Check the maximum gap between any two consecutive obstacles up to x
                max_internal_gap = gap_tree.query(1, x)
                if max_internal_gap >= sz:
                    results.append(True)
                    continue
                
                results.append(False)
        
        return results