class Solution:
    def findThePrefixCommonArray(self, A: List[int], B: List[int]) -> List[int]:
        n = len(A)
        C = [0] * n
        
        # freq[x] will count how many times number x has appeared in the prefixes of A and B combined.
        # If freq[x] == 2, it means x is in both prefixes and is therefore a common number.
        # Since numbers are guaranteed to be from 1 to n, an array is efficient.
        freq = [0] * (n + 1)
        
        common_count = 0
        
        for i in range(n):
            # Process the element from A's prefix
            val_A = A[i]
            freq[val_A] += 1
            # If the frequency becomes 2, it means this number has now been seen in both prefixes.
            if freq[val_A] == 2:
                common_count += 1
            
            # Process the element from B's prefix
            val_B = B[i]
            # This logic correctly handles both cases: A[i] == B[i] and A[i] != B[i].
            # If A[i] == B[i] = x, freq[x] is incremented twice. It goes from 0 to 1 (no count change),
            # then from 1 to 2 (common_count increments by 1).
            freq[val_B] += 1
            if freq[val_B] == 2:
                common_count += 1
            
            C[i] = common_count
            
        return C