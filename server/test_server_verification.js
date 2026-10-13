/**
 * Automated Verification Script for Milestone 4:
 * Tests Dual HTTP Server and WebSocket Signaling Relay Routing
 */

const http = require('http');
const WebSocket = require('ws');

const SERVER_URL = 'http://localhost:8080';
const WS_URL = 'ws://localhost:8080';

async function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

function httpGet(path, headers = {}) {
    return new Promise((resolve, reject) => {
        const url = SERVER_URL + path;
        http.get(url, { headers }, (res) => {
            let data = [];
            res.on('data', chunk => data.push(chunk));
            res.on('end', () => {
                const buffer = Buffer.concat(data);
                resolve({
                    statusCode: res.statusCode,
                    headers: res.headers,
                    body: buffer.toString('utf-8'),
                    rawLength: buffer.length
                });
            });
        }).on('error', reject);
    });
}

async function runTests() {
    console.log('================================================================');
    console.log('  Milestone 4: Dual HTTP & WebSocket Relay Automated Test Suite');
    console.log('================================================================\n');

    let allPassed = true;

    // 1. Test HTTP GET /
    try {
        console.log('[TEST 1] Testing HTTP GET / (Web Console)...');
        const res = await httpGet('/');
        if (res.statusCode === 200 && res.body.includes('Rakshak Setu VoIP Calling Rig')) {
            console.log(`  PASS: GET / returned 200 OK (${res.rawLength} bytes, content-type: ${res.headers['content-type']})`);
        } else {
            console.error(`  FAIL: Unexpected response for /: code=${res.statusCode}`);
            allPassed = false;
        }
    } catch (err) {
        console.error('  FAIL: HTTP GET / error:', err.message);
        allPassed = false;
    }

    // 2. Test HTTP GET Audio Assets
    const audioAssets = [
        '/assets/audio/genuine_speaker.wav',
        '/assets/audio/cloned_speaker.wav',
        '/assets/audio/digital_arrest_extortion.wav'
    ];

    for (const asset of audioAssets) {
        try {
            console.log(`[TEST 2] Testing HTTP GET ${asset}...`);
            const res = await httpGet(asset);
            if (res.statusCode === 200 && res.headers['content-type'] === 'audio/wav' && res.rawLength > 100000) {
                console.log(`  PASS: ${asset} returned 200 OK (${res.rawLength} bytes, content-type: audio/wav)`);
            } else {
                console.error(`  FAIL: Unexpected response for ${asset}: code=${res.statusCode}, len=${res.rawLength}`);
                allPassed = false;
            }
        } catch (err) {
            console.error(`  FAIL: HTTP GET ${asset} error:`, err.message);
            allPassed = false;
        }
    }

    // 3. Test HTTP Range Request (Partial Content 206)
    try {
        console.log('[TEST 3] Testing HTTP Range Request (bytes=0-1023)...');
        const res = await httpGet('/assets/audio/genuine_speaker.wav', { 'Range': 'bytes=0-1023' });
        if (res.statusCode === 206 && res.rawLength === 1024 && res.headers['content-range']) {
            console.log(`  PASS: Range request returned 206 Partial Content (Range: ${res.headers['content-range']})`);
        } else {
            console.error(`  FAIL: Range request failed: code=${res.statusCode}, len=${res.rawLength}`);
            allPassed = false;
        }
    } catch (err) {
        console.error('  FAIL: Range request error:', err.message);
        allPassed = false;
    }

    // 4. Test WebSocket Registration & Peer-to-Peer Signaling Handshake
    console.log('\n[TEST 4] Testing WebSocket Signaling Relay Handshake (+91 normalization)...');
    try {
        const clientA = new WebSocket(WS_URL);
        const clientB = new WebSocket(WS_URL);

        await new Promise((resolve) => {
            let openCount = 0;
            const onOpen = () => { if (++openCount === 2) resolve(); };
            clientA.on('open', onOpen);
            clientB.on('open', onOpen);
        });

        console.log('  Both Client A (Laptop) and Client B (Phone) WebSocket connections established.');

        // Client A registers as "+91 98765 00001"
        // Client B registers as "+919876543210"
        let regA = false, regB = false;

        const pA_reg = new Promise(resolve => {
            clientA.on('message', (msg) => {
                const data = JSON.parse(msg.toString());
                if (data.type === 'registered') {
                    regA = true;
                    resolve(data);
                }
            });
        });

        const pB_reg = new Promise(resolve => {
            clientB.on('message', (msg) => {
                const data = JSON.parse(msg.toString());
                if (data.type === 'registered') {
                    regB = true;
                    resolve(data);
                }
            });
        });

        clientA.send(JSON.stringify({ type: 'register', clientId: '+91 98765 00001', role: 'caller' }));
        clientB.send(JSON.stringify({ type: 'register', clientId: '+919876543210', role: 'callee' }));

        const ackA = await pA_reg;
        const ackB = await pB_reg;
        console.log(`  Client A registered: clientId='${ackA.clientId}'`);
        console.log(`  Client B registered: clientId='${ackB.clientId}'`);

        // Test Offer routing with +91 normalization and callerName
        const pB_offer = new Promise(resolve => {
            clientB.on('message', (msg) => {
                const data = JSON.parse(msg.toString());
                if (data.type === 'offer') resolve(data);
            });
        });

        console.log('  Client A dispatching SDP offer with unnormalized target number "9876543210"...');
        clientA.send(JSON.stringify({
            type: 'offer',
            from: '+91 98765 00001',
            to: '9876543210', // Unnormalized callee number
            callerName: 'DCP Cyber Crime (Digital Arrest Cell)',
            sdp: 'v=0\r\no=- 12345 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n'
        }));

        const receivedOffer = await pB_offer;
        if (receivedOffer.callerName === 'DCP Cyber Crime (Digital Arrest Cell)' && receivedOffer.sdp.includes('m=audio')) {
            console.log(`  PASS: Client B received offer with preserved callerName: '${receivedOffer.callerName}'`);
        } else {
            console.error('  FAIL: Client B received invalid offer:', receivedOffer);
            allPassed = false;
        }

        // Test Answer routing
        const pA_answer = new Promise(resolve => {
            clientA.on('message', (msg) => {
                const data = JSON.parse(msg.toString());
                if (data.type === 'answer') resolve(data);
            });
        });

        clientB.send(JSON.stringify({
            type: 'answer',
            from: '+919876543210',
            to: '+91 98765 00001',
            sdp: 'v=0\r\no=- 54321 2 IN IP4 127.0.0.1\r\ns=-\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n'
        }));

        const receivedAnswer = await pA_answer;
        if (receivedAnswer.from === '+919876543210' && receivedAnswer.sdp.includes('m=audio')) {
            console.log(`  PASS: Client A received SDP answer from callee.`);
        } else {
            console.error('  FAIL: Client A received invalid answer:', receivedAnswer);
            allPassed = false;
        }

        // Test Candidate routing
        const pB_cand = new Promise(resolve => {
            clientB.on('message', (msg) => {
                const data = JSON.parse(msg.toString());
                if (data.type === 'candidate') resolve(data);
            });
        });

        clientA.send(JSON.stringify({
            type: 'candidate',
            from: '+91 98765 00001',
            to: '+91 98765 43210',
            sdpMid: '0',
            sdpMLineIndex: 0,
            sdpCandidate: 'candidate:1 1 UDP 2122260223 192.168.1.100 54321 typ host'
        }));

        const receivedCandidate = await pB_cand;
        if (receivedCandidate.sdpMid === '0' && receivedCandidate.sdpCandidate.includes('typ host')) {
            console.log(`  PASS: Client B received ICE candidate successfully.`);
        } else {
            console.error('  FAIL: Client B received invalid candidate:', receivedCandidate);
            allPassed = false;
        }

        // Test Hangup routing
        const pB_hangup = new Promise(resolve => {
            clientB.on('message', (msg) => {
                const data = JSON.parse(msg.toString());
                if (data.type === 'hangup') resolve(data);
            });
        });

        clientA.send(JSON.stringify({
            type: 'hangup',
            from: '+91 98765 00001',
            to: '+91 98765 43210',
            reason: 'scam_threat_intercepted'
        }));

        const receivedHangup = await pB_hangup;
        if (receivedHangup.reason === 'scam_threat_intercepted') {
            console.log(`  PASS: Client B received hangup with reason: '${receivedHangup.reason}'`);
        } else {
            console.error('  FAIL: Client B received invalid hangup:', receivedHangup);
            allPassed = false;
        }

        clientA.close();
        clientB.close();
    } catch (err) {
        console.error('  FAIL: WebSocket test error:', err.message);
        allPassed = false;
    }

    console.log('\n================================================================');
    if (allPassed) {
        console.log('  ALL VERIFICATION TESTS PASSED SUCCESSFULLY! (100% OK)');
    } else {
        console.error('  SOME TESTS FAILED. Inspect errors above.');
        process.exit(1);
    }
    console.log('================================================================');
}

runTests().catch(err => {
    console.error('Fatal test runner error:', err);
    process.exit(1);
});
