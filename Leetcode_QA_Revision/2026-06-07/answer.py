# Definition for a binary tree node.
# class TreeNode:
#     def __init__(self, val=0, left=None, right=None):
#         self.val = val
#         self.left = left
#         self.right = right
class Solution:
    def createBinaryTree(self, descriptions: List[List[int]]) -> Optional[TreeNode]:
        nodes_map = {}
        children_set = set()

        # Step 1: Build the tree structure and identify all child nodes.
        for parent_val, child_val, is_left in descriptions:
            # Add the child value to a set for later root identification.
            children_set.add(child_val)

            # Get or create the parent node.
            if parent_val not in nodes_map:
                nodes_map[parent_val] = TreeNode(parent_val)
            
            # Get or create the child node.
            if child_val not in nodes_map:
                nodes_map[child_val] = TreeNode(child_val)

            # Link the parent and child nodes.
            if is_left:
                nodes_map[parent_val].left = nodes_map[child_val]
            else:
                nodes_map[parent_val].right = nodes_map[child_val]

        # Step 2: Find the root.
        # The root is the only node that appears as a parent but never as a child.
        # Since the input guarantees a valid, non-empty tree, the root must be a parent.
        # We can iterate through the parent values from the descriptions and find the one
        # that is not in our set of children.
        for p, _, _ in descriptions:
            if p not in children_set:
                return nodes_map[p]
        
        return None # Should not be reached due to problem constraints.