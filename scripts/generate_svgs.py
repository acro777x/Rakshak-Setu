import os

def create_radar_svg():
    return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 960 360" width="100%" height="360" style="background: #090d16; border-radius: 12px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
  <defs>
    <linearGradient id="radarGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#00f0ff" stop-opacity="0.6"/>
      <stop offset="50%" stop-color="#00f0ff" stop-opacity="0.1"/>
      <stop offset="100%" stop-color="#00f0ff" stop-opacity="0.0"/>
    </linearGradient>
    <linearGradient id="threatGlow" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#ff0055" stop-opacity="0.8"/>
      <stop offset="100%" stop-color="#ff7700" stop-opacity="0.8"/>
    </linearGradient>
    <filter id="glow" x="-20%" y="-20%" width="140%" height="140%">
      <feGaussianBlur stdDeviation="4" result="blur" />
      <feComposite in="SourceGraphic" in2="blur" operator="over" />
    </filter>
    <style>
      @keyframes radarSweep {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
      }
      @keyframes blinkPulse {
        0%, 100% { opacity: 1; }
        50% { opacity: 0.2; }
      }
      @keyframes waveFlow {
        0% { stroke-dashoffset: 0; }
        100% { stroke-dashoffset: -120; }
      }
      .sweep-beam {
        transform-origin: 180px 180px;
        animation: radarSweep 4s linear infinite;
      }
      .blink {
        animation: blinkPulse 1.6s ease-in-out infinite;
      }
      .wave-line-1 {
        stroke-dasharray: 8 4;
        animation: waveFlow 2s linear infinite;
      }
    </style>
  </defs>

  <!-- Background Grid and Accents -->
  <g stroke="#152033" stroke-width="1">
    <line x1="0" y1="60" x2="960" y2="60" />
    <line x1="0" y1="180" x2="960" y2="180" />
    <line x1="0" y1="300" x2="960" y2="300" />
    <line x1="360" y1="0" x2="360" y2="360" stroke="#1a2942" stroke-width="1.5" />
    <line x1="660" y1="0" x2="660" y2="360" stroke="#1a2942" stroke-width="1.5" />
  </g>

  <!-- RADAR SECTION (Left: 0 - 360) -->
  <g transform="translate(0, 0)">
    <circle cx="180" cy="180" r="130" fill="none" stroke="#1b2d4b" stroke-width="1.5" />
    <circle cx="180" cy="180" r="95" fill="none" stroke="#1e3860" stroke-width="1.5" stroke-dasharray="4 4" />
    <circle cx="180" cy="180" r="60" fill="none" stroke="#24497d" stroke-width="1.5" />
    <circle cx="180" cy="180" r="25" fill="none" stroke="#2b5b9e" stroke-width="1.5" />
    
    <line x1="40" y1="180" x2="320" y2="180" stroke="#1e3860" stroke-width="1" />
    <line x1="180" y1="40" x2="180" y2="320" stroke="#1e3860" stroke-width="1" />

    <!-- Radar Sweep with Dual CSS and SMIL Animation -->
    <g class="sweep-beam">
      <animateTransform
        attributeName="transform"
        type="rotate"
        from="0 180 180"
        to="360 180 180"
        dur="4s"
        repeatCount="indefinite" />
      <path d="M180,180 L310,180 A130,130 0 0,0 272,88 Z" fill="url(#radarGrad)" />
      <line x1="180" y1="180" x2="310" y2="180" stroke="#00f0ff" stroke-width="2" filter="url(#glow)" />
    </g>

    <circle cx="245" cy="135" r="4.5" fill="#ff0055" filter="url(#glow)" class="blink">
      <animate attributeName="opacity" values="1;0.2;1" dur="1.6s" repeatCount="indefinite" />
    </circle>
    <circle cx="245" cy="135" r="9" fill="none" stroke="#ff0055" stroke-width="1" class="blink">
      <animate attributeName="opacity" values="1;0.2;1" dur="1.6s" repeatCount="indefinite" />
    </circle>
    <text x="260" y="132" fill="#ff0055" font-size="10" font-weight="bold" letter-spacing="1">SYNTH_VOCODER (98.4%)</text>

    <circle cx="130" cy="220" r="3.5" fill="#00ff88" />
    <text x="80" y="238" fill="#00ff88" font-size="9" opacity="0.8">BONAFIDE_PCM</text>

    <text x="24" y="32" fill="#00f0ff" font-size="12" font-weight="700" letter-spacing="1.5">THREAT RADAR • LIVE ACOUSTIC SCAN</text>
    <text x="24" y="340" fill="#47688d" font-size="10" font-family="monospace">BANDWIDTH: 16kHz SincNet • LATENCY: 2.1s</text>
  </g>

  <!-- LIVE WAVEFORM AND SPECTRAL ANALYZER (Center: 360 - 660) -->
  <g transform="translate(380, 0)">
    <text x="0" y="32" fill="#ffb800" font-size="12" font-weight="700" letter-spacing="1.5">SPECTRAL HARMONICS &amp; PHASE JITTER</text>
    
    <path d="M 0,140 Q 30,80 60,140 T 120,140 T 180,110 T 240,165 T 260,140" fill="none" stroke="#00f0ff" stroke-width="2" filter="url(#glow)" class="wave-line-1">
      <animate attributeName="stroke-dashoffset" from="0" to="-120" dur="2s" repeatCount="indefinite" />
    </path>
    <path d="M 0,140 Q 25,190 50,140 T 100,170 T 150,130 T 200,150 T 260,140" fill="none" stroke="#ff0055" stroke-width="1.8" opacity="0.8" stroke-dasharray="4 2" />

    <!-- Equalizer Spectrum Bars with Native SMIL + CSS -->
    <g transform="translate(10, 260)">
      <rect x="0" y="-50" width="8" height="50" fill="#00f0ff" rx="2">
        <animate attributeName="height" values="20;50;25;60;20" dur="1.2s" repeatCount="indefinite" />
        <animate attributeName="y" values="-20;-50;-25;-60;-20" dur="1.2s" repeatCount="indefinite" />
      </rect>
      <rect x="16" y="-75" width="8" height="75" fill="#00f0ff" rx="2">
        <animate attributeName="height" values="35;75;40;80;35" dur="0.9s" repeatCount="indefinite" />
        <animate attributeName="y" values="-35;-75;-40;-80;-35" dur="0.9s" repeatCount="indefinite" />
      </rect>
      <rect x="32" y="-95" width="8" height="95" fill="#00f0ff" rx="2">
        <animate attributeName="height" values="45;95;60;100;45" dur="1.5s" repeatCount="indefinite" />
        <animate attributeName="y" values="-45;-95;-60;-100;-45" dur="1.5s" repeatCount="indefinite" />
      </rect>
      <rect x="48" y="-60" width="8" height="60" fill="#00f0ff" rx="2">
        <animate attributeName="height" values="25;60;35;70;25" dur="1.1s" repeatCount="indefinite" />
        <animate attributeName="y" values="-25;-60;-35;-70;-25" dur="1.1s" repeatCount="indefinite" />
      </rect>
      <rect x="64" y="-40" width="8" height="40" fill="#00f0ff" rx="2">
        <animate attributeName="height" values="15;40;25;50;15" dur="1.4s" repeatCount="indefinite" />
        <animate attributeName="y" values="-15;-40;-25;-50;-15" dur="1.4s" repeatCount="indefinite" />
      </rect>
      <rect x="80" y="-70" width="8" height="70" fill="#ffb800" rx="2">
        <animate attributeName="height" values="30;70;45;85;30" dur="1.0s" repeatCount="indefinite" />
        <animate attributeName="y" values="-30;-70;-45;-85;-30" dur="1.0s" repeatCount="indefinite" />
      </rect>
      <rect x="96" y="-85" width="8" height="85" fill="#ffb800" rx="2">
        <animate attributeName="height" values="40;85;50;90;40" dur="1.3s" repeatCount="indefinite" />
        <animate attributeName="y" values="-40;-85;-50;-90;-40" dur="1.3s" repeatCount="indefinite" />
      </rect>
      <rect x="112" y="-60" width="8" height="60" fill="#ffb800" rx="2">
        <animate attributeName="height" values="20;60;30;75;20" dur="0.8s" repeatCount="indefinite" />
        <animate attributeName="y" values="-20;-60;-30;-75;-20" dur="0.8s" repeatCount="indefinite" />
      </rect>
      <rect x="128" y="-105" width="8" height="105" fill="#ff0055" rx="2" filter="url(#glow)">
        <animate attributeName="height" values="50;105;65;115;50" dur="1.1s" repeatCount="indefinite" />
        <animate attributeName="y" values="-50;-105;-65;-115;-50" dur="1.1s" repeatCount="indefinite" />
      </rect>
      <rect x="144" y="-115" width="8" height="115" fill="#ff0055" rx="2" filter="url(#glow)">
        <animate attributeName="height" values="60;115;75;125;60" dur="1.4s" repeatCount="indefinite" />
        <animate attributeName="y" values="-60;-115;-75;-125;-60" dur="1.4s" repeatCount="indefinite" />
      </rect>
      <rect x="160" y="-85" width="8" height="85" fill="#ff0055" rx="2">
        <animate attributeName="height" values="35;85;45;95;35" dur="1.0s" repeatCount="indefinite" />
        <animate attributeName="y" values="-35;-85;-45;-95;-35" dur="1.0s" repeatCount="indefinite" />
      </rect>
      <rect x="176" y="-50" width="8" height="50" fill="#ff0055" rx="2">
        <animate attributeName="height" values="20;50;30;60;20" dur="1.2s" repeatCount="indefinite" />
        <animate attributeName="y" values="-20;-50;-30;-60;-20" dur="1.2s" repeatCount="indefinite" />
      </rect>
      <rect x="192" y="-35" width="8" height="35" fill="#00ff88" rx="2">
        <animate attributeName="height" values="15;35;20;45;15" dur="1.5s" repeatCount="indefinite" />
        <animate attributeName="y" values="-15;-35;-20;-45;-15" dur="1.5s" repeatCount="indefinite" />
      </rect>
      <rect x="208" y="-20" width="8" height="20" fill="#00ff88" rx="2">
        <animate attributeName="height" values="10;20;15;30;10" dur="0.9s" repeatCount="indefinite" />
        <animate attributeName="y" values="-10;-20;-15;-30;-10" dur="0.9s" repeatCount="indefinite" />
      </rect>
    </g>

    <text x="0" y="295" fill="#8da2c0" font-size="10" font-family="monospace">SincNet Bandpass: 60Hz - 7.8kHz</text>
    <text x="0" y="312" fill="#ff0055" font-size="10" font-family="monospace" font-weight="bold">Phase Discontinuity: DETECTED (p &lt; 0.001)</text>
    <text x="0" y="340" fill="#47688d" font-size="10" font-family="monospace">BUFFER: SPSC Lock-Free RAM (Zero Disk IO)</text>
  </g>

  <!-- WALD SPRT DECISION ENGINE (Right: 660 - 960) -->
  <g transform="translate(680, 0)">
    <text x="0" y="32" fill="#00ff88" font-size="12" font-weight="700" letter-spacing="1.5">WALD'S SPRT CONFIDENCE</text>
    
    <rect x="0" y="55" width="255" height="80" rx="8" fill="#111927" stroke="#ff0055" stroke-width="1.5" />
    <circle cx="24" cy="80" r="8" fill="#ff0055" class="blink">
      <animate attributeName="opacity" values="1;0.2;1" dur="1.6s" repeatCount="indefinite" />
    </circle>
    <text x="42" y="84" fill="#ffffff" font-size="14" font-weight="700">CRIMSON THREAT ALERT</text>
    <text x="42" y="102" fill="#ff0055" font-size="11" font-family="monospace" font-weight="bold">DECISION LOG-RATIO: +6.41</text>
    <text x="42" y="120" fill="#8da2c0" font-size="10">Threshold A = +5.288 (Cloned Impersonation)</text>

    <g transform="translate(0, 155)">
      <rect x="0" y="0" width="120" height="52" rx="6" fill="#111927" stroke="#1c2d47" />
      <text x="10" y="18" fill="#6f8ab0" font-size="9" font-weight="bold">FALSE ALARM (α)</text>
      <text x="10" y="38" fill="#00ff88" font-size="16" font-weight="bold" font-family="monospace">≤ 0.5%</text>

      <rect x="135" y="0" width="120" height="52" rx="6" fill="#111927" stroke="#1c2d47" />
      <text x="145" y="18" fill="#6f8ab0" font-size="9" font-weight="bold">STOPPING TIME</text>
      <text x="145" y="38" fill="#00f0ff" font-size="16" font-weight="bold" font-family="monospace">2.4 sec</text>
    </g>

    <g transform="translate(0, 220)">
      <rect x="0" y="0" width="255" height="56" rx="6" fill="#111927" stroke="#1c2d47" />
      <text x="12" y="18" fill="#6f8ab0" font-size="9" font-weight="bold">BSA 2023 / SEC 65B EVIDENCE DOSSIER</text>
      <text x="12" y="36" fill="#ffb800" font-size="11" font-family="monospace">SHA256: 7f8b9a2c... StrongBox-Signed</text>
      <text x="12" y="49" fill="#00ff88" font-size="9" font-weight="bold">READY FOR 1930 / NCRP / CHAKSHU</text>
    </g>

    <text x="0" y="340" fill="#47688d" font-size="10" font-family="monospace">SIH 2026 • PS26104 • TEAM FORGE LABS</text>
  </g>
