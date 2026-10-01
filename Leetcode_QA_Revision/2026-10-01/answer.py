class Solution:
    def isValid(self, s: str) -> bool:
        stack = []
        match = {')': '(', ']': '[', '}': '{'}
        for ch in s:
            if ch in match:
                if not stack or stack[-1] != match[ch]:
                    return False
                stack.pop()
            else:
                stack.append(ch)
        return not stack