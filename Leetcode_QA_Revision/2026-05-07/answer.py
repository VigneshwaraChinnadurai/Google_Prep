import sys
from typing import List

# It's possible we might hit a recursion limit with a deep segment tree on some platforms.
# LeetCode's environment usually has a high enough limit.
# sys.setrecursionlimit(2 * 10**5)

class SegTree:
    def __init__(self, size):
        self.size = size
        # Initialize with a value that won't interfere with max operations, e.g., 0
        self.tree = [0] * (4 * size)

    def _update(self, node, start, end, idx, val):
        if start == end:
            self.tree[node] = max(self.tree[node], val)
            return
        
        mid = (start + end) // 2
        if start <= idx <= mid:
            self._update(2 * node, start, mid, idx, val)
        else:
            self._update(2 * node + 1, mid + 1, end, idx, val)
        
        self.tree[node] = max(self.tree[2 * node], self.tree[2 * node + 1])

    def update(self, idx, val):
        self._update(1, 1, self.size, idx, val)

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
        return self._query(1, 1, self.size, l, r)

class Solution:
    def maxValue(self, nums: List[int]) -> List[int]:
        n = len(nums)
        
        # Coordinate compression for nums values since they can be large
        unique_sorted_nums = sorted(list(set(nums)))
        m = len(unique_sorted_nums)
        val_to_rank = {val: i + 1 for i, val in enumerate(unique_sorted_nums)}
        
        ranks = [val_to_rank[num] for num in nums]
        
        ans = list(nums)
        
        # This problem can be modeled as finding the maximum value in a reachability graph.
        # The graph is implicit and potentially has cycles. We can solve this using a
        # fixed-point iteration approach, similar to Bellman-Ford. We repeatedly
        # update the maximum reachable value for each index until no more changes occur.
        # Each pass is optimized using a segment tree to avoid O(n^2) work.
        while True:
            old_ans = list(ans)
            
            # Forward pass:
            # A jump from i to j > i is allowed if nums[j] < nums[i].
            # This pass propagates maximums from right to left.
            # We iterate i from n-1 down to 0. For each i, we query for maximums
            # among indices j > i that have already been processed in this pass.
            st_f = SegTree(m)
            for i in range(n - 1, -1, -1):
                rank_i = ranks[i]
                # Query for max ans[j] where j > i and nums[j] < nums[i].
                # This corresponds to ranks from 1 to rank_i - 1.
                max_val = st_f.query(1, rank_i - 1)
                if max_val > ans[i]:
                    ans[i] = max_val
                
                # Update segment tree with the current ans[i] for subsequent queries
                # (from indices to the left of i).
                st_f.update(rank_i, ans[i])

            # Backward pass:
            # A jump from i to j < i is allowed if nums[j] > nums[i].
            # This pass propagates maximums from left to right.
            # We iterate i from 0 to n-1. For each i, we query for maximums
            # among indices j < i that have already been processed.
            st_b = SegTree(m)
            for i in range(n):
                rank_i = ranks[i]
                # Query for max ans[j] where j < i and nums[j] > nums[i].
                # This corresponds to ranks from rank_i + 1 to m.
                max_val = st_b.query(rank_i + 1, m)
                if max_val > ans[i]:
                    ans[i] = max_val
                
                # Update segment tree with current ans[i].
                st_b.update(rank_i, ans[i])

            if ans == old_ans:
                break
                
        return ans