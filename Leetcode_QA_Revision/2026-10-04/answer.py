class Solution:
    def checkValidString(self, s: str) -> bool:
        # Track the range [lo, hi] of possible open-paren counts
        # lo = minimum possible unmatched '(' (treat '*' as ')' or '')
        # hi = maximum possible unmatched '(' (treat '*' as '(')
        lo = hi = 0
        for ch in s:
            if ch == '(':
                lo += 1; hi += 1
            elif ch == ')':
                lo -= 1; hi -= 1
            else:  # '*'
                lo -= 1; hi += 1
            if hi < 0:
                return False  # too many ')'
            lo = max(lo, 0)  # lo can't go negative
        return lo == 0