// Import the Express module
const express = require('express');

// Create an instance of an Express application
const app = express();

// Define a port to listen on
const port = 3000;

// Define a simple route
app.get('/', (req, res) => {
  res.send('Hello, World!');
});

// Define another route
app.get('/about', (req, res) => {
  res.send('About Page');
});

// Start the server and listen on the defined port
app.listen(port, () => {
  console.log(`Server is running on http://localhost:${port}`);
});
