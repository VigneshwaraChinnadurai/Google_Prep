class Solution:
    def numOfSubarrays(self, arr: list[int]) -> int:
        MOD = 10**9 + 7
        even, odd = 1, 0  # counts of prefix sums by parity, including the empty prefix
        parity = 0
        ans = 0
        for x in arr:
            parity ^= x & 1
            if parity:
                ans += even
                odd += 1
            else:
                ans += odd
                even += 1
        return ans % MOD