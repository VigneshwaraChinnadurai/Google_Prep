class Solution:
    def maxDepthAfterSplit(self, seq: str) -> list[int]:
        # Assign each '(' at depth d to group (d % 2), each ')' similarly.
        # This interleaves the parentheses alternately between A and B,
        # halving the maximum nesting depth.
        ans = []
        depth = 0
        for ch in seq:
            if ch == '(':
                depth += 1
                ans.append(depth % 2)
            else:
                ans.append(depth % 2)
                depth -= 1
        return ans