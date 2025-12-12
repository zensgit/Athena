const http = require('http');
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..', 'build');
const port = process.env.PORT || 3200;

const mime = {
  '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css',
  '.json': 'application/json', '.png': 'image/png', '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml', '.woff2': 'font/woff2', '.woff': 'font/woff', '.ttf': 'font/ttf'
};

http.createServer((req, res) => {
  let filePath = path.join(root, req.url.split('?')[0]);
  if (req.url === '/' || !path.extname(filePath)) {
    filePath = path.join(root, 'index.html');
  }
  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404); res.end(); return;
    }
    res.writeHead(200, { 'Content-Type': mime[path.extname(filePath)] || 'application/octet-stream' });
    res.end(data);
  });
}).listen(port, '127.0.0.1', () => {
  console.log(`Serving build on http://127.0.0.1:${port}`);
});
