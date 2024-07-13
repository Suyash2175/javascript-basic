function processArray(numbers) {
  return numbers
    .filter(function(number) {
      return number % 2 === 0; // Filter out even numbers
    })
    .map(function(number) {
      return number * number; // Square each even number
    })
    .reduce(function(sum, number) {
      return sum + number; // Sum all the squared numbers
    }, 0); // Start with an initial sum of 0
}

// Example usage:
let numbers = [1, 2, 3, 4, 5, 6];
let result = processArray(numbers);
console.log(result); // Output: 56 (2^2 + 4^2 + 6^2 = 4 + 16 + 36 = 56)