</svg>
"""

def create_pipeline_svg():
    return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 440" width="100%" height="440" style="background: #080c14; border-radius: 12px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
  <defs>
    <linearGradient id="pipeGrad" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#00f0ff"/>
      <stop offset="50%" stop-color="#7928ca"/>
      <stop offset="100%" stop-color="#ff0055"/>
    </linearGradient>
    <filter id="glowPipe" x="-20%" y="-20%" width="140%" height="140%">
      <feGaussianBlur stdDeviation="3" result="blur" />
      <feComposite in="SourceGraphic" in2="blur" operator="over" />
    </filter>
    <style>
      @keyframes dash {
        to { stroke-dashoffset: -32; }
      }
      .flow-path {
        stroke-dasharray: 6 6;
        animation: dash 1s linear infinite;
      }
    </style>
  </defs>

  <!-- Title & Subtitle Header -->
  <text x="30" y="36" fill="#00f0ff" font-size="16" font-weight="800" letter-spacing="1.5">RAKSHAK SETU (रक्षक सेतु) — DUAL-TRACK ON-DEVICE PIPELINE</text>
  <text x="30" y="54" fill="#758ea8" font-size="11">Sovereign Telecom Call Interception • 100% On-Device Edge ML • DPDP Act 2023 &amp; BSA Sec 65B Compliant</text>

  <!-- PHASE 1: TELECOM INGEST -->
  <g transform="translate(30, 80)">
    <rect x="0" y="0" width="160" height="140" rx="8" fill="#0f1726" stroke="#1e3454" stroke-width="1.5" />
    <rect x="0" y="0" width="160" height="28" rx="8" fill="#16263d" />
    <text x="12" y="19" fill="#38bdf8" font-size="11" font-weight="bold">1. TELECOM INGEST</text>
    <text x="12" y="50" fill="#e2e8f0" font-size="11" font-weight="600">Android 14+ Telecom</text>
    <text x="12" y="68" fill="#8da2c0" font-size="9.5">• ConnectionService Hook</text>
    <text x="12" y="84" fill="#8da2c0" font-size="9.5">• WebRTC DTLS-SRTP</text>
    <text x="12" y="100" fill="#8da2c0" font-size="9.5">• Opus to 16kHz PCM</text>
    <rect x="10" y="114" width="140" height="18" rx="4" fill="#0284c7" opacity="0.2" />
    <text x="16" y="127" fill="#38bdf8" font-size="9" font-weight="bold">IN-FLIGHT STREAM</text>
  </g>

  <!-- FLOW ARROW 1 -> 2 -->
  <path d="M 190,150 L 230,150" stroke="#00f0ff" stroke-width="2" fill="none" class="flow-path">
    <animate attributeName="stroke-dashoffset" from="0" to="-24" dur="1s" repeatCount="indefinite" />
  </path>

  <!-- PHASE 2: SPSC CIRCULAR RAM BUFFER -->
  <g transform="translate(230, 80)">
    <rect x="0" y="0" width="160" height="140" rx="8" fill="#0f1726" stroke="#818cf8" stroke-width="1.5" />
    <rect x="0" y="0" width="160" height="28" rx="8" fill="#22204c" />
    <text x="12" y="19" fill="#a5b4fc" font-size="11" font-weight="bold">2. SPSC RAM BUFFER</text>
    <text x="12" y="50" fill="#e2e8f0" font-size="11" font-weight="600">Lock-Free Circular RAM</text>
    <text x="12" y="68" fill="#8da2c0" font-size="9.5">• 32KB Circular Array</text>
    <text x="12" y="84" fill="#8da2c0" font-size="9.5">• Atomic Read/Write Ptr</text>
    <text x="12" y="100" fill="#34d399" font-size="9.5" font-weight="bold">• ZERO DISK WRITE (RAM)</text>
    <rect x="10" y="114" width="140" height="18" rx="4" fill="#10b981" opacity="0.2" />
    <text x="16" y="127" fill="#34d399" font-size="9" font-weight="bold">DPDP ACT 2023 COMPLIANT</text>
  </g>

  <!-- BRANCH ARROWS 2 -> TRACK A & TRACK B -->
  <path d="M 390,130 L 430,110" stroke="#38bdf8" stroke-width="2" fill="none" class="flow-path">
    <animate attributeName="stroke-dashoffset" from="0" to="-24" dur="1s" repeatCount="indefinite" />
  </path>
  <path d="M 390,170 L 430,230" stroke="#c084fc" stroke-width="2" fill="none" class="flow-path">
    <animate attributeName="stroke-dashoffset" from="0" to="-24" dur="1s" repeatCount="indefinite" />
  </path>

  <!-- TRACK A: ACOUSTIC ENGINE -->
  <g transform="translate(430, 45)">
    <rect x="0" y="0" width="230" height="125" rx="8" fill="#0f1726" stroke="#38bdf8" stroke-width="1.5" />
    <rect x="0" y="0" width="230" height="26" rx="8" fill="#122b47" />
    <text x="12" y="18" fill="#38bdf8" font-size="11" font-weight="bold">TRACK A: ACOUSTIC ENGINE (HOW)</text>
    <text x="12" y="46" fill="#e2e8f0" font-size="10.5" font-weight="600">AASIST-L INT8 ONNX [1, 1, 16000L]</text>
    <text x="12" y="63" fill="#8da2c0" font-size="9">• SincNet Learnable Bandpass (8kHz Safe)</text>
    <text x="12" y="78" fill="#8da2c0" font-size="9">• Neural Vocoder Phase Jitter Extraction</text>
    <text x="12" y="93" fill="#8da2c0" font-size="9">• Cepstral Peak Prominence &amp; Shimmer</text>
    <text x="12" y="112" fill="#38bdf8" font-size="9.5" font-weight="bold">Voice Biometric Authenticity Score</text>
  </g>

  <!-- TRACK B: SEMANTIC ENGINE -->
  <g transform="translate(430, 190)">
    <rect x="0" y="0" width="230" height="125" rx="8" fill="#0f1726" stroke="#c084fc" stroke-width="1.5" />
    <rect x="0" y="0" width="230" height="26" rx="8" fill="#2b1b47" />
    <text x="12" y="18" fill="#c084fc" font-size="11" font-weight="bold">TRACK B: SEMANTIC ENGINE (WHAT)</text>
    <text x="12" y="46" fill="#e2e8f0" font-size="10.5" font-weight="600">Streaming Vosk ASR + MiniLM</text>
    <text x="12" y="63" fill="#8da2c0" font-size="9">• Aho-Corasick Multi-Pattern Threat Trie</text>
    <text x="12" y="78" fill="#8da2c0" font-size="9">• 21+ Indian Scam Vectors (Digital Arrest)</text>
    <text x="12" y="93" fill="#8da2c0" font-size="9">• Intent Cosine Distance &amp; Coercion Map</text>
    <text x="12" y="112" fill="#c084fc" font-size="9.5" font-weight="bold">Extortion &amp; Scam Intent Score</text>
  </g>

  <!-- MERGE INTO SPRT ENGINE -->
  <path d="M 660,110 L 700,160" stroke="#38bdf8" stroke-width="2" fill="none" class="flow-path">
    <animate attributeName="stroke-dashoffset" from="0" to="-24" dur="1s" repeatCount="indefinite" />
  </path>
  <path d="M 660,240 L 700,180" stroke="#c084fc" stroke-width="2" fill="none" class="flow-path">
    <animate attributeName="stroke-dashoffset" from="0" to="-24" dur="1s" repeatCount="indefinite" />
  </path>

  <!-- PHASE 4: WALD SPRT DECISION ENGINE -->
  <g transform="translate(700, 110)">
    <rect x="0" y="0" width="270" height="125" rx="8" fill="#150e24" stroke="#f59e0b" stroke-width="2" />
    <rect x="0" y="0" width="270" height="28" rx="8" fill="#2b1a05" />
    <text x="12" y="19" fill="#fbbf24" font-size="11" font-weight="bold">4. WALD'S SPRT DECISION MATRIX</text>
    <text x="12" y="48" fill="#ffffff" font-size="10.5" font-weight="bold">Sequential Probability Ratio Test</text>
    <text x="12" y="66" fill="#e2e8f0" font-size="9.5">• Upper Threat Threshold: <tspan fill="#ef4444" font-weight="bold">A = +5.288</tspan></text>
    <text x="12" y="82" fill="#e2e8f0" font-size="9.5">• Lower Safe Threshold: <tspan fill="#10b981" font-weight="bold">B = -4.600</tspan></text>
    <text x="12" y="98" fill="#38bdf8" font-size="9.5">• Stopping Time: <tspan font-weight="bold">2.1 - 2.6 sec</tspan> | α ≤ 0.5%</text>
    <text x="12" y="114" fill="#fbbf24" font-size="9" font-weight="bold">Zero Indecision • Mathematically Optimal</text>
  </g>

  <!-- FLOW DOWN TO ENFORCEMENT & FORENSICS -->
  <path d="M 835,235 L 835,290" stroke="#ef4444" stroke-width="2.5" fill="none" class="flow-path">
    <animate attributeName="stroke-dashoffset" from="0" to="-24" dur="1s" repeatCount="indefinite" />
  </path>

  <!-- PHASE 5: IMMEDIATE ACTION & BSA 2023 FORENSICS -->
  <g transform="translate(30, 290)">
    <rect x="0" y="0" width="940" height="125" rx="8" fill="#180a10" stroke="#ef4444" stroke-width="1.5" />
    <rect x="0" y="0" width="940" height="26" rx="8" fill="#330c17" />
    <text x="16" y="18" fill="#f87171" font-size="11" font-weight="bold">5. CITIZEN DEFENSE ENFORCEMENT &amp; COURTROOM EVIDENCE ADMISSIBILITY (BSA 2023)</text>
    
    <g transform="translate(20, 38)">
      <rect x="0" y="0" width="280" height="72" rx="6" fill="#220d15" stroke="#ef4444" />
      <text x="12" y="20" fill="#ffffff" font-size="11" font-weight="bold">IN-CALL CRIMSON HUD</text>
      <text x="12" y="38" fill="#fca5a5" font-size="9.5">• Full-Screen Threat Warning</text>
      <text x="12" y="54" fill="#fca5a5" font-size="9.5">• One-Tap Emergency Call Killswitch</text>
    </g>

    <g transform="translate(320, 38)">
      <rect x="0" y="0" width="280" height="72" rx="6" fill="#1b1c0b" stroke="#eab308" />
      <text x="12" y="20" fill="#ffffff" font-size="11" font-weight="bold">GOVERNMENT PORTAL CO-PILOT</text>
      <text x="12" y="38" fill="#fef08a" font-size="9.5">• NCRP 1930 Cybercrime Auto-Fill</text>
      <text x="12" y="54" fill="#fef08a" font-size="9.5">• DoT Chakshu Sanchar Saathi Dispatch</text>
    </g>

    <g transform="translate(620, 38)">
      <rect x="0" y="0" width="300" height="72" rx="6" fill="#0b1f1a" stroke="#10b981" />
      <text x="12" y="20" fill="#ffffff" font-size="11" font-weight="bold">BSA 2023 / SEC 65B FORENSIC SEAL</text>
      <text x="12" y="38" fill="#6ee7b7" font-size="9.5">• Hardware Keystore / StrongBox Signature</text>
      <text x="12" y="54" fill="#6ee7b7" font-size="9.5">• SHA-256 Tamper-Evident Court Dossier</text>
    </g>
  </g>
</svg>
"""

