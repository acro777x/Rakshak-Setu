/**
 * Rakshak Setu Sovereign VoIP - Lightweight WebSocket Signaling Relay Server
 *
 * Facilitates peer-to-peer WebRTC DTLS-SRTP handshakes (SDP Offer/Answer & ICE candidate routing)
 * between sovereign Android endpoints.
 *
 * Usage:
 *   node signaling_server.js [port]
 */

const http = require('http');
const server = http.createServer((req, res) => {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('Rakshak Setu Sovereign VoIP Signaling Relay Running\n');
});

const PORT = process.env.PORT || process.argv[2] || 8080;

let WebSocketServer;
try {
    WebSocketServer = require('ws').Server;
} catch (e) {
    console.log('[INFO] The "ws" package is optional. To run standalone: npm install ws');
}

if (WebSocketServer) {
    const wss = new WebSocketServer({ server });
    const clients = new Map(); // clientId -> ws

    wss.on('connection', (ws) => {
        let registeredId = null;

        ws.on('message', (message) => {
            try {
                const data = JSON.parse(message.toString());
                const type = data.type;

                switch (type) {
                    case 'register':
                        registeredId = data.clientId;
                        clients.set(registeredId, ws);
                        console.log(`[REGISTER] Client ${registeredId} connected. (Total: ${clients.size})`);
                        break;

                    case 'offer':
                    case 'answer':
                    case 'candidate':
                    case 'hangup':
                        const targetId = data.to;
                        const targetWs = clients.get(targetId);
                        if (targetWs && targetWs.readyState === 1 /* OPEN */) {
                            targetWs.send(JSON.stringify(data));
                            console.log(`[ROUTE] Forwarded '${type}' from ${data.from} -> ${targetId}`);
                        } else {
                            console.warn(`[ROUTE] Target client ${targetId} not found or disconnected.`);
                        }
                        break;

                    default:
                        console.log(`[WARN] Unknown message type: ${type}`);
                }
            } catch (err) {
                console.error('[ERROR] Malformed JSON:', err.message);
            }
        });

        ws.on('close', () => {
            if (registeredId) {
                clients.delete(registeredId);
                console.log(`[DISCONNECT] Client ${registeredId} disconnected.`);
            }
        });

        ws.on('error', (err) => {
            console.error('[WS ERROR]', err.message);
        });
    });
}

server.listen(PORT, () => {
    console.log(`================================================================`);
    console.log(`  Rakshak Setu Sovereign WebRTC Signaling Relay Server`);
    console.log(`  Listening on port : ${PORT}`);
    console.log(`  WebSocket URL     : ws://localhost:${PORT}`);
    console.log(`================================================================`);
});
