class Solution:
    def minInsertions(self, s: str) -> int:
        insertions = 0
        open_count = 0  # unmatched '(' needing '))'
        i = 0
        n = len(s)

        while i < n:
            if s[i] == '(':
                open_count += 1
                i += 1
            else:
                # Try to consume '))'
                if i + 1 < n and s[i + 1] == ')':
                    i += 2  # consume '))'
                else:
                    insertions += 1  # insert one ')' to complete '))'
                    i += 1

                if open_count > 0:
                    open_count -= 1  # matched one '('
                else:
                    insertions += 1  # insert '(' to match this '))'

        # Each unmatched '(' needs '))'
        insertions += 2 * open_count
        return insertions