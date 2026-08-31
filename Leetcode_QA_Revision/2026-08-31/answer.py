from typing import Optional, List

class Solution:
    def nodesBetweenCriticalPoints(self, head: Optional[ListNode]) -> List[int]:
        criticals = []
        idx = 1
        prev = head
        curr = head.next

        while curr and curr.next:
            nxt = curr.next
            if (curr.val > prev.val and curr.val > nxt.val) or \
               (curr.val < prev.val and curr.val < nxt.val):
                criticals.append(idx)
            prev = curr
            curr = nxt
            idx += 1

        if len(criticals) < 2:
            return [-1, -1]

        max_dist = criticals[-1] - criticals[0]
        min_dist = min(criticals[i+1] - criticals[i] for i in range(len(criticals)-1))
        return [min_dist, max_dist]