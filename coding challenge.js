// //CODING//
// //1. FIND SIMPLE INTERST
// //WRITE A FUNCTION WHICH TAKES PRINCIPAL RATE OF INTEREST AND TIME
// //AS INPUTE AND RETURN THE SIMPLE INTRESTE?
// //hint: (principal * rate of interest * time)/100

// function simpleintrest(p,r,t){
//     const simpleintrest=(p* r*t)/100;
//     return simpleintrest;
// };
// console.log(simpleintrest(10000,8,1));

// //WRITE A FUNCTION WHICH TAKES A LIST OF NUMBER
// //A LIST OF NUMBER AS INPUTS AND RETURN ITS SUM

// function sum( ...numbers){
//     let total=0;
//     for (i=0;i<numbers.length;i++){
        
//         total +=numbers[i];
//     }
//     return total;


// }

// //console.log(sum(1,2,3,4,5,8,9,77));
// //WRITE A FUNCTION WHICH TAKES A NUMBER AND PRINTS THE TABLES
//  function printtable(num){
//     for(let i =1;i<=10;i++){
//         console.log(`${num}* ${i}=${num* i}`)
//     }
//  }
// printtable(9);

// //4: WRITE A FUNCTION WHICH TAKES A ARRAY OF
// //NUMBER ,CALCULATE THE AVERAGE OF NUMBER
// //AND RETURNS IT


// function average(numbers){
//     let sum =0;
//     for(let i=0;i<numbers.length;i++){
//         sum +=numbers[i];
//     }
// const avg=sum/numbers.length
// return avg;
// }
// console.log(average([1,2,3,4,5]));

// //5< WRITE A FUNCTION WHICH FINDS A NUMBER IN A ARRAY  OF NUMBERS
// // RETURN TRUE IF THE NUMBER IS IN THE ARRAY OTHERWISE RETURN FALSE

// // reversed array

// function reversedarray(array){
//     let reversedarray=[];
//     for (let i=array.length-1;i>=0;i--){
//         console.log(array[i]);
//     }
//     return reversedarray
// }

// let array1=[1,2,3,4,5,6,7];
// reversedarray(array1);

/// NEW TOPICE
//CONTCAT TWO ARRAYS
let pet=['dog','cat','cow'];
let wild=['lion','tiger','wolf'];

let animal=pet.concat(wild);
console.log(animal)


//include methods

let array=['suyash','sss','shushant'];
let isavailable =array.includes('suyash');

console.log(isavailable)


// two string method
let array=[1,2,3,4,5,6];
let mystring =array.toString();
console.log(array)
console.log(mystring);


