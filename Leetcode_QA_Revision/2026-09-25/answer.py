class Solution:
    def braceExpansionII(self, expression: str) -> list[str]:
        def parse(s, i):
            # Returns (set_of_words, next_index)
            # Handles a full expression at the current level
            result = set()
            current = {""}  # current concatenation group

            while i < len(s) and s[i] != '}':
                if s[i] == '{':
                    # Parse inner braced group
                    inner, i = parse(s, i + 1)
                    # i now points to '}'
                    i += 1  # skip '}'
                    # Concatenate current with inner
                    current = {a + b for a in current for b in inner}
                elif s[i] == ',':
                    # Union: flush current into result, start new group
                    result |= current
                    current = {""}
                    i += 1
                else:
                    # Letter: extend current group
                    ch = s[i]
                    current = {a + ch for a in current}
                    i += 1

            result |= current
            return result, i

        words, _ = parse(expression, 0)
        return sorted(words)