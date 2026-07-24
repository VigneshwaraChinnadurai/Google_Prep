from typing import List

class Solution:
    def uniqueXorTriplets(self, nums: List[int]) -> int:
        # The problem asks for the number of unique values of the form
        # nums[i] ^ nums[j] ^ nums[k], where i <= j <= k. This is equivalent
        # to finding the size of the set {a ^ b ^ c}, where a, b, and c are
        # any values from the set of unique numbers in `nums`.

        # A brute-force O(N^3) approach is too slow given N <= 1500.
        # The constraints on values (nums[i] <= 1500) suggest an approach
        # based on the value range, such as Fast Walsh-Hadamard Transform (FWHT)
        # for XOR convolution.

        # Let S1 be the set of unique numbers in `nums`.
        # Let S2 = {a ^ b | a, b in S1}.
        # Let S3 = {s2 ^ c | s2 in S2, c in S1}. This is the set of target values.
        # We can compute these sets using XOR convolution.

        # The size of the transform array `M` must be a power of 2 greater
        # than the maximum possible XOR sum. Since max(nums[i]) <= 1500,
        # any number is < 2^11 = 2048. The XOR sum will also be < 2048.
        # So, M = 2048 is sufficient.
        M = 2048

        def fwht(a: list[float], inverse: bool = False):
            """In-place Fast Walsh-Hadamard Transform for XOR convolution."""
            n = len(a)
            h = 1
            while h < n:
                for i in range(0, n, h * 2):
                    for j in range(i, i + h):
                        x = a[j]
                        y = a[j + h]
                        a[j] = x + y
                        a[j + h] = x - y
                h *= 2
            
            if inverse:
                inv_n = 1.0 / n
                for i in range(n):
                    a[i] *= inv_n

        # Step 1: Create a frequency vector for S1 (unique numbers in nums).
        v1 = [0.0] * M
        for x in set(nums):
            v1[x] = 1.0
        
        # Step 2: Compute the transform of v1.
        t1 = v1[:]
        fwht(t1)

        # Step 3: Compute the vector for S2 = {a ^ b}.
        # In the transform domain, convolution becomes element-wise product.
        # FWHT(v1 (x) v1) = FWHT(v1) * FWHT(v1)
        t2 = [x * x for x in t1]
        v2 = t2[:]
        fwht(v2, inverse=True)

        # Step 4: Create a boolean vector for the set S2.
        # We only care about which XOR sums are possible, not how many ways.
        v2_bool = [0.0] * M
        for i in range(M):
            if abs(v2[i]) > 1e-9:  # Use tolerance for float comparison
                v2_bool[i] = 1.0
        
        # Step 5: Compute the transform of the boolean vector for S2.
        t2_bool = v2_bool[:]
        fwht(t2_bool)

        # Step 6: Compute the vector for S3 = {s2 ^ c}.
        # FWHT(v2_bool (x) v1) = FWHT(v2_bool) * FWHT(v1)
        t3 = [x * y for x, y in zip(t2_bool, t1)]
        v3 = t3[:]
        fwht(v3, inverse=True)

        # Step 7: Count the number of unique values in S3.
        # These are the indices with non-zero values in v3.
        count = 0
        for x in v3:
            if abs(x) > 1e-9:
                count += 1
        
        return count