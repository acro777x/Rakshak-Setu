/**
 * Rakshak Setu Sovereign VoIP - Dual HTTP Web Console & WebSocket Signaling Relay
 *
 * Facilitates peer-to-peer WebRTC DTLS-SRTP handshakes (SDP Offer/Answer & ICE candidate routing)
 * between the Laptop Calling Console and sovereign Android mobile endpoints.
 *
 * Features:
 * - Dual HTTP static file server on 0.0.0.0:8080 serving web_console/ (HTML, CSS, JS, WAV/MP3)
 * - HTTP Range request support for instant audio decoding in Web Audio API
 * - WebSocket signaling relay supporting register, offer, answer, candidate, hangup
 * - Phone number normalization (+91 normalization for Indian mobile telephony)
 * - Caller ID & Caller Name preservation and routing
 *
 * Usage:
 *   node signaling_server.js [port]
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const url = require('url');

const PORT = process.env.PORT || process.argv[2] || 8080;
const HOST = '0.0.0.0';
const WEB_CONSOLE_DIR = path.join(__dirname, 'web_console');

// MIME type lookup table
const MIME_TYPES = {
    '.html': 'text/html; charset=utf-8',
    '.htm': 'text/html; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.js': 'application/javascript; charset=utf-8',
    '.json': 'application/json; charset=utf-8',
    '.wav': 'audio/wav',
    '.mp3': 'audio/mpeg',
    '.ogg': 'audio/ogg',
    '.aac': 'audio/aac',
    '.m4a': 'audio/mp4',
    '.svg': 'image/svg+xml',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg',
    '.ico': 'image/x-icon'
};

/**
 * Normalizes Indian and international phone numbers into E.164 +91 format.
 * Examples:
 *   "9876543210"        -> "+919876543210"
 *   "09876543210"       -> "+919876543210"
 *   "+91 98765 43210"   -> "+919876543210"
 *   "919876543210"      -> "+919876543210"
 *   "+91-98765-43210"   -> "+919876543210"
 *   "laptop_console"    -> "laptop_console"
 */
function normalizePhoneNumber(rawId) {
    if (!rawId || typeof rawId !== 'string') return '';
    const trimmed = rawId.trim();
    const digits = trimmed.replace(/\D/g, '');

    // Pure 10-digit Indian mobile number
    if (digits.length === 10) {
        return '+91' + digits;
    }
    // 11-digit leading 0 (trunk prefix)
    if (digits.length === 11 && digits.startsWith('0')) {
        return '+91' + digits.slice(1);
    }
    // 12-digit leading 91
    if (digits.length === 12 && digits.startsWith('91')) {
        return '+' + digits;
    }
    // +91 with more or exactly 10 digits
    if (trimmed.startsWith('+91') && digits.length >= 10) {
        return '+91' + digits.slice(-10);
    }
    // Any international + number
    if (trimmed.startsWith('+') && digits.length >= 7) {
        return '+' + digits;
    }
    // Non-phone identifier or fallback
    return trimmed;
}

/**
 * HTTP Request Handler: Serves static files from web_console directory.
 */
