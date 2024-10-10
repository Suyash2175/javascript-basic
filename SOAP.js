const soap = require('soap');
const express = require('express');
const app = express();

const myService = {
  MyService: {
    MyPort: {
      MyFunction: (args) => {
        return { message: `Hello ${args.name}` };
      }
    }
  }
};

const xml = require('fs').readFileSync('myservice.wsdl', 'utf8');

app.listen(8000, () => {
  soap.listen(app, '/wsdl', myService, xml, () => {
    console.log('SOAP server running on port 8000');
  });
});
