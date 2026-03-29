class Solution:
    def findTheString(self, lcp: list[list[int]]) -> str:
        n = len(lcp)
        
        # Step 1: Greedily construct the lexicographically smallest candidate string.
        # The logic is that if a solution exists, the lexicographically smallest one
        # is found by assigning the smallest possible characters ('a', then 'b', etc.)
        # to groups of indices that must have the same character.
        
        # word[i] = 0 means the character at index i is not yet assigned.
        # A positive integer (1-26) represents an assigned character ('a' corresponds to 1).
        word = [0] * n
        next_char_code = 1
        
        for i in range(n):
            # If word[i] has not been determined by a previous character's constraint,
            # assign it the smallest available new character.
            if word[i] == 0:
                if next_char_code > 26:
                    # We need more than 26 distinct characters, which is impossible.
                    return ""
                word[i] = next_char_code
                next_char_code += 1
            
            # Propagate the character assignment based on LCP information.
            # If lcp[i][j] > 0, it implies word[i] must equal word[j].
            for j in range(i + 1, n):
                if lcp[i][j] > 0:
                    if word[j] == 0:
                        # If word[j] is unassigned, assign it the same character as word[i].
                        word[j] = word[i]
                    elif word[j] != word[i]:
                        # If word[j] was already assigned a different character by a previous
                        # constraint (e.g., from some lcp[k][j] > 0 with k < i),
                        # then we have a contradiction. The lcp matrix is invalid.
                        return ""

        # Convert the numeric representation of the word to a string.
        candidate_word = "".join(chr(ord('a') + c - 1) for c in word)
        
        # Step 2: Verify if the constructed candidate_word generates the given lcp matrix.
        # This candidate is our only hope for a solution. If it's not valid, no solution exists.
        
        # We compute the LCP matrix from our candidate_word using dynamic programming.
        # lcp_computed[i][j] = LCP of candidate_word[i:] and candidate_word[j:]
        lcp_computed = [[0] * n for _ in range(n)]
        for i in range(n - 1, -1, -1):
            for j in range(n - 1, -1, -1):
                if candidate_word[i] == candidate_word[j]:
                    if i == n - 1 or j == n - 1:
                        lcp_computed[i][j] = 1
                    else:
                        lcp_computed[i][j] = 1 + lcp_computed[i+1][j+1]
        
        # Compare the computed LCP matrix with the input. If they don't match,
        # the input lcp matrix is invalid or inconsistent.
        if lcp_computed != lcp:
            return ""
            
        return candidate_word