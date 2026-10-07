class Solution:
    def removeInvalidParentheses(self, s: str) -> list[str]:
        def is_valid(t):
            count = 0
            for ch in t:
                if ch == '(':
                    count += 1
                elif ch == ')':
                    count -= 1
                    if count < 0:
                        return False
            return count == 0

        result = set()
        queue = {s}
        found = False

        while queue:
            for candidate in queue:
                if is_valid(candidate):
                    result.add(candidate)
                    found = True
            if found:
                break
            next_level = set()
            for candidate in queue:
                for i in range(len(candidate)):
                    if candidate[i] in '()':
                        next_level.add(candidate[:i] + candidate[i+1:])
            queue = next_level

        return list(result)