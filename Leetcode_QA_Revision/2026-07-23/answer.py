import math

class Solution:
    def uniqueXorTriplets(self, nums: list[int]) -> int:
        n = len(nums)

        # Case n = 1:
        # The set of numbers is {1}. The only possible triplet is 1 XOR 1 XOR 1 = 1.
        # The set of unique values is {1}, so the count is 1.
        if n == 1:
            return 1
        
        # Case n = 2:
        # The set of numbers is {1, 2}. The possible triplets are:
        # 1^1^1 = 1
        # 1^1^2 = 2
        # 1^2^2 = 1
        # 2^2^2 = 2
        # The set of unique values is {1, 2}, so the count is 2.
        if n == 2:
            return 2
            
        # Case n >= 3:
        # The problem asks for the number of unique values of `a ^ b ^ c` where
        # a, b, c are chosen from the set S = {1, 2, ..., n}.
        # Let's consider these numbers as vectors in a vector space over GF(2) (with XOR as addition).
        # Let V be the vector space spanned by S. The basis for this space is {1, 2, 4, ..., 2^k}
        # where 2^k <= n. The dimension of V is d = floor(log2(n)) + 1, which is `n.bit_length()` in Python.
        # The size of this space V is 2^d. Any XOR sum of elements from S is in V.
        #
        # For n >= 3, the set S contains {1, 2, 3}. We have the linear dependency 1 ^ 2 ^ 3 = 0.
        # This is a dependency with an odd number of terms (3). The existence of such a dependency
        # implies that the set of all possible triplet XOR sums {a ^ b ^ c} is equal to the
        # entire vector space V.
        #
        # Therefore, for n >= 3, the number of unique XOR triplet values is the size of the
        # vector space V spanned by {1, ..., n}.
        # The size of V is 2^d, where d = n.bit_length().
        return 1 << n.bit_length()