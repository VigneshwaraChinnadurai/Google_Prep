# Definition for singly-linked list.
# class ListNode:
#     def __init__(self, val=0, next=None):
#         self.val = val
#         self.next = next
class Solution:
    def rotateRight(self, head: Optional[ListNode], k: int) -> Optional[ListNode]:
        # Edge cases: empty list, single-node list, or no rotation needed.
        if not head or not head.next or k == 0:
            return head

        # 1. Find the length of the list and the tail node.
        # This loop also positions 'old_tail' at the last node.
        length = 1
        old_tail = head
        while old_tail.next:
            old_tail = old_tail.next
            length += 1

        # 2. Calculate the effective number of rotations.
        # Rotating by 'length' is a full circle, resulting in no change.
        k = k % length
        
        # If the effective rotation is 0, the list remains unchanged.
        if k == 0:
            return head

        # 3. Find the new tail of the rotated list.
        # The new tail is the node at position (length - k - 1) from the start.
        # We need to traverse (length - k - 1) steps from the head.
        new_tail = head
        for _ in range(length - k - 1):
            new_tail = new_tail.next
            
        # 4. Re-wire the pointers to perform the rotation.
        # The node immediately after the new tail becomes the new head.
        new_head = new_tail.next
        
        # Break the list at the new tail to form the end of the rotated list.
        new_tail.next = None
        
        # Connect the original tail to the original head.
        old_tail.next = head

        return new_head