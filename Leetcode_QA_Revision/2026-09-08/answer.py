class Solution:
    def countCommas(self, n: int) -> int:
        if n < 1000:
            return 0
        # Numbers 1000..9999 each have 1 comma
        # Numbers 10000..99999 each have 1 comma
        # Numbers 100000..999999 each have 1 comma
        # Numbers 1000000..9999999 each have 2 commas
        # Since n <= 10^5, max 1 comma per number (10^6 > 10^5)
        return n - 999