from collections import Counter

class Solution:
    def smallestPalindrome(self, s: str, k: int) -> str:
        n = len(s)
        freq = Counter(s)

        half = {}
        mid = ''
        for c, cnt in freq.items():
            if cnt % 2 == 1:
                mid = c
            if cnt // 2 > 0:
                half[c] = cnt // 2

        m = n // 2
        CAP = k + 1  # anything >= this is "more than enough"

        def capped_binom(n, r, cap):
            r = min(r, n - r)
            if r == 0: return 1
            result = 1
            for i in range(r):
                result = result * (n - i) // (i + 1)
                if result >= cap:
                    return cap
            return result

        def count_perms(counts, cap):
            # Multinomial: total! / prod(cnt!) via product of binomials
            remaining = sum(counts.values())
            result = 1
            for cnt in counts.values():
                result *= capped_binom(remaining, cnt, cap)
                if result >= cap:
                    return cap
                remaining -= cnt
            return result

        total_count = count_perms(half, CAP)
        if total_count < k:
            return ""

        result_chars = []
        current_count = total_count
        total = m  # decrements by 1 each step

        for _ in range(m):
            for c in 'abcdefghijklmnopqrstuvwxyz':
                if half.get(c, 0) == 0:
                    continue

                if current_count < CAP:
                    # Exact O(1) update: N_new = N * half[c] / total
                    subtree = current_count * half[c] // total
                else:
                    # Count is capped; recompute subtree from scratch
                    old = half[c]
                    half[c] -= 1
                    if half[c] == 0: del half[c]
                    subtree = count_perms(half, CAP)
                    half[c] = old  # restore

                if k <= subtree:
                    result_chars.append(c)
                    half[c] -= 1
                    if half[c] == 0: del half[c]
                    current_count = subtree
                    total -= 1
                    break
                k -= subtree

        first_half = ''.join(result_chars)
        return first_half + mid + first_half[::-1]