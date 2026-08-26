class Solution:
    def shortestBeautifulSubstring(self, s: str, k: int) -> str:
        n = len(s)
        best = ""
        for i in range(n):
            ones = 0
            for j in range(i, n):
                ones += int(s[j])
                if ones == k:
                    sub = s[i:j+1]
                    if not best or len(sub) < len(best) or (len(sub) == len(best) and sub < best):
                        best = sub
                    break
        return best