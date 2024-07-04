function linearSearch(arr, target) {
    for (let i = 0; i < arr.length; i++) {
        if (arr[i] === target) {
            return i; // Target found, return the index
        }
    }
    return -1; // Target not found, return -1
}

// Example usage:
const array = [10, 20, 30, 40, 50];
const target = 30;
const index = linearSearch(array, target);

if (index !== -1) {
    console.log(`Element found at index ${index}`);
} else {
    console.log('Element not found in the array');
}
