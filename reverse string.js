// Function to reverse a string interview questions 
function reverseString(str) {
    return str.split('').reverse().join('');
}

// Test the function
let originalString = "Hello, World!";
let reversedString = reverseString(originalString);

console.log(`Original String: ${originalString}`); // Output: "Hello, World!"
console.log(`Reversed String: ${reversedString}`); // Output: "!dlroW ,olleH"
