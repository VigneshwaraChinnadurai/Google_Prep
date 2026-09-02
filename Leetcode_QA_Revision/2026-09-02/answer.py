class Solution:
    def uniformArray(self, nums1: list[int]) -> bool:
        # For each element nums1[i], nums2[i] can be:
        #   nums1[i]            → parity = nums1[i] % 2
        #   nums1[i] - nums1[j] → parity = (nums1[i] - nums1[j]) % 2
        #                       = (nums1[i] + nums1[j]) % 2  (subtraction same parity as addition)
        # So nums2[i] can be even or odd depending on choice of j.
        # Key insight: nums1[i] - nums1[j] has parity = (nums1[i] % 2) XOR (nums1[j] % 2)
        # If nums1[i] is even: nums2[i] can be even (keep) or odd (subtract an odd j)
        # If nums1[i] is odd:  nums2[i] can be odd (keep) or even (subtract an odd j)
        #
        # Each element can always achieve BOTH parities as long as there exists
        # at least one odd element in nums1 (to pair with for subtraction).
        # If all elements are even: nums2[i]=nums1[i] gives all even, OR
        #   nums1[i]-nums1[j] is even-even=even. Can't get odd. So all-even only.
        # If all elements are odd: nums2[i]=nums1[i] gives all odd, OR
        #   nums1[i]-nums1[j] is odd-odd=even. Can get all-even too.
        # If mixed: any element can be made either parity → both all-even and all-odd work.
        #
        # So: return True if either all-even or all-odd is achievable.
        # → return True always EXCEPT when no construction is possible.
        # When is it impossible? Never with n>=1, since:
        #   - If any odd exists: can make all elements odd (keep odds, subtract pairs for evens)
        #     and can make all elements even.
        #   - If all even: can only make all-even (since even-even=even).
        # So answer is always True.

        # Simpler analysis: can we make all even?
        # nums2[i] even iff nums1[i] even (keep) OR nums1[i]-nums1[j] even
        # nums1[i]-nums1[j] even iff same parity. So for even nums2[i]:
        #   if nums1[i] even: use nums1[i] directly.
        #   if nums1[i] odd: need j with nums1[j] odd (j != i), so odd-odd=even.
        # Can make all even iff: every odd element has another odd element to pair with,
        # i.e., count of odds != 1, OR n==1 (only element, no restriction on j needed
        # since we can just keep nums1[0]).
        # Wait, if count_odd == 1, that single odd element has no other odd to subtract.
        # It can keep itself (odd) or subtract an even (odd-even=odd). Can't make it even.
        # So all-even fails when count_odd == 1.

        # Can we make all odd?
        # nums2[i] odd iff nums1[i] odd (keep) OR nums1[i]-nums1[j] odd (different parities).
        # For even nums1[i]: need j with nums1[j] odd. Possible iff count_odd >= 1.
        # For odd nums1[i]: just keep it.
        # So all-odd possible iff count_odd >= 1 (every even has an odd to subtract).

        count_odd = sum(1 for x in nums1 if x % 2 == 1)
        n = len(nums1)

        all_even_possible = (count_odd == 0) or (count_odd >= 2)
        all_odd_possible  = (count_odd >= 1)

        return all_even_possible or all_odd_possible