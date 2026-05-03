class Solution:
    def rotateString(self, s: str, goal: str) -> bool:
        # 1. A rotation is only possible if the strings have the same length.
        if len(s) != len(goal):
            return False
        
        # 2. The core idea: If `goal` is a rotation of `s`, then `goal` must
        # be a substring of `s` concatenated with itself.
        # Example: s = "abcde", goal = "cdeab"
        # s + s = "abcdeabcde"
        # "cdeab" is a substring of "abcdeabcde", so it's a rotation.
        #
        # Example: s = "abcde", goal = "abced"
        # s + s = "abcdeabcde"
        # "abced" is not a substring, so it's not a rotation.
        #
        # This works because concatenating `s` with itself creates a string
        # that contains all possible cyclic shifts of `s`.
        # The initial length check is crucial. Without it, cases like
        # s="a", goal="aa" would incorrectly return True because "aa" is in "aa".
        
        return goal in (s + s)