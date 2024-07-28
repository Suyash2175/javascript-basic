important// Function to check if a number is prime
function isPrime(num) {
    if (num <= 1) return false; // Numbers less than or equal to 1 are not prime
    if (num <= 3) return true;  // 2 and 3 are prime numbers
    if (num % 2 === 0 || num % 3 === 0) return false; // Multiples of 2 and 3 are not prime

    for (let i = 5; i * i <= num; i += 6) {
        if (num % i === 0 || num % (i + 2) === 0) return false;
    }
    return true;
}

// Function to sum all prime numbers in an array
function sumOfPrimes(arr) {
    return arr
        .filter(isPrime)    // Filter out only the prime numbers
        .reduce((acc, num) => acc + num, 0); // Sum up all the prime numbers
}

// Example usage:
const numbers = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
const sum = sumOfPrimes(numbers);
console.log(`Sum of prime numbers: ${sum}`); // Output will be 17 (2 + 3 + 5 + 7)
