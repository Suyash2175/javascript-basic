function delay() {
    return new Promise((resolve, reject) => {
        setTimeout(() => {
            resolve('Success');
        }, 2000);
    });
}

delay().then((message) => {
    console.log(message); // "Success" after 2 seconds
});
