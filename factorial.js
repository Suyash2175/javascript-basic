function factorialIterative(n) {
  if (n < 0) {
    return -1; // Factorial is not defined for negative numbers
  }
  let result = 1;
  for (let i = 1; i <= n; i++) {
    result *= i;
  }
  return result;
}

// Test the iterative method
console.log(`Factorial of 5 (iterative): ${factorialIterative(5)}`);
