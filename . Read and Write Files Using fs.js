// read and write file using javascript 
const fs = require('fs');

function readAndWriteFiles(inputPath, outputPath) {
    fs.readFile(inputPath, 'utf8', (err, data) => {
        if (err) {
            console.error(err);
            return;
        }
        fs.writeFile(outputPath, data, (err) => {
            if (err) {
                console.error(err);
                return;
            }
            console.log('File written successfully');
        });
    });
}

// Usage
readAndWriteFiles('input.txt', 'output.txt');
