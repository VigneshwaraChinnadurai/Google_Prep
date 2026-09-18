class Solution:
    def maxNumOfSubstrings(self, s: str) -> list[str]:
        n = len(s)
        first = {}
        last = {}
        for i, c in enumerate(s):
            if c not in first:
                first[c] = i
            last[c] = i

        def get_right(i):
            # i must be first occurrence of s[i]
            r = last[s[i]]
            j = i
            while j <= r:
                c = s[j]
                if first[c] < i:
                    return -1
                if last[c] > r:
                    r = last[c]
                j += 1
            return r

        # Precompute right boundary for each valid start
        # Valid starts: only positions i where first[s[i]] == i
        intervals = []  # (right, left) for sorting
        for i in range(n):
            if first[s[i]] == i:
                r = get_right(i)
                if r != -1:
                    intervals.append((i, r))

        # Sort by right endpoint (greedy interval scheduling for max count, min length)
        intervals.sort(key=lambda x: x[1])

        result = []
        last_end = -1
        for l, r in intervals:
            if l > last_end:
                result.append(s[l:r + 1])
                last_end = r
            elif r < last_end:
                # Replace last with shorter one (same count, smaller total length)
                result[-1] = s[l:r + 1]
                last_end = r

        return result