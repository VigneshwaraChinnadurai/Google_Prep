class Solution:
    def furthestDistanceFromOrigin(self, moves: str) -> int:
        """
        Calculates the furthest distance from the origin (0) on a number line.

        The problem asks for the maximum possible distance from the origin after a series
        of moves. The moves can be 'L' (left), 'R' (right), or '_' (wildcard, can be
        either left or right).

        To maximize the distance from the origin, we need to maximize the absolute value
        of our final position. This means we should try to go as far right as possible
        or as far left as possible.

        Let's analyze the contributions of each type of move:
        - 'L' moves decrease the position by 1.
        - 'R' moves increase the position by 1.
        - '_' moves can either decrease or increase the position by 1.

        The net displacement from the fixed 'L' and 'R' moves is `count('R') - count('L')`.
        
        The '_' moves are wildcards. To maximize our final distance, we should use all
        of them to move in the same direction, amplifying the initial displacement.

        Scenario 1: Maximize the final position (go furthest right).
        We treat all '_' as 'R'. The final position would be:
        `pos_right = count('R') - count('L') + count('_')`

        Scenario 2: Minimize the final position (go furthest left).
        We treat all '_' as 'L'. The final position would be:
        `pos_left = count('R') - count('L') - count('_')`

        The furthest distance from the origin is the maximum of the absolute values of
        these two potential final positions. This simplifies to:
        `abs(count('R') - count('L')) + count('_')`.
        """
        
        count_L = moves.count('L')
        count_R = moves.count('R')
        count_underscore = moves.count('_')
        
        # The net displacement from fixed moves ('L' and 'R').
        net_fixed_displacement = count_R - count_L
        
        # The maximum distance is the absolute net displacement from fixed moves,
        # plus the displacement from all wildcard moves used in the most
        # advantageous direction.
        return abs(net_fixed_displacement) + count_underscore