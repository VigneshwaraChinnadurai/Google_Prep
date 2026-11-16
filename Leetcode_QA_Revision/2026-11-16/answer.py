class Solution:
    def digitSum(self, s: str, k: int) -> str:
        while len(s) > k:
            parts = []
            for i in range(0, len(s), k):
                parts.append(str(sum(int(c) for c in s[i:i + k])))
            s = "".join(parts)
        return s