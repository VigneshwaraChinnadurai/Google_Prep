class Solution:
    def zigZagArrays(self, n: int, l: int, r: int) -> int:
        MOD = 10**9 + 7
        m = r - l + 1

        def mat_mul(A, B):
            C = [[0] * m for _ in range(m)]
            for i in range(m):
                for j in range(m):
                    for k in range(m):
                        C[i][j] = (C[i][j] + A[i][k] * B[k][j]) % MOD
            return C

        def mat_pow(A, p):
            res = [[0] * m for _ in range(m)]
            for i in range(m):
                res[i][i] = 1
            
            base = A
            while p > 0:
                if p % 2 == 1:
                    res = mat_mul(res, base)
                base = mat_mul(base, base)
                p //= 2
            return res

        def mat_vec_mul(A, v):
            res = [0] * m
            for i in range(m):
                for j in range(m):
                    res[i] = (res[i] + A[i][j] * v[j]) % MOD
            return res
        
        def reverse_matrix(M):
            # This is equivalent to J * M * J where J is the anti-diagonal identity matrix
            M_rev_rows = M[::-1]
            return [row[::-1] for row in M_rev_rows]

        # A[i][j] = 1 if j < i, represents transition from DOWN to UP
        A = [[(1 if j < i else 0) for j in range(m)] for i in range(m)]
        # B[i][j] = 1 if j > i, represents transition from UP to DOWN
        B = [[(1 if j > i else 0) for j in range(m)] for i in range(m)]
        
        # C = A * B, C[i][k] = min(i, k)
        # This matrix is for two steps: DOWN -> UP -> DOWN
        C = [[min(i, k) for k in range(m)] for i in range(m)]
        
        # Initial vectors for length 2
        # V2_up[v]: num arrays of length 2 ending in (l+v) with a[0] < a[1]
        V2_up = [v for v in range(m)]
        # V2_down[v]: num arrays of length 2 ending in (l+v) with a[0] > a[1]
        V2_down = [m - 1 - v for v in range(m)]

        k = n - 2
        
        if k % 2 == 0: # k is even, n is even
            p = k // 2
            Cp = mat_pow(C, p)
            # D = J*C*J, so D^p = J*C^p*J
            Dp = reverse_matrix(Cp)
            
            Vn_up = mat_vec_mul(Cp, V2_up)
            Vn_down = mat_vec_mul(Dp, V2_down)
        else: # k is odd, n is odd
            p = (k - 1) // 2
            Cp = mat_pow(C, p)
            Dp = reverse_matrix(Cp)
            
            # V_{n-1}_up = C^p * V2_up
            # V_{n-1}_down = D^p * V2_down
            V_intermediate_up = mat_vec_mul(Cp, V2_up)
            V_intermediate_down = mat_vec_mul(Dp, V2_down)
            
            # V_n_up = A * V_{n-1}_down
            # V_n_down = B * V_{n-1}_up
            Vn_up = mat_vec_mul(A, V_intermediate_down)
            Vn_down = mat_vec_mul(B, V_intermediate_up)

        total_sum = (sum(Vn_up) + sum(Vn_down)) % MOD
        return total_sum