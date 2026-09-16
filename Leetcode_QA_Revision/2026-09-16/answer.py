class Solution:
    def numberOfSets(self, n: int, k: int) -> int:
        # Key insight: k non-overlapping segments on n points where segments
        # can share endpoints = C(n + k - 1, 2k)
        # 
        # Derivation: Transform each of the k segments' endpoints.
        # Segments share endpoints allowed → treat endpoints as allowing repeats.
        # Map to choosing 2k endpoints from n+k-1 points (stars and bars style).
        # 
        # Formula: C(n + k - 1, 2k)
        MOD = 10**9 + 7

        # Compute C(n + k - 1, 2k) mod MOD
        total = n + k - 1
        r = 2 * k

        if r > total:
            return 0

        # Lucas/Fermat inverse for modular binomial
        def modinv(a, m):
            return pow(a, m - 2, m)

        # Compute C(total, r) mod MOD
        num = 1
        den = 1
        for i in range(r):
            num = num * ((total - i) % MOD) % MOD
            den = den * (i + 1) % MOD

        return num * modinv(den, MOD) % MOD