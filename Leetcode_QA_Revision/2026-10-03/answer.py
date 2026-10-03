class Solution:
    def longestValidParentheses(self, s: str) -> int:
        # Stack stores indices; initialize with -1 as base sentinel
        stack = [-1]
        best = 0

        for i, ch in enumerate(s):
            if ch == '(':
                stack.append(i)
            else:
                stack.pop()
                if not stack:
                    stack.append(i)  # new base sentinel
                else:
                    best = max(best, i - stack[-1])

        return best