const http = require('node:http');
let mode = 'success';
let voice = {command: '语音帮助'};
const requests = [];
http.createServer((req, res) => {
  let body = '';
  req.on('data', part => body += part);
  req.on('end', () => {
    res.setHeader('Content-Type', 'application/json');
    if (req.url.startsWith('/control/')) {
      mode = req.url.slice(9); res.end('{}'); return;
    }
    if (req.url === '/voice') {
      if (req.method === 'POST') voice = JSON.parse(body);
      res.end(JSON.stringify(voice)); return;
    }
    if (req.url === '/requests') { res.end(JSON.stringify(requests)); return; }
    if (req.url === '/api/chat') {
      const payload = JSON.parse(body);
      requests.push(payload);
      console.log(JSON.stringify({mode, model: payload.model, hasInstruction: payload.messages[0].content.includes('只读价格')}));
      const currentMode = mode;
      const timer = setTimeout(() => {
        if (currentMode === 'error') { res.writeHead(401); res.end('{}'); }
        else res.end(JSON.stringify({message: {content: '模拟讲解：价格是123元。'}}));
      }, currentMode === 'slow' ? 8000 : 200);
      res.on('close', () => clearTimeout(timer));
      return;
    }
    res.writeHead(404); res.end('{}');
  });
}).listen(11435, '127.0.0.1', () => console.log('Voice mock listening on 11435'));
