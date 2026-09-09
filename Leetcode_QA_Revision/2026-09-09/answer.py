class Solution:
    def countCommas(self, n: int) -> int:
        # A number x has floor((len(str(x)) - 1) / 3) commas.
        # Equivalently, commas = floor((digits - 1) / 3).
        # d digits: range [10^(d-1), 10^d - 1]
        # commas per number: floor((d-1)/3)
        #
        # Alternatively: total commas = sum over k>=1 of count(numbers >= 10^(3k))
        # = sum over k>=1 of max(0, n - 10^(3k) + 1)
        #
        # Thresholds: 10^3=1000, 10^6, 10^9, 10^12, 10^15, ...
        # n <= 10^15, so at most 5 iterations.

        total = 0
        threshold = 1000
        while threshold <= n:
            total += n - threshold + 1
            threshold *= 1000
        return total