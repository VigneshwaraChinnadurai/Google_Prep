import math

class Solution:
    def subsequencePairCount(self, nums: List[int]) -> int:
        MOD = 10**9 + 7
        MAX_VAL = 200

        freq = [0] * (MAX_VAL + 1)
        for x in nums:
            if x <= MAX_VAL:
                freq[x] += 1

        count_mult = [0] * (MAX_VAL + 1)
        for g in range(1, MAX_VAL + 1):
            for m in range(g, MAX_VAL + 1, g):
                count_mult[g] += freq[m]

        mu = [0] * (MAX_VAL + 1)
        lp = [0] * (MAX_VAL + 1)
        primes = []
        mu[1] = 1
        for i in range(2, MAX_VAL + 1):
            if lp[i] == 0:
                lp[i] = i
                primes.append(i)
                mu[i] = -1
            for p in primes:
                if p > lp[i] or i * p > MAX_VAL:
                    break
                lp[i * p] = p
                if p == lp[i]:
                    mu[i * p] = 0
                else:
                    mu[i * p] = -mu[i]

        pow2 = [1] * (len(nums) + 1)
        pow3 = [1] * (len(nums) + 1)
        for i in range(1, len(nums) + 1):
            pow2[i] = (pow2[i - 1] * 2) % MOD
            pow3[i] = (pow3[i - 1] * 3) % MOD

        def gcd(a, b):
            return math.gcd(a, b)

        def lcm(a, b):
            if a == 0 or b == 0: return 0
            res = abs(a * b) // gcd(a, b)
            return res

        total_ans = 0
        for g in range(1, MAX_VAL + 1):
            ans_g = 0
            for k1 in range(1, MAX_VAL // g + 1):
                mu1 = mu[k1]
                if mu1 == 0:
                    continue
                
                for k2 in range(1, MAX_VAL // g + 1):
                    mu2 = mu[k2]
                    if mu2 == 0:
                        continue

                    g1 = g * k1
                    g2 = g * k2
                    
                    L = lcm(g1, g2)
                    
                    c12 = count_mult[L] if L <= MAX_VAL else 0
                    c1 = count_mult[g1] - c12
                    c2 = count_mult[g2] - c12

                    term_F = (pow2[c1] * pow2[c2]) % MOD
                    term_F = (term_F * pow3[c12]) % MOD
                    
                    sub1 = (pow2[c2] * pow2[c12]) % MOD
                    term_F = (term_F - sub1 + MOD) % MOD
                    
                    sub2 = (pow2[c1] * pow2[c12]) % MOD
                    term_F = (term_F - sub2 + MOD) % MOD
                    
                    term_F = (term_F + 1) % MOD

                    if mu1 * mu2 == 1:
                        ans_g = (ans_g + term_F) % MOD
                    else:
                        ans_g = (ans_g - term_F + MOD) % MOD
            
            total_ans = (total_ans + ans_g) % MOD
            
        return total_ans