const server = http.createServer((req, res) => {
    // Add CORS headers to enable audio fetching and cross-origin tools
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Range');
    res.setHeader('Access-Control-Expose-Headers', 'Content-Range, Accept-Ranges, Content-Length');

    if (req.method === 'OPTIONS') {
        res.writeHead(204);
        res.end();
        return;
    }

    if (req.method !== 'GET' && req.method !== 'HEAD') {
        res.writeHead(405, { 'Content-Type': 'text/plain' });
        res.end('Method Not Allowed\n');
        return;
    }

    const parsedUrl = new URL(req.url, `http://${req.headers.host || 'localhost:8080'}`);
    let pathname = decodeURIComponent(parsedUrl.pathname);

    // Default to index.html
    if (pathname === '/' || pathname === '') {
        pathname = '/index.html';
    }

    // Health check endpoint
    if (pathname === '/health' || pathname === '/status') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            status: 'ok',
            service: 'Rakshak Setu Sovereign Signaling Relay',
            uptime: process.uptime(),
            timestamp: new Date().toISOString(),
            connectedPeers: peerRegistry.size
        }));
        return;
    }

    // Resolve safe file path within WEB_CONSOLE_DIR
    const safePath = path.normalize(pathname).replace(/^(\.\.[\/\\])+/, '');
    const filePath = path.join(WEB_CONSOLE_DIR, safePath);

    // Prevent path traversal
    if (!filePath.startsWith(WEB_CONSOLE_DIR)) {
        res.writeHead(403, { 'Content-Type': 'text/plain' });
        res.end('Forbidden: Access outside web_console boundary\n');
        return;
    }

    fs.stat(filePath, (err, stats) => {
        if (err || !stats.isFile()) {
            res.writeHead(404, { 'Content-Type': 'text/plain' });
            res.end(`404 Not Found: ${pathname}\n`);
            return;
        }

        const ext = path.extname(filePath).toLowerCase();
        const contentType = MIME_TYPES[ext] || 'application/octet-stream';
        const fileSize = stats.size;
        const range = req.headers.range;

        // Support HTTP Range requests for seamless audio streaming & seeking
        if (range) {
            const parts = range.replace(/bytes=/, '').split('-');
            const start = parseInt(parts[0], 10);
            const end = parts[1] ? parseInt(parts[1], 10) : fileSize - 1;

            if (start >= fileSize || end >= fileSize || start > end) {
                res.writeHead(416, {
                    'Content-Range': `bytes */${fileSize}`,
                    'Content-Type': 'text/plain'
                });
                res.end('Requested Range Not Satisfiable\n');
                return;
            }

            const chunksize = (end - start) + 1;
            res.writeHead(206, {
                'Content-Range': `bytes ${start}-${end}/${fileSize}`,
                'Accept-Ranges': 'bytes',
                'Content-Length': chunksize,
                'Content-Type': contentType
            });

            if (req.method === 'HEAD') {
                res.end();
            } else {
                const stream = fs.createReadStream(filePath, { start, end });
                stream.pipe(res);
            }
        } else {
            res.writeHead(200, {
                'Content-Length': fileSize,
                'Accept-Ranges': 'bytes',
                'Content-Type': contentType,
                'Cache-Control': ext.match(/\.(wav|mp3|ogg|png|svg)$/) ? 'public, max-age=3600' : 'no-cache'
            });

            if (req.method === 'HEAD') {
                res.end();
            } else {
                const stream = fs.createReadStream(filePath);
                stream.pipe(res);
            }
        }
    });
});

// Peer registry: identifier -> WebSocket instance
const peerRegistry = new Map();
// Reverse registry: WebSocket instance -> Set of registered identifiers
const socketToIds = new Map();

let WebSocketServer;
try {
    WebSocketServer = require('ws').Server;
} catch (e) {
    console.warn('[WARN] Package "ws" is not yet loaded. Run `npm install` in server directory.');
}

