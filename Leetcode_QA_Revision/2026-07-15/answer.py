import math

class Solution:
    def gcdOfOddEvenSums(self, n: int) -> int:
        """
        Calculates the GCD of the sum of the first n odd numbers and the sum of the first n even numbers.

        The problem can be solved by first finding mathematical formulas for the two sums.

        1. Sum of the first n positive odd numbers (sumOdd):
           The sequence is 1, 3, 5, ..., (2n - 1).
           This is an arithmetic progression. The sum of the first n odd numbers is a well-known result: n^2.
           sumOdd = n^2

        2. Sum of the first n positive even numbers (sumEven):
           The sequence is 2, 4, 6, ..., 2n.
           This can be seen as 2 * (1 + 2 + 3 + ... + n).
           The sum of the first n positive integers is n * (n + 1) / 2.
           So, sumEven = 2 * [n * (n + 1) / 2] = n * (n + 1).

        3. Compute the GCD:
           We need to find GCD(sumOdd, sumEven), which is GCD(n^2, n * (n + 1)).

        4. Simplify the GCD expression:
           Using the property GCD(a*b, a*c) = a * GCD(b, c), we can factor out 'n':
           GCD(n * n, n * (n + 1)) = n * GCD(n, n + 1)

        5. Final step:
           The numbers 'n' and 'n + 1' are consecutive integers. The greatest common
           divisor of any two consecutive integers is always 1.
           This is because any common divisor of n and n+1 must also divide their
           difference, which is (n + 1) - n = 1. The only positive divisor of 1 is 1.
           So, GCD(n, n + 1) = 1.

        6. Conclusion:
           Substituting this back, the result is n * 1 = n.
        """
        return n