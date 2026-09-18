class Solution:
    def maxNumOfSubstrings(self, s: str) -> list[str]:
        n = len(s)

        # For each character, find first and last occurrence
        first = {}
        last = {}
        for i, c in enumerate(s):
            if c not in first:
                first[c] = i
            last[c] = i

        # For a starting position i (first occurrence of s[i]),
        # compute the minimal valid right boundary of the substring starting at i.
        # If the substring is invalid (contains a char whose first occurrence is < i), return -1.
        def get_right(i):
            c = s[i]
            if first[c] != i:
                return -1  # i is not the first occurrence of s[i]
            r = last[c]
            j = i
            while j <= r:
                c2 = s[j]
                if first[c2] < i:
                    return -1  # must include a char that starts before i → invalid
                r = max(r, last[c2])
                j += 1
            return r

        # Greedy: scan left to right, greedily pick shortest valid substrings
        result = []
        last_end = -1

        for i in range(n):
            if s[i] != s[first[s[i]]]:
                continue  # not the first occurrence of this char
            if first[s[i]] != i:
                continue

            r = get_right(i)
            if r == -1:
                continue  # invalid starting point

            if i > last_end:
                # Start a new substring
                result.append(s[i:r + 1])
                last_end = r
            else:
                # Try to replace last result with a shorter one ending earlier
                if r < last_end:
                    result[-1] = s[i:r + 1]
                    last_end = r

        return result