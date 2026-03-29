class Solution:
    def areSimilar(self, mat: List[List[int]], k: int) -> bool:
        m = len(mat)
        n = len(mat[0])

        # The pattern of shifts repeats every n steps for a row of length n.
        # So, we only need to consider the net shift k % n.
        effective_k = k % n

        # If the effective shift is 0, the matrix is unchanged.
        if effective_k == 0:
            return True

        for i in range(m):
            original_row = mat[i]
            
            if i % 2 == 0:  # Even-indexed row: shift left
                # A left shift by `effective_k` positions.
                shifted_row = original_row[effective_k:] + original_row[:effective_k]
            else:  # Odd-indexed row: shift right
                # A right shift by `effective_k` is equivalent to a left shift by `n - effective_k`.
                left_shift_amount = n - effective_k
                shifted_row = original_row[left_shift_amount:] + original_row[:left_shift_amount]

            # If any row is not the same as its original state after the shifts,
            # the final matrix is not similar.
            if shifted_row != original_row:
                return False
        
        # If all rows are identical to their original state, the matrix is similar.
        return True