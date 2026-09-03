class Solution:
    def uniformArray(self, nums1: list[int]) -> bool:
        # nums2[i] can be:
        #   nums1[i]              → same parity as nums1[i]
        #   nums1[i] - nums1[j]  → parity = parity(nums1[i]) XOR parity(nums1[j])
        #                          AND nums1[i] - nums1[j] >= 1 → nums1[i] > nums1[j]
        #
        # Key difference from Part I: now nums1[i] - nums1[j] >= 1 (positive only).
        # So for element i, we can only subtract a SMALLER element.
        #
        # Parity of nums1[i] - nums1[j]:
        #   same parity as nums1[i]: subtract even nums1[j], or nums1[j]=0 (N/A, values>=1)
        #   diff parity from nums1[i]: subtract odd nums1[j]
        #
        # For element nums1[i]:
        #   Option A (keep parity): use nums1[i] directly.
        #   Option B (flip parity): use nums1[i] - nums1[j] where nums1[j] < nums1[i]
        #                           and parity(nums1[j]) differs from parity(nums1[i]).
        #     → odd nums1[i]: can flip to even by subtracting odd j with nums1[j] < nums1[i]
        #     → even nums1[i]: can flip to odd by subtracting odd j with nums1[j] < nums1[i]
        #
        # Note: subtracting even j keeps parity; subtracting odd j flips parity.
        # So flipping parity of element i requires existence of an odd element < nums1[i].
        #
        # Let's analyze both target parities:
        #
        # ALL-ODD target:
        #   - Odd nums1[i]: keep (Option A). Always OK.
        #   - Even nums1[i]: need to flip → need odd nums1[j] < nums1[i].
        #     This is possible iff there exists at least one odd value less than nums1[i].
        #     Easiest check: sort nums1. Process left to right tracking if any odd seen so far.
        #     For each even element, it's convertible iff we've seen an odd element before it.
        #     But if an even element is smaller than ALL odd elements, it can't be converted.
        #
        # ALL-EVEN target:
        #   - Even nums1[i]: keep. Always OK.
        #   - Odd nums1[i]: need to flip → need odd nums1[j] < nums1[i].
        #     Same condition: an odd element smaller than nums1[i] must exist.
        #     But subtracting odd from odd gives even: parity flip. Need odd j < odd i.
        #     So odd i can be converted to even iff there's a smaller odd j.
        #
        # Summary after sorting:
        #   ALL-ODD: every even element must have a preceding odd element in sorted order.
        #   ALL-EVEN: every odd element (except possibly the smallest odd) must have a
        #             preceding odd element (but the smallest odd has no smaller odd,
        #             so it can only keep its value = odd, can't make even).
        #             → ALL-EVEN fails if any odd element exists AND that odd element
        #               has no smaller odd predecessor.
        #             → Specifically, the smallest odd element cannot be made even.
        #             → ALL-EVEN possible iff no odd elements exist.
        #             Wait: let me reconsider.
        #             Odd i to even: subtract odd j where j < i (in value).
        #             The smallest odd element has no smaller odd. But it could subtract
        #             an even j < it? No: odd - even = odd (parity preserved, not flipped).
        #             So smallest odd element CANNOT be made even.
        #             → ALL-EVEN possible iff count_odd == 0.
        #
        # Refined:
        #   ALL-EVEN: possible iff count_odd == 0.
        #   ALL-ODD: possible iff for every even element, there exists an odd element
        #             strictly smaller than it.
        #             Equivalently: sort nums1; process left to right; maintain seen_odd flag;
        #             for each even element, check seen_odd (some odd seen before it in sorted order).
        #             If any even fails this check → ALL-ODD impossible.

        count_odd = sum(1 for x in nums1 if x % 2 == 1)

        # Check ALL-EVEN
        if count_odd == 0:
            return True

        # Check ALL-ODD: every even element must have a smaller odd element
        sorted_nums = sorted(nums1)
        seen_odd = False
        all_odd_ok = True
        for x in sorted_nums:
            if x % 2 == 1:
                seen_odd = True
            else:
                if not seen_odd:
                    all_odd_ok = False
                    break

        return all_odd_ok