def main():
    radar = create_radar_svg()
    pipeline = create_pipeline_svg()

    paths = [
        (r"c:\Users\Public\Downloads\chrome downloads\Rakshak Setu\RakshakSetu\docs\images", "threat_radar_waveform.svg", radar),
        (r"c:\Users\Public\Downloads\chrome downloads\Rakshak Setu\RakshakSetu\docs\images", "dual_track_pipeline.svg", pipeline),
        (r"c:\Users\Public\Downloads\chrome downloads\Rakshak Setu\RakshakSetu\assets", "threat_radar_waveform.svg", radar),
        (r"c:\Users\Public\Downloads\chrome downloads\Rakshak Setu\RakshakSetu\assets", "dual_track_pipeline.svg", pipeline),
        (r"c:\Users\Public\Downloads\chrome downloads\Rakshak Setu\docs\images", "threat_radar_waveform.svg", radar),
        (r"c:\Users\Public\Downloads\chrome downloads\Rakshak Setu\docs\images", "dual_track_pipeline.svg", pipeline),
        (r"c:\Users\Public\Downloads\chrome downloads\Rakshak Setu\assets", "threat_radar_waveform.svg", radar),
        (r"c:\Users\Public\Downloads\chrome downloads\Rakshak Setu\assets", "dual_track_pipeline.svg", pipeline),
    ]

    for d, filename, content in paths:
        os.makedirs(d, exist_ok=True)
        target = os.path.join(d, filename)
        with open(target, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Generated: {target}")

if __name__ == "__main__":
    main()
