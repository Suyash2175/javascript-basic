console.log('Start'); // Synchronous, executed first

setTimeout(() => {
    console.log('Timeout 1'); // Asynchronous, placed in the callback queue and executed after the synchronous code
}, 0);

setTimeout(() => {
    console.log('Timeout 2'); // Asynchronous, will also be placed in the callback queue
}, 1000);

console.log('End'); // Synchronous, executed immediately after 'Start'
