class Solution:
    def lexPalindromicPermutation(self, s: str, target: str) -> str:
        from collections import Counter
        
        n = len(s)
        char_count = Counter(s)
        
        # Check if palindrome is possible
        odd_count = sum(1 for c in char_count.values() if c % 2 == 1)
        if (n % 2 == 0 and odd_count > 0) or (n % 2 == 1 and odd_count != 1):
            return ""
        
        # Find middle character (for odd length palindromes)
        middle_char = ''
        for ch, cnt in char_count.items():
            if cnt % 2 == 1:
                middle_char = ch
                break
        
        # Prepare character counts for first half
        half_len = n // 2
        half_counts = {}
        for ch, cnt in char_count.items():
            half_counts[ch] = cnt // 2
        
        def build_palindrome(first_half):
            result = list(first_half) + ([middle_char] if n % 2 == 1 else []) + list(reversed(first_half))
            return ''.join(result)
        
        def get_smallest_suffix(remaining, length):
            """Build lexicographically smallest suffix from remaining chars"""
            result = []
            rem = remaining.copy()
            for _ in range(length):
                for ch in sorted(rem.keys()):
                    if rem[ch] > 0:
                        result.append(ch)
                        rem[ch] -= 1
                        break
                else:
                    return None
            return result
        
        def backtrack(pos, current, remaining):
            if pos == half_len:
                pal = build_palindrome(current)
                if pal > target:
                    return pal
                return None
            
            # Try each character in sorted order (greedy for lexicographically smallest)
            for ch in sorted(remaining.keys()):
                if remaining[ch] > 0:
                    new_current = current + [ch]
                    new_remaining = remaining.copy()
                    new_remaining[ch] -= 1
                    if new_remaining[ch] == 0:
                        del new_remaining[ch]
                    
                    # Build smallest possible completion and check early
                    suffix = get_smallest_suffix(new_remaining, half_len - pos - 1)
                    if suffix is not None:
                        complete_half = new_current + suffix
                        pal = build_palindrome(complete_half)
                        if pal > target:
                            return pal
                    
                    # Continue backtracking for other possibilities
                    result = backtrack(pos + 1, new_current, new_remaining)
                    if result:
                        return result
            
            return None
        
        return backtrack(0, [], half_counts) or ""