class Solution:
    def averageOfSubtree(self, root: TreeNode) -> int:
        self.count = 0

        def dfs(node):
            if not node:
                return 0, 0  # sum, count
            ls, lc = dfs(node.left)
            rs, rc = dfs(node.right)
            total_sum = ls + rs + node.val
            total_cnt = lc + rc + 1
            if total_sum // total_cnt == node.val:
                self.count += 1
            return total_sum, total_cnt

        dfs(root)
        return self.count