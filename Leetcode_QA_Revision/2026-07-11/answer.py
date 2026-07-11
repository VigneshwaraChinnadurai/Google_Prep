import collections
from typing import List

class Solution:
    def countCompleteComponents(self, n: int, edges: List[List[int]]) -> int:
        # 1. Build an adjacency list representation of the graph.
        adj = collections.defaultdict(list)
        for u, v in edges:
            adj[u].append(v)
            adj[v].append(u)

        visited = [False] * n
        complete_components_count = 0

        # 2. Iterate through all vertices to find connected components.
        for i in range(n):
            if not visited[i]:
                # Found a new component. Start a traversal (e.g., BFS) to find all its nodes.
                component_nodes = []
                q = collections.deque([i])
                visited[i] = True
                
                while q:
                    u = q.popleft()
                    component_nodes.append(u)
                    for v in adj[u]:
                        if not visited[v]:
                            visited[v] = True
                            q.append(v)
                
                # 3. For the found component, check if it's complete.
                # A component with 'k' nodes is complete if every node in it has a degree of 'k-1'.
                # The problem's definition of a connected component ensures that a node's degree
                # in the whole graph is the same as its degree within its component.
                num_nodes = len(component_nodes)
                is_complete = True
                for node in component_nodes:
                    # The degree of a node is the number of its neighbors.
                    if len(adj[node]) != num_nodes - 1:
                        is_complete = False
                        break
                
                if is_complete:
                    complete_components_count += 1
        
        return complete_components_count