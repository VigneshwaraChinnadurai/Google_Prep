class Solution:
    def maxSumOfSquares(self, num: int, s: int) -> str:
        # Greedy: to maximise sum of squares with fixed digit sum s and num digits,
        # concentrate as much digit-sum as possible into each digit (max 9).
        # Place 9s as early as possible (leftmost), fill remainder at end.
        # Also: the number itself must be maximised among equal-score numbers.
        # Placing large digits early achieves both goals simultaneously.

        # Validity check: sum must be achievable with num digits (each 0-9)
        if s > 9 * num or s < 1:
            return ""

        digits = []
        remaining = s
        for i in range(num):
            # Assign max possible to this digit
            d = min(9, remaining - (num - i - 1))  # leave at least 0 for remaining digits
            # Also ensure remaining - d >= 0 (can't go negative)
            d = max(0, d)
            digits.append(d)
            remaining -= d

        return ''.join(map(str, digits))