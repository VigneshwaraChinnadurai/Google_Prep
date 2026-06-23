class Solution:
    def zigZagArrays(self, n: int, l: int, r: int) -> int:
        MOD = 10**9 + 7

        # dp[j][0]: count of valid arrays of length i ending with ... < j
        # dp[j][1]: count of valid arrays of length i ending with ... > j
        # We use prev_dp for length i-1 and dp for length i.
        # Using arrays of size r+2 for 1-based indexing on values up to r+1
        # to avoid index out of bounds issues with k-1 or k+1.
        prev_dp = [[0, 0] for _ in range(r + 2)]

        # Base case: i = 2
        # For a sequence of length 2, [a1, a2], the only condition is a1 != a2.
        # prev_dp[j][0] counts pairs [a1, j] where a1 < j.
        # prev_dp[j][1] counts pairs [a1, j] where a1 > j.
        for j in range(l, r + 1):
            # Number of a1 in [l, r] with a1 < j is j - l.
            if j > l:
                prev_dp[j][0] = j - l
            # Number of a1 in [l, r] with a1 > j is r - j.
            if j < r:
                prev_dp[j][1] = r - j

        # DP for lengths i from 3 to n
        for i in range(3, n + 1):
            dp = [[0, 0] for _ in range(r + 2)]
            
            # To form a valid sequence of length i ending with a_{i-1} < a_i = j,
            # the previous triplet must be a valley: a_{i-2} > a_{i-1}.
            # So, we need to extend sequences of length i-1 that ended with a down-stroke.
            # dp[i][j][0] = sum(prev_dp[k][1] for k < j)
            # We can calculate this using a running prefix sum.
            current_prefix_sum_1 = 0
            for j in range(l, r + 1):
                dp[j][0] = current_prefix_sum_1
                current_prefix_sum_1 = (current_prefix_sum_1 + prev_dp[j][1]) % MOD

            # To form a valid sequence of length i ending with a_{i-1} > a_i = j,
            # the previous triplet must be a peak: a_{i-2} < a_{i-1}.
            # So, we need to extend sequences of length i-1 that ended with an up-stroke.
            # dp[i][j][1] = sum(prev_dp[k][0] for k > j)
            # We can calculate this using a running suffix sum.
            current_suffix_sum_0 = 0
            for j in range(r, l - 1, -1):
                dp[j][1] = current_suffix_sum_0
                current_suffix_sum_0 = (current_suffix_sum_0 + prev_dp[j][0]) % MOD
            
            prev_dp = dp

        # The total number of valid arrays of length n is the sum of all entries
        # in the final DP table.
        total_count = 0
        for j in range(l, r + 1):
            total_count = (total_count + prev_dp[j][0]) % MOD
            total_count = (total_count + prev_dp[j][1]) % MOD
            
        return total_count