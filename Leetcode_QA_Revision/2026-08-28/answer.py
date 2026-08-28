class Solution:
    def lexPalindromicPermutation(self, s: str, target: str) -> str:
        from collections import Counter
        
        n = len(s)
        char_count = Counter(s)
        
        # Check if palindrome is possible
        odd_count = sum(1 for c in char_count.values() if c % 2 == 1)
        if (n % 2 == 0 and odd_count > 0) or (n % 2 == 1 and odd_count != 1):
            return ""
        
        # Find middle character
        middle_char = ''
        for ch, cnt in char_count.items():
            if cnt % 2 == 1:
                middle_char = ch
                break
        
        half_len = n // 2
        half_counts = {ch: cnt // 2 for ch, cnt in char_count.items()}
        
        def build_palindrome(first_half):
            return ''.join(first_half) + (middle_char if n % 2 == 1 else '') + ''.join(reversed(first_half))
        
        def fill_smallest(remaining, length):
            """Fill with smallest available characters"""
            result = []
            rem = remaining.copy()
            for _ in range(length):
                found = False
                for ch in sorted(rem.keys()):
                    if rem[ch] > 0:
                        result.append(ch)
                        rem[ch] -= 1
                        if rem[ch] == 0:
                            del rem[ch]
                        found = True
                        break
                if not found:
                    return None
            return result
        
        # Try to match target up to position i, then go greater at position i
        for i in range(half_len + 1):
            remaining = half_counts.copy()
            first_half = []
            
            # Match target for positions 0 to i-1
            valid = True
            for j in range(i):
                target_char = target[j]
                if remaining.get(target_char, 0) > 0:
                    first_half.append(target_char)
                    remaining[target_char] -= 1
                    if remaining[target_char] == 0:
                        del remaining[target_char]
                else:
                    valid = False
                    break
            
            if not valid:
                continue
            
            if i == half_len:
                # All matched, check if equal
                pal = build_palindrome(first_half)
                if pal > target:
                    return pal
                continue
            
            # Try characters > target[i] at position i
            target_char = target[i]
            for ch in sorted(remaining.keys()):
                if ch > target_char and remaining[ch] > 0:
                    new_remaining = remaining.copy()
                    new_remaining[ch] -= 1
                    if new_remaining[ch] == 0:
                        del new_remaining[ch]
                    
                    # Fill rest with smallest chars
                    suffix = fill_smallest(new_remaining, half_len - i - 1)
                    if suffix is not None:
                        result_half = first_half + [ch] + suffix
                        return build_palindrome(result_half)
        
        return ""