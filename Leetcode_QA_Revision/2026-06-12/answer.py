import sys
from typing import List

# It's good practice to increase the recursion limit for deep trees.
# On LeetCode, this might not be strictly necessary due to their environment
# setup, but it's a safe measure for recursion-heavy solutions.
sys.setrecursionlimit(10**5 + 5)

class Solution:
    def assignEdgeWeights(self, edges: List[List[int]], queries: List[List[int]]) -> List[int]:
        n = len(edges) + 1
        MOD = 10**9 + 7

        # Step 1: Build the adjacency list representation of the tree.
        # Nodes are 1-indexed, so we use arrays of size n+1.
        adj = [[] for _ in range(n + 1)]
        for u, v in edges:
            adj[u].append(v)
            adj[v].append(u)

        # Step 2: Precomputation for LCA (Lowest Common Ancestor) using binary lifting.
        # MAX_LOG is determined by the maximum possible depth, which is n.
        # (n).bit_length() is a clean way to get ceil(log2(n)).
        MAX_LOG = (n).bit_length()
        
        depth = [-1] * (n + 1)
        # up[i][j] stores the 2^j-th ancestor of node i.
        up = [[0] * MAX_LOG for _ in range(n + 1)]

        # Step 2a: Perform a DFS from the root (node 1) to compute depths
        # and the immediate parent (2^0-th ancestor) for each node.
        def dfs(u, p, d):
            depth[u] = d
            up[u][0] = p
            for v in adj[u]:
                if v != p:
                    dfs(v, u, d + 1)

        # The root's parent is set to itself for simplicity in binary lifting.
        dfs(1, 1, 0)

        # Step 2b: Fill the binary lifting table using dynamic programming.
        # The 2^j-th ancestor is the 2^(j-1)-th ancestor of the 2^(j-1)-th ancestor.
        for j in range(1, MAX_LOG):
            for i in range(1, n + 1):
                up[i][j] = up[up[i][j - 1]][j - 1]

        # Step 3: Implement the LCA function.
        def lca(u, v):
            # Ensure u is the deeper node to simplify logic.
            if depth[u] < depth[v]:
                u, v = v, u
            
            # Lift u up to the same level as v by jumping by powers of 2.
            diff = depth[u] - depth[v]
            for j in range(MAX_LOG - 1, -1, -1):
                if (diff >> j) & 1:
                    u = up[u][j]
            
            # If v was an ancestor of the original u, u is now equal to v.
            if u == v:
                return u
            
            # Lift u and v up together until their parents are the same.
            for j in range(MAX_LOG - 1, -1, -1):
                if up[u][j] != up[v][j]:
                    u = up[u][j]
                    v = up[v][j]
            
            # After the loop, u and v are direct children of the LCA.
            return up[u][0]

        # Step 4: Process each query.
        answers = []
        for u, v in queries:
            # Find the LCA of the two nodes.
            l = lca(u, v)
            
            # Calculate the path length (number of edges) between u and v using depths.
            # path_len = dist(root,u) + dist(root,v) - 2*dist(root,lca)
            path_len = depth[u] + depth[v] - 2 * depth[l]
            
            if path_len == 0:
                # If path length is 0 (u == v), cost is 0 (even). No ways to make it odd.
                answers.append(0)
            else:
                # For a path of length k, the sum of weights is odd if an odd number
                # of edges have weight 1 (odd). The number of ways to choose an odd
                # number of items from k is 2^(k-1).
                ans = pow(2, path_len - 1, MOD)
                answers.append(ans)
                
        return answers