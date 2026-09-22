from typing import List

class Solution:
    def resultArray(self, nums: List[int], k: int, queries: List[List[int]]) -> List[int]:
        n = len(nums)

        # Segment tree where each leaf i stores nums[i] % k.
        # Each node stores cnt[r] = number of suffixes starting within this node's
        # range whose prefix-product (from that start to the right boundary of the
        # full array) has remainder r mod k.
        #
        # Wait — we need: for a query (start, x), count suffixes nums[start..j]
        # (for j = start..n-1) such that product(nums[start..j]) % k == x.
        #
        # Reformulate: we fix the left boundary at `start`, and vary the right
        # boundary j from start to n-1. We want count of j where
        # product(nums[start..j]) % k == x.
        #
        # Key observation: product(nums[start..j]) = product(nums[0..j]) / product(nums[0..start-1])
        # But division mod k doesn't work simply since k may not be prime.
        #
        # Alternative: segment tree where each node covers [l, r].
        # Node stores cnt[k] array: for each remainder rem, how many "right endpoints"
        # j in [l,r] have product(nums[l..j]) % k == rem.
        # Also store prod[l..r] % k for range product.
        #
        # Merge: to merge left child [l,m] and right child [m+1,r]:
        #   - Right child contributes its cnt directly (products starting at m+1).
        #   - Left child's products extend into the right: product(l..j) for j in [m+1,r]
        #     = product(l..m) * product(m+1..j). So left child's total product
        #     (product(l..m)) multiplied by each rem in right child's cnt:
        #     for each rem in right.cnt: merged.cnt[(left.prod * rem) % k] += right.cnt[rem]
        #   - Left child's own cnt for j in [l,m]: merged.cnt[rem] += left.cnt[rem]
        #
        # For query (start, x): query the segment tree on [start, n-1] for cnt[x].
        # For update (index, value): point update on index.

        # Segment tree using arrays
        # Each node: prod (int), cnt (list of k ints)

        size = 1
        while size < n:
            size <<= 1

        # Store as flat arrays
        tree_prod = [1] * (2 * size)
        tree_cnt = [[0] * k for _ in range(2 * size)]

        def build(pos, val):
            # Set leaf
            i = size + pos
            v = val % k
            tree_prod[i] = v
            tree_cnt[i] = [0] * k
            tree_cnt[i][v] = 1
            i >>= 1
            while i >= 1:
                l, r = 2 * i, 2 * i + 1
                lp, rp = tree_prod[l], tree_prod[r]
                tree_prod[i] = (lp * rp) % k
                new_cnt = [0] * k
                # left child's j in left range
                for rem in range(k):
                    if tree_cnt[l][rem]:
                        new_cnt[rem] += tree_cnt[l][rem]
                # right child's j in right range: product(l..m) * product(m+1..j)
                for rem in range(k):
                    if tree_cnt[r][rem]:
                        new_cnt[(lp * rem) % k] += tree_cnt[r][rem]
                tree_cnt[i] = new_cnt
                i >>= 1

        # Initialize
        for i in range(n):
            leaf = size + i
            v = nums[i] % k
            tree_prod[leaf] = v
            tree_cnt[leaf] = [0] * k
            tree_cnt[leaf][v] = 1
        # Build internal nodes bottom-up
        for i in range(size - 1, 0, -1):
            l, r = 2 * i, 2 * i + 1
            lp = tree_prod[l]
            rp = tree_prod[r]
            tree_prod[i] = (lp * rp) % k
            new_cnt = [0] * k
            for rem in range(k):
                if tree_cnt[l][rem]:
                    new_cnt[rem] += tree_cnt[l][rem]
            for rem in range(k):
                if tree_cnt[r][rem]:
                    new_cnt[(lp * rem) % k] += tree_cnt[r][rem]
            tree_cnt[i] = new_cnt

        def update(pos, val):
            i = size + pos
            v = val % k
            tree_prod[i] = v
            tree_cnt[i] = [0] * k
            tree_cnt[i][v] = 1
            i >>= 1
            while i >= 1:
                l, r = 2 * i, 2 * i + 1
                lp = tree_prod[l]
                tree_prod[i] = (lp * tree_prod[r]) % k
                new_cnt = [0] * k
                for rem in range(k):
                    if tree_cnt[l][rem]:
                        new_cnt[rem] += tree_cnt[l][rem]
                for rem in range(k):
                    if tree_cnt[r][rem]:
                        new_cnt[(lp * rem) % k] += tree_cnt[r][rem]
                tree_cnt[i] = new_cnt
                i >>= 1

        def query(ql, qr):
            # Returns (prod % k, cnt[k]) for subarray [ql, qr]
            # We do a standard segment tree range query but need to merge carefully
            # maintaining left-to-right order.
            # Returns (prod, cnt) for the queried range
            def merge(lp, lc, rp, rc):
                new_cnt = list(lc)
                for rem in range(k):
                    if rc[rem]:
                        new_cnt[(lp * rem) % k] += rc[rem]
                return (lp * rp) % k, new_cnt

            # Iterative range query
            left_prod = 1
            left_cnt = [0] * k
            right_prod = 1
            right_cnt = [0] * k

            l, r = ql + size, qr + size
            # We need to merge segments left to right
            # Collect left-side segments and right-side segments
            left_segs = []
            right_segs = []

            while l <= r:
                if l & 1:
                    left_segs.append((tree_prod[l], list(tree_cnt[l])))
                    l += 1
                if not (r & 1):
                    right_segs.append((tree_prod[r], list(tree_cnt[r])))
                    r -= 1
                l >>= 1
                r >>= 1

            # Merge left_segs left to right, then right_segs right to left
            res_prod = 1
            res_cnt = [0] * k
            for seg_prod, seg_cnt in left_segs:
                res_prod, res_cnt = merge(res_prod, res_cnt, seg_prod, seg_cnt)
            for seg_prod, seg_cnt in reversed(right_segs):
                res_prod, res_cnt = merge(res_prod, res_cnt, seg_prod, seg_cnt)

            return res_prod, res_cnt

        result = []
        for idx, val, start, x in queries:
            # Update nums[idx] = val
            nums[idx] = val
            update(idx, val)
            # Query [start, n-1] for cnt[x]
            _, cnt = query(start, n - 1)
            result.append(cnt[x])

        return result