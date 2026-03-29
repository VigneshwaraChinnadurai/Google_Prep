class Solution:
    def canBeEqual(self, s1: str, s2: str) -> bool:
        # The operation allows swapping characters at indices i and j where j - i = 2.
        # For a string of length 4, the possible swaps are:
        # 1. Index 0 with Index 2
        # 2. Index 1 with Index 3
        #
        # This partitions the string's indices into two independent groups:
        # - Even indices: {0, 2}
        # - Odd indices: {1, 3}
        # Characters can be swapped freely within their group but cannot move between groups.
        #
        # For s1 to be transformable into s2, the multiset of characters in each group
        # must be the same for both strings. We can verify this by sorting the characters
        # from each group and comparing the sorted results.

        # Check if the multiset of characters at even indices (0, 2) is the same.
        even_indices_match = sorted([s1[0], s1[2]]) == sorted([s2[0], s2[2]])
        
        # Check if the multiset of characters at odd indices (1, 3) is the same.
        odd_indices_match = sorted([s1[1], s1[3]]) == sorted([s2[1], s2[3]])
        
        return even_indices_match and odd_indices_match