if (WebSocketServer) {
    const wss = new WebSocketServer({ server });

    wss.on('connection', (ws, req) => {
        const clientIp = req.socket.remoteAddress;
        socketToIds.set(ws, new Set());

        ws.on('message', (message) => {
            try {
                const data = JSON.parse(message.toString());
                const type = data.type;

                switch (type) {
                    case 'register': {
                        const rawId = data.clientId || data.id || '';
                        if (!rawId) {
                            ws.send(JSON.stringify({ type: 'error', message: 'Missing clientId in register request' }));
                            return;
                        }

                        const normalizedId = normalizePhoneNumber(rawId);
                        const role = data.role || 'peer';

                        // Register both normalized ID and raw ID for flexible lookup
                        peerRegistry.set(normalizedId, ws);
                        peerRegistry.set(rawId, ws);

                        const ids = socketToIds.get(ws);
                        if (ids) {
                            ids.add(normalizedId);
                            ids.add(rawId);
                        }

                        console.log(`[REGISTER] Client '${rawId}' (normalized: '${normalizedId}', role: '${role}') registered from ${clientIp}. Total active keys: ${peerRegistry.size}`);

                        ws.send(JSON.stringify({
                            type: 'registered',
                            clientId: normalizedId,
                            rawId: rawId,
                            role: role,
                            status: 'ok',
                            serverTime: new Date().toISOString()
                        }));
                        break;
                    }

                    case 'offer':
                    case 'answer':
                    case 'candidate':
                    case 'hangup': {
                        const targetId = data.to;
                        if (!targetId) {
                            ws.send(JSON.stringify({ type: 'error', message: `Missing 'to' recipient field for ${type}` }));
                            return;
                        }

                        // Try exact match first, then normalized match, then last-10-digits match
                        const normalizedTarget = normalizePhoneNumber(targetId);
                        let targetWs = peerRegistry.get(targetId) || peerRegistry.get(normalizedTarget);

                        if (!targetWs) {
                            const digitsTarget = targetId.replace(/\D/g, '').slice(-10);
                            if (digitsTarget.length === 10) {
                                for (const [key, candidateWs] of peerRegistry.entries()) {
                                    if (key.replace(/\D/g, '').slice(-10) === digitsTarget) {
                                        targetWs = candidateWs;
                                        break;
                                    }
                                }
                            }
                        }

                        if (targetWs && targetWs.readyState === 1 /* OPEN */) {
                            // Ensure normalized from and caller info is preserved
                            if (data.from) {
                                data.fromNormalized = normalizePhoneNumber(data.from);
                            }
                            targetWs.send(JSON.stringify(data));
                            console.log(`[ROUTE] Forwarded '${type}' from [${data.from || 'unknown'}] -> [${targetId}] (callerName: ${data.callerName || 'N/A'})`);
                        } else {
                            console.warn(`[ROUTE] Target peer '${targetId}' (normalized: '${normalizedTarget}') not reachable or offline.`);
                            ws.send(JSON.stringify({
                                type: 'peer_offline',
                                to: targetId,
                                normalizedTo: normalizedTarget,
                                originalType: type,
                                message: `Peer ${targetId} is currently not registered or offline.`
                            }));
                        }
                        break;
                    }

                    case 'ping': {
                        ws.send(JSON.stringify({ type: 'pong', timestamp: Date.now() }));
                        break;
                    }

                    default:
                        console.log(`[WARN] Unknown message type: ${type}`);
                }
            } catch (err) {
                console.error('[ERROR] Malformed JSON signaling payload:', err.message);
                ws.send(JSON.stringify({ type: 'error', message: 'Malformed JSON payload: ' + err.message }));
            }
        });

        ws.on('close', () => {
            const ids = socketToIds.get(ws);
            if (ids) {
                for (const id of ids) {
                    peerRegistry.delete(id);
                }
                socketToIds.delete(ws);
                console.log(`[DISCONNECT] Peer disconnected. Removed registered keys: ${Array.from(ids).join(', ')}. Remaining: ${peerRegistry.size}`);
            }
        });

        ws.on('error', (err) => {
            console.error('[WS ERROR]', err.message);
        });
    });
}

server.listen(PORT, HOST, () => {
    console.log(`================================================================`);
    console.log(`  Rakshak Setu Sovereign WebRTC Signaling Relay & Web Console`);
    console.log(`  Listening on        : http://${HOST}:${PORT}`);
    console.log(`  Web Console URL     : http://localhost:${PORT}`);
    console.log(`  WebSocket URL       : ws://localhost:${PORT}`);
    console.log(`  Serving files from  : ${WEB_CONSOLE_DIR}`);
    console.log(`================================================================`);
});

module.exports = { server, normalizePhoneNumber };
