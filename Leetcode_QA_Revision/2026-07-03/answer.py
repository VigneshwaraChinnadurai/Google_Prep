import collections
from typing import List

class Solution:
    def findMaxPathScore(self, edges: List[List[int]], online: List[bool], k: int) -> int:
        n = len(online)

        def check(s: int) -> bool:
            """
            Checks if a path exists with a minimum edge cost of at least s.
            This is done by finding the shortest path in a subgraph and comparing its cost to k.
            The subgraph only contains online nodes and edges with cost >= s.
            """
            adj = [[] for _ in range(n)]
            in_degree = [0] * n
            
            for u, v, cost in edges:
                if cost >= s and online[u] and online[v]:
                    adj[u].append((v, cost))
                    in_degree[v] += 1

            # Topological sort (Kahn's algorithm)
            queue = collections.deque()
            for i in range(n):
                if in_degree[i] == 0:
                    queue.append(i)
            
            topo_order = []
            while queue:
                u = queue.popleft()
                topo_order.append(u)
                for v, _ in adj[u]:
                    in_degree[v] -= 1
                    if in_degree[v] == 0:
                        queue.append(v)
            
            # Shortest path calculation on the DAG
            dist = [float('inf')] * n
            dist[0] = 0

            for u in topo_order:
                if dist[u] == float('inf'):
                    continue
                
                for v, weight in adj[u]:
                    dist[v] = min(dist[v], dist[u] + weight)
            
            return dist[n-1] <= k

        # Binary search for the maximum score
        low = 0
        high = 10**9
        ans = -1

        while low <= high:
            mid = low + (high - low) // 2
            if check(mid):
                ans = mid
                low = mid + 1
            else:
                high = mid - 1
        
        return ans