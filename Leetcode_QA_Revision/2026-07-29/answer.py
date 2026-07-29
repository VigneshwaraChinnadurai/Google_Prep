import collections

class Solution:
    def smallestPalindrome(self, s: str, k: int) -> str:
        # 1. Character counts and determine first half composition
        n = len(s)
        counts = collections.Counter(s)
        
        half_counts_list = [0] * 26
        middle_char = ""
        
        for i in range(26):
            char = chr(ord('a') + i)
            count = counts[char]
            half_counts_list[i] = count // 2
            if count % 2 == 1:
                middle_char = char
                
        L = n // 2
        
        # 2. Precompute factorials for permutation calculations
        # Python's arbitrary-precision integers handle large factorial values.
        fact = [1] * (L + 1)
        for i in range(2, L + 1):
            fact[i] = fact[i-1] * i
            
        # Helper to calculate permutations of a multiset: n! / (c1! * c2! * ...)
        def count_perms(current_counts, length):
            if length < 0:
                return 0
            
            res = fact[length]
            for count in current_counts:
                if count > length: return 0 # Should not happen
                res //= fact[count]
            return res

        # 3. Check if k is valid
        total_perms = count_perms(half_counts_list, L)
        if k > total_perms:
            return ""
            
        # 4. Construct the first half of the k-th palindrome
        # This is a standard algorithm for finding the k-th permutation of a multiset (unranking).
        first_half = []
        current_L = L
        
        for _ in range(L):
            for i in range(26): # Iterate through characters 'a' to 'z'
                if half_counts_list[i] > 0:
                    # Try placing character 'a'+i at the current position
                    half_counts_list[i] -= 1
                    
                    # Calculate how many permutations start with this prefix
                    perms_count = count_perms(half_counts_list, current_L - 1)
                    
                    if k <= perms_count:
                        # This is the correct character. Append and move to the next position.
                        first_half.append(chr(ord('a') + i))
                        current_L -= 1
                        break
                    else:
                        # Skip over these permutations and try the next character.
                        k -= perms_count
                        # Backtrack the count for the next iteration of the inner loop
                        half_counts_list[i] += 1
            
        # 5. Assemble the final palindrome
        first_half_str = "".join(first_half)
        second_half_str = first_half_str[::-1]
        
        return first_half_str + middle_char + second_half_str