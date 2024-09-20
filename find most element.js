function mostFrequent(arr) {
    const map = {};
    let maxCount = 0;
    let mostFrequentElement = null;
    
    for (const element of arr) {
        map[element] = (map[element] || 0) + 1;
        
        if (map[element] > maxCount) {
            maxCount = map[element];
            mostFrequentElement = element;
        }
    }
    
    return mostFrequentElement;
}

console.log(mostFrequent([1, 3, 2, 1, 4, 1, 2, 3, 3])); // Output: 1
