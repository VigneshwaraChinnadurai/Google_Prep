class Solution:
    def sumGame(self, num: str) -> bool:
        n = len(num)
        half = n // 2

        # Sum of known digits and count of '?' in each half
        left_sum  = sum(int(c) for c in num[:half] if c != '?')
        right_sum = sum(int(c) for c in num[half:] if c != '?')
        left_q    = num[:half].count('?')
        right_q   = num[half:].count('?')

        # Bob wins iff he can always equalise.
        # Key insight: pairs of '?' cancel (one from each half, Bob mirrors Alice).
        # After pairing, any leftover unpaired '?' on one side must be balanced by
        # the digit-sum difference. A single unpaired '?' can contribute at most 9
        # (average 4.5). Bob can equalise an unpaired pair of same-side '??'
        # by choosing 9 and 0 (sum=9) to match a deficit of 9.
        # Alice wins iff the following condition does NOT hold:
        #   (left_sum - right_sum) == (right_q - left_q) * 9 / 2
        # Which after clearing fractions (total '?' must be even for pairing):
        #   2*(left_sum - right_sum) == (right_q - left_q) * 9
        # Alice wins iff this is NOT satisfied.

        return 2 * (left_sum - right_sum) != (right_q - left_q) * 9