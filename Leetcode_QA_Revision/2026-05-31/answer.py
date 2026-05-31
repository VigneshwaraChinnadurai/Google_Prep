from typing import List

class Solution:
    def asteroidsDestroyed(self, mass: int, asteroids: List[int]) -> bool:
        # The optimal strategy is to collide with asteroids in increasing order of their mass.
        # This greedy approach ensures that at each step, we face the easiest possible
        # challenge (`planet_mass >= asteroid_mass`). Successfully destroying smaller
        # asteroids increases the planet's mass, making it more capable of handling
        # larger asteroids later. If this strategy fails, no other order can succeed.
        
        asteroids.sort()
        
        current_mass = mass
        
        for asteroid_mass in asteroids:
            # Check if the planet can destroy the current asteroid.
            if current_mass < asteroid_mass:
                # If the mass is insufficient for the smallest remaining asteroid,
                # it's impossible to destroy all of them.
                return False
            
            # If successful, the planet gains the asteroid's mass.
            current_mass += asteroid_mass
            
        # If the loop completes, all asteroids were destroyed.
        return True