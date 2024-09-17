function flatten(arr) {
    return arr.reduce((acc, val) => 
        Array.isArray(val) ? acc.concat(flatten(val)) : acc.concat(val), 
    []);
}

// Example usage:
const nestedArray = [1, [2, [3, [4, 5]]], 6];
console.log(flatten(nestedArray)); // [1, 2, 3, 4, 5, 6]
