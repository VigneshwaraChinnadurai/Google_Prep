class Solution:
    def numOfStrings(self, patterns: list[str], word: str) -> int:
        """
        Counts the number of strings in `patterns` that are substrings of `word`.

        This solution iterates through each pattern in the `patterns` list.
        For each pattern, it checks if it is a substring of the `word` string.
        Python's `in` operator for strings is an efficient way to perform this check.

        A generator expression `(p in word for p in patterns)` yields a sequence of
        booleans (True/False). The `sum()` function treats `True` as 1 and `False` as 0,
        effectively counting the number of `True` values, which corresponds to
        the number of patterns found in the word.
        """
        return sum(1 for p in patterns if p in word)