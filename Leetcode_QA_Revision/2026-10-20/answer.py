class Solution:
    def sortableIntegers(self, nums: list[int]) -> int:
        n = len(nums)
        # sorted reference
        s = sorted(nums)
        s_idx = {v: i for i, v in enumerate(s)}  # value → sorted position

        # For a given k, partition into n/k blocks of size k.
        # Each block must be a cyclic rotation of the corresponding sorted block.
        # Condition: for each block [ik, ik+k), the elements must equal the sorted
        # elements at those positions, and they must form a valid rotation
        # (i.e., the block is sorted except for at most one "wrap" where values decrease).

        # A rotation of a subarray is valid for sorted order iff:
        # 1. The multiset of elements matches the sorted subarray.
        # 2. The subarray has at most one "descent" (where a[i] > a[i+1]) — it's a
        #    cyclic rotation of a sorted array.

        def is_sortable(k):
            num_blocks = n // k
            for b in range(num_blocks):
                start = b * k
                block = nums[start:start + k]
                ref   = s[start:start + k]
                # Must have same multiset
                if sorted(block) != ref:
                    return False
                # Check it's a rotation of ref (which is already sorted).
                # A cyclic rotation of a sorted array has at most one descent.
                descents = sum(1 for i in range(k - 1) if block[i] > block[i + 1])
                if descents > 1:
                    return False
                # Also check wrap-around: block[-1] could be > block[0] only if descent=0 already
                # Actually: a rotation of sorted array → block[-1] <= block[0] OR descents==0
                # More precisely: at most one descent, and the "wrap" must also be consistent.
                # If descents == 1: the descent position splits block into [sorted_right, sorted_left]
                # where sorted_left + sorted_right = ref. Already ensured by multiset check + ≤1 descent.
                # But we need block[-1] ≤ block[0] when descents == 1 (the wrap connects them):
                if descents == 1 and block[-1] > block[0]:
                    return False
            return True

        total = 0
        for k in range(1, n + 1):
            if n % k == 0 and is_sortable(k):
                total += k
        return total