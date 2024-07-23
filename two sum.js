/**
 * @param {number[]} nums
 * @param {number} target
 * @return {number[]}
 */
function twoSum(nums, target) {
    const numToIndex = {};
    
    for (let i = 0; i < nums.length; i++) {
        const complement = target - nums[i];
        
        if (numToIndex.hasOwnProperty(complement)) {
            return [numToIndex[complement], i];
        }
        
        numToIndex[nums[i]] = i;
    }
    
    return []; // In case there is no solution, though the problem states there is exactly one solution
}

// Test the function with the example input
const nums = [2, 7, 11, 15];
const target = 9;
console.log(twoSum(nums, target)); // Output: [0, 1]
