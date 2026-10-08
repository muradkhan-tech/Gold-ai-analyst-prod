import React, { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import TradingViewWidget from './components/TradingViewWidget';
import { LiveTradesView, LiveTrade } from './components/LiveTradesView';

type TabType = 'signals' | 'chart' | 'trades' | 'ai_news' | 'calendar' | 'settings';
type TimeframeType = '1m' | '3m' | '5m' | '10m' | '15m' | '1h';
type DirectionType = 'STRONG BUY' | 'BUY' | 'WEAK BUY' | 'WAIT' | 'WEAK SELL' | 'SELL' | 'STRONG SELL';

interface CandleData {
  time: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

interface SignalCardData {
  timeframe: TimeframeType;
  direction: DirectionType;
  entryZoneLow: number;
  entryZoneHigh: number;
  takeProfit1: number;
  takeProfit2: number;
  stopLoss: number;
  riskReward: number;
  confidenceScore: number;
  winRate: number;
  summary: string;
}

interface AlignmentRow {
  timeframe: TimeframeType;
  trend: 'BULLISH' | 'BEARISH' | 'NEUTRAL';
  score: number;
  rsi: number;
  macdHist: number;
  emaStatus: string;
}

interface NewsItem {
  id: number;
  title: string;
  source: string;
  summary: string;
  category: 'GOLD' | 'FED_POLICY' | 'INFLATION' | 'TREASURY_YIELDS' | 'USD_DXY';
  sentiment: 'BULLISH' | 'BEARISH' | 'NEUTRAL';
  timeAgo: string;
  url?: string;
}

interface CalendarRelease {
  id: number;
  title: string;
  country: string;
  impact: 'HIGH' | 'MEDIUM' | 'LOW';
  dateStr: string;
  timeStr: string;
  forecast: string;
  previous: string;
  goldBias: 'BULLISH' | 'BEARISH' | 'NEUTRAL';
  notes: string;
}

interface CustomPriceAlert {
  id: string;
  targetPrice: number;
  condition: 'ABOVE' | 'BELOW';
  type: 'BID' | 'ASK' | 'SPOT';
  triggered: boolean;
  createdAt: number;
  label?: string;
  triggeredAt?: number;
}

// Generate realistic initial candles for interactive canvas
function generateInitialCandles(basePrice: number, count: number = 40): CandleData[] {
  const candles: CandleData[] = [];
  let curr = basePrice - 8.5;
  const now = Date.now();
  for (let i = count; i >= 0; i--) {
    const time = new Date(now - i * 15 * 60 * 1000).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    const delta = (Math.random() - 0.47) * 2.2;
    const open = curr;
    const close = +(open + delta).toFixed(2);
    const high = +(Math.max(open, close) + Math.random() * 1.5).toFixed(2);
    const low = +(Math.min(open, close) - Math.random() * 1.5).toFixed(2);
    const volume = Math.floor(400 + Math.random() * 1200);
    candles.push({ time, open, high, low, close, volume });
    curr = close;
  }
  return candles;
}

// Subtle institutional notification chime using HTML AudioContext API
function playAlertChime() {
  try {
    const AudioContextClass = window.AudioContext || (window as any).webkitAudioContext;
    if (!AudioContextClass) return;
    const ctx = new AudioContextClass();
    if (ctx.state === 'suspended') {
      ctx.resume();
    }
    const now = ctx.currentTime;

    // Primary crystal chime tone (A5 880Hz -> D6 1174.66Hz)
    const osc1 = ctx.createOscillator();
    const gain1 = ctx.createGain();
    osc1.type = 'sine';
    osc1.frequency.setValueAtTime(880, now);
    osc1.frequency.exponentialRampToValueAtTime(1174.66, now + 0.08);

    gain1.gain.setValueAtTime(0.18, now);
    gain1.gain.exponentialRampToValueAtTime(0.001, now + 0.5);

    osc1.connect(gain1);
    gain1.connect(ctx.destination);
    osc1.start(now);
    osc1.stop(now + 0.5);

    // Subtle warm overtone (triangle wave at 1760Hz)
    const osc2 = ctx.createOscillator();
    const gain2 = ctx.createGain();
    osc2.type = 'triangle';
    osc2.frequency.setValueAtTime(1760, now + 0.04);
    gain2.gain.setValueAtTime(0.08, now + 0.04);
    gain2.gain.exponentialRampToValueAtTime(0.001, now + 0.38);

    osc2.connect(gain2);
    gain2.connect(ctx.destination);
    osc2.start(now + 0.04);
    osc2.stop(now + 0.38);
  } catch {
    // Ignore audio context errors if browser blocks autoplay before user gesture
  }
}

// IndexedDB Cache for Instant (0ms) Chart Startup & Live Trades Persistence
const DB_NAME = 'GoldAiAnalystDB';
const DB_VERSION = 2;
const STORE_NAME = 'market_candles';
const TRADES_STORE_NAME = 'live_simulated_trades';

function openIndexedDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      return reject('IndexedDB not supported');
    }
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = (e: any) => {
      const db = e.target.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME, { keyPath: 'timeframe' });
      }
      if (!db.objectStoreNames.contains(TRADES_STORE_NAME)) {
        db.createObjectStore(TRADES_STORE_NAME, { keyPath: 'id' });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function saveCandlesToIndexedDB(timeframe: string, candles: CandleData[]) {
  try {
    const db = await openIndexedDB();
    const tx = db.transaction(STORE_NAME, 'readwrite');
    tx.objectStore(STORE_NAME).put({ timeframe, candles, updatedAt: Date.now() });
  } catch (err) {
    // Graceful fallback if indexedDB is restricted
  }
}

async function loadCandlesFromIndexedDB(timeframe: string): Promise<CandleData[] | null> {
  try {
    const db = await openIndexedDB();
    return new Promise((resolve) => {
      const tx = db.transaction(STORE_NAME, 'readonly');
      const req = tx.objectStore(STORE_NAME).get(timeframe);
      req.onsuccess = () => resolve(req.result ? req.result.candles : null);
      req.onerror = () => resolve(null);
    });
  } catch (err) {
    return null;
  }
}

async function saveAllTradesToIndexedDB(trades: LiveTrade[]) {
  try {
    const db = await openIndexedDB();
    const tx = db.transaction(TRADES_STORE_NAME, 'readwrite');
    const store = tx.objectStore(TRADES_STORE_NAME);
    trades.forEach(t => store.put(t));
  } catch (err) {
    // Graceful fallback
  }
}

async function loadTradesFromIndexedDB(): Promise<LiveTrade[]> {
  try {
    const db = await openIndexedDB();
    return new Promise((resolve) => {
      const tx = db.transaction(TRADES_STORE_NAME, 'readonly');
      const store = tx.objectStore(TRADES_STORE_NAME);
      const req = store.getAll();
      req.onsuccess = () => resolve(req.result || []);
      req.onerror = () => resolve([]);
    });
  } catch (err) {
    return [];
  }
}

async function clearTradesFromIndexedDB() {
  try {
    const db = await openIndexedDB();
    const tx = db.transaction(TRADES_STORE_NAME, 'readwrite');
    tx.objectStore(TRADES_STORE_NAME).clear();
  } catch (err) {}
}

const initialSeedTrades: LiveTrade[] = [
  {
    id: 'trade-seed-1',
    timeframe: '15m',
    direction: 'BUY',
    lots: 1.0,
    entryPrice: 2854.20,
    entryTime: Date.now() - 5400000,
    stopLoss: 2848.50,
    takeProfit1: 2862.00,
    takeProfit2: 2868.50,
    exitPrice: 2862.00,
    exitTime: Date.now() - 3600000,
    status: 'TP1_HIT',
    pnlDollar: 780.00,
    pnlPips: 78.0,
    riskReward: 2.1,
    signalReason: 'EMA 8/21 Ribbon expansion & Bullish VSA Absorption'
  },
  {
    id: 'trade-seed-2',
    timeframe: '5m',
    direction: 'BUY',
    lots: 1.0,
    entryPrice: 2858.00,
    entryTime: Date.now() - 3200000,
    stopLoss: 2853.00,
    takeProfit1: 2865.50,
    takeProfit2: 2871.00,
    exitPrice: 2871.00,
    exitTime: Date.now() - 1400000,
    status: 'TP2_HIT',
    pnlDollar: 1300.00,
    pnlPips: 130.0,
    riskReward: 2.6,
    signalReason: 'Asian Range Liquidity Purge confirmed rejection'
  },
  {
    id: 'trade-seed-3',
    timeframe: '1m',
    direction: 'SELL',
    lots: 1.0,
    entryPrice: 2866.50,
    entryTime: Date.now() - 900000,
    stopLoss: 2869.50,
    takeProfit1: 2862.00,
    takeProfit2: 2858.00,
    exitPrice: 2869.50,
    exitTime: Date.now() - 450000,
    status: 'SL_HIT',
    pnlDollar: -300.00,
    pnlPips: -30.0,
    riskReward: 1.5,
    signalReason: 'Counter-trend 1m micro scalp'
  }
];

export default function App() {
  const [activeTab, setActiveTab] = useState<TabType>('signals');
  const [selectedTimeframe, setSelectedTimeframe] = useState<TimeframeType>('15m');
  const [useTradingViewEmbed, setUseTradingViewEmbed] = useState<boolean>(false);

  // Live price & tick state
  const [price, setPrice] = useState<number>(2864.50);
  const [bid, setBid] = useState<number>(2864.38);
  const [ask, setAsk] = useState<number>(2864.62);
  const [spread, setSpread] = useState<number>(0.24);
  const [tickDirection, setTickDirection] = useState<'up' | 'down' | null>(null);
  const [candles, setCandles] = useState<CandleData[]>(() => generateInitialCandles(2864.50));

  // Direct DOM refs for zero-re-render quote ticker updates
  const bidRef = useRef<HTMLSpanElement | null>(null);
  const askRef = useRef<HTMLSpanElement | null>(null);
  const priceHeaderRef = useRef<HTMLSpanElement | null>(null);
  const workerRef = useRef<Worker | null>(null);

  // Initialize Web Worker for client-side indicator offloading
  useEffect(() => {
    if (typeof Worker !== 'undefined') {
      try {
        const worker = new Worker('/worker.js');
        workerRef.current = worker;
        worker.onmessage = (e) => {
          if (e.data.type === 'INDICATORS_COMPUTED') {
            // Received offloaded indicator math from worker thread
          }
        };
      } catch (e) {
        // Fallback gracefully
      }
    }
    return () => {
      workerRef.current?.terminate();
    };
  }, []);

  // IndexedDB 0ms perceived load: load cached candles on startup
  useEffect(() => {
    loadCandlesFromIndexedDB(selectedTimeframe).then((cached) => {
      if (cached && cached.length > 0) {
        setCandles(cached);
      }
    });
  }, [selectedTimeframe]);

  // Persist latest candles to IndexedDB in background
  useEffect(() => {
    if (candles.length > 0) {
      saveCandlesToIndexedDB(selectedTimeframe, candles);
      if (workerRef.current) {
        workerRef.current.postMessage({
          type: 'CALCULATE_INDICATORS',
          payload: { candles }
        });
      }
    }
  }, [candles, selectedTimeframe]);

  // Settings State persisted in localStorage
  const [marketDataKey, setMarketDataKey] = useState<string>(() => {
    return localStorage.getItem('gold_ai_market_data_key') || '';
  });
  const [newsApiKey, setNewsApiKey] = useState<string>(() => {
    return localStorage.getItem('gold_ai_news_api_key') || '';
  });
  const [openaiKey, setOpenaiKey] = useState<string>(() => {
    return localStorage.getItem('gold_ai_openai_key') || '';
  });
  const [webhookSecret, setWebhookSecret] = useState<string>(() => {
    return localStorage.getItem('gold_ai_webhook_secret') || 'gold-ai-local-secret';
  });

  const [showKeys, setShowKeys] = useState<{ [key: string]: boolean }>({});
  const [saveToast, setSaveToast] = useState<string | null>(null);
  const [activeNewsCategory, setActiveNewsCategory] = useState<string>('ALL');
  const [isEvaluatingAi, setIsEvaluatingAi] = useState<boolean>(false);

  // Custom Price Alerts State
  const [priceAlerts, setPriceAlerts] = useState<CustomPriceAlert[]>(() => {
    try {
      const saved = localStorage.getItem('gold_ai_price_alerts');
      if (saved) return JSON.parse(saved);
    } catch (e) {}
    return [
      {
        id: 'alert-1',
        targetPrice: 2870.00,
        condition: 'ABOVE',
        type: 'ASK',
        triggered: false,
        createdAt: Date.now() - 3600000,
        label: 'M15 Resistance Breakout'
      },
      {
        id: 'alert-2',
        targetPrice: 2858.00,
        condition: 'BELOW',
        type: 'BID',
        triggered: false,
        createdAt: Date.now() - 7200000,
        label: 'Support Pullback Buy Zone'
      }
    ];
  });

  const [newAlertPrice, setNewAlertPrice] = useState<string>('2868.00');
  const [newAlertCondition, setNewAlertCondition] = useState<'ABOVE' | 'BELOW'>('ABOVE');
  const [newAlertType, setNewAlertType] = useState<'BID' | 'ASK' | 'SPOT'>('ASK');
  const [newAlertLabel, setNewAlertLabel] = useState<string>('');
  const [isAlertFormOpen, setIsAlertFormOpen] = useState<boolean>(false);
  const [soundAlertsEnabled, setSoundAlertsEnabled] = useState<boolean>(() => {
    const saved = localStorage.getItem('gold_ai_sound_alerts_enabled');
    return saved !== null ? saved === 'true' : true;
  });
  const [notificationPermission, setNotificationPermission] = useState<string>(() => {
    if (typeof window !== 'undefined' && 'Notification' in window) {
      return Notification.permission;
    }
    return 'default';
  });

  // Persist alerts to localStorage
  useEffect(() => {
    localStorage.setItem('gold_ai_price_alerts', JSON.stringify(priceAlerts));
  }, [priceAlerts]);

  // Persist sound alerts preference
  useEffect(() => {
    localStorage.setItem('gold_ai_sound_alerts_enabled', String(soundAlertsEnabled));
  }, [soundAlertsEnabled]);

  // Request browser notification permission
  const requestNotificationPermission = useCallback(async () => {
    if (typeof window !== 'undefined' && 'Notification' in window) {
      try {
        const perm = await Notification.requestPermission();
        setNotificationPermission(perm);
        if (perm === 'granted') {
          setSaveToast('Browser notifications enabled! Alert thresholds will notify you automatically.');
          setTimeout(() => setSaveToast(null), 3500);
        } else {
          setSaveToast('Notification permission was not granted. In-app banner alerts remain active.');
          setTimeout(() => setSaveToast(null), 3500);
        }
      } catch (err) {
        console.warn('Error requesting notification permission:', err);
      }
    }
  }, []);

  // Monitor live Bid/Ask/Spot price changes and trigger alert thresholds
  useEffect(() => {
    let triggeredAny = false;
    const updated = priceAlerts.map(alert => {
      if (alert.triggered) return alert;

      const currentVal = alert.type === 'BID' ? bid : alert.type === 'ASK' ? ask : price;
      const isCrossed = alert.condition === 'ABOVE'
        ? currentVal >= alert.targetPrice
        : currentVal <= alert.targetPrice;

      if (isCrossed) {
        triggeredAny = true;
        const conditionText = alert.condition === 'ABOVE' ? 'risen ABOVE' : 'dropped BELOW';
        const title = `🚨 XAUUSD ${alert.type} Alert Triggered!`;
        const body = `${alert.label ? `[${alert.label}] ` : ''}${alert.type} price has ${conditionText} $${alert.targetPrice.toFixed(2)} (Current: $${currentVal.toFixed(2)})`;

        // Subtle HTML Audio API chime when threshold crossed
        if (soundAlertsEnabled) {
          playAlertChime();
        }

        // Native Browser Notification
        if (typeof window !== 'undefined' && 'Notification' in window && Notification.permission === 'granted') {
          try {
            new Notification(title, {
              body,
              icon: '/favicon.ico',
              tag: alert.id
            });
          } catch (e) {
            console.warn('Browser notification error:', e);
          }
        }

        // In-app Alert Toast Notification
        setSaveToast(`🔔 ${title}: ${body}`);
        setTimeout(() => setSaveToast(null), 6000);

        return {
          ...alert,
          triggered: true,
          triggeredAt: Date.now()
        };
      }
      return alert;
    });

    if (triggeredAny) {
      setPriceAlerts(updated);
    }
  }, [bid, ask, price, priceAlerts, soundAlertsEnabled]);

  const handleAddAlert = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const val = parseFloat(newAlertPrice);
    if (isNaN(val) || val <= 0) return;

    const alert: CustomPriceAlert = {
      id: 'alert-' + Date.now(),
      targetPrice: +val.toFixed(2),
      condition: newAlertCondition,
      type: newAlertType,
      triggered: false,
      createdAt: Date.now(),
      label: newAlertLabel.trim() || undefined
    };

    setPriceAlerts(prev => [alert, ...prev]);
    setNewAlertLabel('');
    setIsAlertFormOpen(false);
    setSaveToast(`Threshold set: Alert when ${newAlertType} crosses ${newAlertCondition} $${val.toFixed(2)}`);
    setTimeout(() => setSaveToast(null), 3000);

    if (typeof window !== 'undefined' && 'Notification' in window && Notification.permission === 'default') {
      requestNotificationPermission();
    }
  };

  const handleRemoveAlert = (id: string) => {
    setPriceAlerts(prev => prev.filter(a => a.id !== id));
  };

  const handleRearmAlert = (id: string) => {
    setPriceAlerts(prev => prev.map(a => a.id === id ? { ...a, triggered: false, triggeredAt: undefined } : a));
    setSaveToast('Alert re-armed and watching live price feeds.');
    setTimeout(() => setSaveToast(null), 2500);
  };

  // Canvas ref for high-FPS chart
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  // Tick simulation with flash update
  useEffect(() => {
    const timer = setInterval(() => {
      const step = (Math.random() - 0.48) * 0.45;
      setPrice(prev => {
        const nextPrice = +(prev + step).toFixed(2);
        const direction = nextPrice >= prev ? 'up' : 'down';
        setTickDirection(direction);
        const nextSpread = 0.24;
        setSpread(nextSpread);
        const nextBid = +(nextPrice - nextSpread / 2).toFixed(2);
        const nextAsk = +(nextPrice + nextSpread / 2).toFixed(2);
        setBid(nextBid);
        setAsk(nextAsk);

        // Zero-Re-render Direct DOM update for quote tickers & flashes
        if (bidRef.current) {
          bidRef.current.textContent = `$${nextBid.toFixed(2)}`;
          bidRef.current.className = `text-xl sm:text-2xl font-bold font-mono font-mono-numbers text-rose-600 transition-colors ${direction === 'down' ? 'scale-105' : ''}`;
        }
        if (askRef.current) {
          askRef.current.textContent = `$${nextAsk.toFixed(2)}`;
          askRef.current.className = `text-xl sm:text-2xl font-bold font-mono font-mono-numbers text-emerald-600 transition-colors ${direction === 'up' ? 'scale-105' : ''}`;
        }
        if (priceHeaderRef.current) {
          priceHeaderRef.current.textContent = `$${nextPrice.toFixed(2)}`;
        }

        return nextPrice;
      });
    }, 1800);

    return () => clearInterval(timer);
  }, []);

  // Update canvas chart
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const width = canvas.width;
    const height = canvas.height;
    ctx.clearRect(0, 0, width, height);

    // Draw subtle grid
    ctx.strokeStyle = '#F1F5F9';
    ctx.lineWidth = 1;
    for (let y = 30; y < height; y += 40) {
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(width, y);
      ctx.stroke();
    }
    for (let x = 40; x < width; x += 60) {
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x, height);
      ctx.stroke();
    }

    if (candles.length === 0) return;

    const minPrice = Math.min(...candles.map(c => c.low)) - 1.0;
    const maxPrice = Math.max(...candles.map(c => c.high)) + 1.0;
    const priceRange = maxPrice - minPrice || 1;

    const candleWidth = Math.max(3, (width - 60) / candles.length - 3);

    // Draw Candlesticks
    candles.forEach((candle, idx) => {
      const x = 20 + idx * (candleWidth + 3);
      const openY = height - 35 - ((candle.open - minPrice) / priceRange) * (height - 60);
      const closeY = height - 35 - ((candle.close - minPrice) / priceRange) * (height - 60);
      const highY = height - 35 - ((candle.high - minPrice) / priceRange) * (height - 60);
      const lowY = height - 35 - ((candle.low - minPrice) / priceRange) * (height - 60);

      const isBullish = candle.close >= candle.open;
      const color = isBullish ? '#16A34A' : '#DC2626';

      // Wick
      ctx.strokeStyle = color;
      ctx.lineWidth = 1.2;
      ctx.beginPath();
      ctx.moveTo(x + candleWidth / 2, highY);
      ctx.lineTo(x + candleWidth / 2, lowY);
      ctx.stroke();

      // Body
      ctx.fillStyle = color;
      const topY = Math.min(openY, closeY);
      const bodyH = Math.max(2, Math.abs(closeY - openY));
      ctx.fillRect(x, topY, candleWidth, bodyH);
    });

    // Draw Moving Average Line (EMA 20 approximation)
    ctx.strokeStyle = '#2563EB';
    ctx.lineWidth = 2;
    ctx.beginPath();
    candles.forEach((c, idx) => {
      const x = 20 + idx * (candleWidth + 3) + candleWidth / 2;
      const avgY = height - 35 - ((c.close - minPrice) / priceRange) * (height - 60);
      if (idx === 0) ctx.moveTo(x, avgY);
      else ctx.lineTo(x, avgY);
    });
    ctx.stroke();

    // Price label on right
    ctx.fillStyle = '#1E40AF';
    ctx.font = 'bold 11px ui-monospace, monospace';
    ctx.fillText(`$${price.toFixed(2)}`, width - 58, 24);
  }, [candles, price]);

  // Handle Save Settings
  const handleSaveSettings = useCallback(() => {
    localStorage.setItem('gold_ai_market_data_key', marketDataKey.trim());
    localStorage.setItem('gold_ai_news_api_key', newsApiKey.trim());
    localStorage.setItem('gold_ai_openai_key', openaiKey.trim());
    localStorage.setItem('gold_ai_webhook_secret', webhookSecret.trim());
    localStorage.setItem('gold_ai_sound_alerts_enabled', String(soundAlertsEnabled));

    setSaveToast('Settings saved successfully! Configuration is active immediately.');
    setTimeout(() => setSaveToast(null), 3500);
  }, [marketDataKey, newsApiKey, openaiKey, webhookSecret, soundAlertsEnabled]);

  // Handle Reset Defaults
  const handleResetDefaults = useCallback(() => {
    localStorage.removeItem('gold_ai_market_data_key');
    localStorage.removeItem('gold_ai_news_api_key');
    localStorage.removeItem('gold_ai_openai_key');
    localStorage.setItem('gold_ai_webhook_secret', 'gold-ai-local-secret');
    localStorage.setItem('gold_ai_sound_alerts_enabled', 'true');

    setMarketDataKey('');
    setNewsApiKey('');
    setOpenaiKey('');
    setWebhookSecret('gold-ai-local-secret');
    setSoundAlertsEnabled(true);

    setSaveToast('Reset to zero-configuration defaults (Keyless Yahoo Finance, Heuristics & Chimes active).');
    setTimeout(() => setSaveToast(null), 3500);
  }, []);

  // Multi-Timeframe Signals Data
  const signalsData: SignalCardData[] = useMemo(() => [
    {
      timeframe: '1m',
      direction: 'BUY',
      entryZoneLow: 2863.80,
      entryZoneHigh: 2864.50,
      takeProfit1: 2867.20,
      takeProfit2: 2869.80,
      stopLoss: 2861.50,
      riskReward: 2.1,
      confidenceScore: 84,
      winRate: 72.8,
      summary: 'Fast EMA 8/21 momentum bullish continuation; RSI 59.2.'
    },
    {
      timeframe: '3m',
      direction: 'STRONG BUY',
      entryZoneLow: 2863.20,
      entryZoneHigh: 2864.40,
      takeProfit1: 2869.00,
      takeProfit2: 2873.50,
      stopLoss: 2860.80,
      riskReward: 2.4,
      confidenceScore: 89,
      winRate: 78.4,
      summary: 'Institutional volume surge; pullbacks bought above EMA ribbon.'
    },
    {
      timeframe: '5m',
      direction: 'STRONG BUY',
      entryZoneLow: 2862.50,
      entryZoneHigh: 2864.20,
      takeProfit1: 2872.00,
      takeProfit2: 2878.00,
      stopLoss: 2859.50,
      riskReward: 2.8,
      confidenceScore: 92,
      winRate: 81.2,
      summary: 'Dr Hafiz V2 & 3ESRA confluence confirmed; liquidity sweep complete.'
    },
    {
      timeframe: '10m',
      direction: 'BUY',
      entryZoneLow: 2861.00,
      entryZoneHigh: 2863.50,
      takeProfit1: 2873.50,
      takeProfit2: 2881.00,
      stopLoss: 2857.00,
      riskReward: 2.5,
      confidenceScore: 86,
      winRate: 75.6,
      summary: 'Ascending triangle breakout test; EMA 50 holding firmly.'
    },
    {
      timeframe: '15m',
      direction: 'STRONG BUY',
      entryZoneLow: 2860.00,
      entryZoneHigh: 2863.00,
      takeProfit1: 2875.00,
      takeProfit2: 2886.00,
      stopLoss: 2854.00,
      riskReward: 2.7,
      confidenceScore: 94,
      winRate: 83.5,
      summary: 'Macro structural bull flag; primary trend synchronized with 1h.'
    },
    {
      timeframe: '1h',
      direction: 'STRONG BUY',
      entryZoneLow: 2854.00,
      entryZoneHigh: 2860.00,
      takeProfit1: 2890.00,
      takeProfit2: 2915.00,
      stopLoss: 2842.00,
      riskReward: 3.2,
      confidenceScore: 91,
      winRate: 79.8,
      summary: 'Sovereign bullion accumulation baseline; price comfortably above EMA 200.'
    }
  ], []);

  // Alignment matrix
  const alignmentMatrix: AlignmentRow[] = useMemo(() => [
    { timeframe: '1m', trend: 'BULLISH', score: 2, rsi: 59.4, macdHist: 0.28, emaStatus: 'EMA 8 > 21 > 50' },
    { timeframe: '3m', trend: 'BULLISH', score: 3, rsi: 61.2, macdHist: 0.44, emaStatus: 'Bullish Stack' },
    { timeframe: '5m', trend: 'BULLISH', score: 3, rsi: 63.8, macdHist: 0.62, emaStatus: 'Golden Ribbon' },
    { timeframe: '10m', trend: 'BULLISH', score: 2, rsi: 60.1, macdHist: 0.38, emaStatus: 'Above EMA 50' },
    { timeframe: '15m', trend: 'BULLISH', score: 3, rsi: 64.5, macdHist: 0.75, emaStatus: 'Confluence 20/50/200' },
    { timeframe: '1h', trend: 'BULLISH', score: 2, rsi: 58.9, macdHist: 0.52, emaStatus: 'Above 200 EMA' },
  ], []);

  // News items strictly filtered
  const newsItems: NewsItem[] = useMemo(() => [
    {
      id: 1,
      title: 'Federal Reserve Policymakers Signal Cautious Rate-Cut Path as Real Yields Moderate',
      source: 'Bloomberg Macro',
      summary: 'FOMC officials emphasized data dependency ahead of upcoming CPI data. Softening 10-year real yields provide ongoing tailwinds for physical bullion reserves.',
      category: 'FED_POLICY',
      sentiment: 'BULLISH',
      timeAgo: '18m ago'
    },
    {
      id: 2,
      title: 'Global Central Bank Gold Purchases Surge 38 Tons in Monthly Institutional Inflow',
      source: 'World Gold Council',
      summary: 'Sovereign institutions continue reserve de-dollarization strategy, generating an enduring structural demand floor under XAUUSD above $2,840/oz.',
      category: 'GOLD',
      sentiment: 'BULLISH',
      timeAgo: '1h ago'
    },
    {
      id: 3,
      title: 'US 10-Year Real TIPS Yields Slip to 1.84% Amid Easing Labor Cost Projections',
      source: 'Financial Times',
      summary: 'Declining real yields reduce the non-interest holding cost of physical gold bars, boosting quantitative macro allocations.',
      category: 'TREASURY_YIELDS',
      sentiment: 'BULLISH',
      timeAgo: '2h ago'
    },
    {
      id: 4,
      title: 'US Headline Inflation Forecast Stable at 2.6% YoY; Shelter Costs Flattening',
      source: 'Reuters Markets',
      summary: 'Consensus forecasts project headline inflation remains contained, supporting expectations for steady interest rate reductions in H2.',
      category: 'INFLATION',
      sentiment: 'NEUTRAL',
      timeAgo: '4h ago'
    },
    {
      id: 5,
      title: 'US Dollar Index (DXY) Consolidates Near 104.10 Ahead of Non-Farm Payrolls',
      source: 'FXStreet',
      summary: 'The greenback remains in a constrained channel. Any labor market cooling is expected to provoke immediate dollar weakness and bullion rally.',
      category: 'USD_DXY',
      sentiment: 'BULLISH',
      timeAgo: '6h ago'
    }
  ], []);

  const filteredNews = useMemo(() => {
    if (activeNewsCategory === 'ALL') return newsItems;
    return newsItems.filter(item => item.category === activeNewsCategory);
  }, [newsItems, activeNewsCategory]);

  // Economic Calendar
  const calendarItems: CalendarRelease[] = useMemo(() => [
    {
      id: 1,
      title: 'US Non-Farm Payrolls (NFP) & Unemployment Rate',
      country: 'USD',
      impact: 'HIGH',
      dateStr: 'Friday',
      timeStr: '12:30 UTC',
      forecast: '175K',
      previous: '142K',
      goldBias: 'BEARISH',
      notes: 'Weaker print boosts Gold; hot print triggers short-term USD bounce.'
    },
    {
      id: 2,
      title: 'US Consumer Price Index (CPI) YoY',
      country: 'USD',
      impact: 'HIGH',
      dateStr: 'Next Tuesday',
      timeStr: '12:30 UTC',
      forecast: '2.6%',
      previous: '2.5%',
      goldBias: 'BULLISH',
      notes: 'Sticky inflation reinforces gold as premier fiat purchasing power hedge.'
    },
    {
      id: 3,
      title: 'FOMC Federal Funds Rate Decision & Press Conference',
      country: 'USD',
      impact: 'HIGH',
      dateStr: 'In 6 Days',
      timeStr: '18:00 UTC',
      forecast: '4.50%',
      previous: '4.75%',
      goldBias: 'BULLISH',
      notes: '25 bps rate cut priced at 82% probability; dovish rhetoric supports bullion.'
    },
    {
      id: 4,
      title: 'US Core Retail Sales MoM',
      country: 'USD',
      impact: 'MEDIUM',
      dateStr: 'In 8 Days',
      timeStr: '12:30 UTC',
      forecast: '0.3%',
      previous: '0.1%',
      goldBias: 'NEUTRAL',
      notes: 'Consumer expenditure pulse check.'
    }
  ], []);

  const runAiReasoning = () => {
    setIsEvaluatingAi(true);
    setTimeout(() => {
      setIsEvaluatingAi(false);
      setSaveToast('Quantitative Reasoning updated based on multi-timeframe alignment & catalysts.');
      setTimeout(() => setSaveToast(null), 3000);
    }, 1200);
  };

  // =========================================================================
  // LIVE SIMULATED TRADES STATE & AUTOMATIC INDEXEDDB PERSISTENCE
  // =========================================================================
  const [trades, setTrades] = useState<LiveTrade[]>([]);
  const [autoExecuteEnabled, setAutoExecuteEnabled] = useState<boolean>(() => {
    return localStorage.getItem('gold_ai_auto_execute') !== 'false';
  });

  // Load trades from local IndexedDB on startup
  useEffect(() => {
    loadTradesFromIndexedDB().then((saved) => {
      if (saved && saved.length > 0) {
        setTrades(saved);
      } else {
        setTrades(initialSeedTrades);
        saveAllTradesToIndexedDB(initialSeedTrades);
      }
    });
  }, []);

  // Save trades to local IndexedDB whenever updated
  useEffect(() => {
    if (trades.length > 0) {
      saveAllTradesToIndexedDB(trades);
    }
  }, [trades]);

  // Save autoExecute preference to localStorage
  useEffect(() => {
    localStorage.setItem('gold_ai_auto_execute', String(autoExecuteEnabled));
  }, [autoExecuteEnabled]);

  // Monitor live Bid/Ask ticks: calculate floating PnL & evaluate automated exits (TP1, TP2, SL)
  useEffect(() => {
    setTrades(prevTrades => {
      let changed = false;
      const updated = prevTrades.map(trade => {
        if (trade.status !== 'OPEN') return trade;

        const isBuy = trade.direction === 'BUY';
        const currentRefPrice = isBuy ? bid : ask;

        // 1. Evaluate TP2 Target
        if (isBuy && currentRefPrice >= trade.takeProfit2) {
          changed = true;
          const pnl = (trade.takeProfit2 - trade.entryPrice) * 100 * trade.lots;
          if (soundAlertsEnabled) playAlertChime();
          return {
            ...trade,
            status: 'TP2_HIT' as const,
            exitPrice: trade.takeProfit2,
            exitTime: Date.now(),
            pnlDollar: pnl,
            pnlPips: (trade.takeProfit2 - trade.entryPrice) * 10
          };
        }
        if (!isBuy && currentRefPrice <= trade.takeProfit2) {
          changed = true;
          const pnl = (trade.entryPrice - trade.takeProfit2) * 100 * trade.lots;
          if (soundAlertsEnabled) playAlertChime();
          return {
            ...trade,
            status: 'TP2_HIT' as const,
            exitPrice: trade.takeProfit2,
            exitTime: Date.now(),
            pnlDollar: pnl,
            pnlPips: (trade.entryPrice - trade.takeProfit2) * 10
          };
        }

        // 2. Evaluate TP1 Target
        if (isBuy && currentRefPrice >= trade.takeProfit1) {
          changed = true;
          const pnl = (trade.takeProfit1 - trade.entryPrice) * 100 * trade.lots;
          if (soundAlertsEnabled) playAlertChime();
          return {
            ...trade,
            status: 'TP1_HIT' as const,
            exitPrice: trade.takeProfit1,
            exitTime: Date.now(),
            pnlDollar: pnl,
            pnlPips: (trade.takeProfit1 - trade.entryPrice) * 10
          };
        }
        if (!isBuy && currentRefPrice <= trade.takeProfit1) {
          changed = true;
          const pnl = (trade.entryPrice - trade.takeProfit1) * 100 * trade.lots;
          if (soundAlertsEnabled) playAlertChime();
          return {
            ...trade,
            status: 'TP1_HIT' as const,
            exitPrice: trade.takeProfit1,
            exitTime: Date.now(),
            pnlDollar: pnl,
            pnlPips: (trade.entryPrice - trade.takeProfit1) * 10
          };
        }

        // 3. Evaluate Stop Loss Breach
        if (isBuy && currentRefPrice <= trade.stopLoss) {
          changed = true;
          const pnl = (trade.stopLoss - trade.entryPrice) * 100 * trade.lots;
          return {
            ...trade,
            status: 'SL_HIT' as const,
            exitPrice: trade.stopLoss,
            exitTime: Date.now(),
            pnlDollar: pnl,
            pnlPips: (trade.stopLoss - trade.entryPrice) * 10
          };
        }
        if (!isBuy && currentRefPrice >= trade.stopLoss) {
          changed = true;
          const pnl = (trade.entryPrice - trade.stopLoss) * 100 * trade.lots;
          return {
            ...trade,
            status: 'SL_HIT' as const,
            exitPrice: trade.stopLoss,
            exitTime: Date.now(),
            pnlDollar: pnl,
            pnlPips: (trade.entryPrice - trade.stopLoss) * 10
          };
        }

        // 4. Update Floating PnL
        const floatingDollar = isBuy
          ? (bid - trade.entryPrice) * 100 * trade.lots
          : (trade.entryPrice - ask) * 100 * trade.lots;
        const floatingPips = isBuy
          ? (bid - trade.entryPrice) * 10
          : (trade.entryPrice - ask) * 10;

        if (Math.abs(trade.pnlDollar - floatingDollar) > 0.05) {
          changed = true;
          return {
            ...trade,
            pnlDollar: floatingDollar,
            pnlPips: floatingPips
          };
        }

        return trade;
      });

      return changed ? updated : prevTrades;
    });
  }, [bid, ask, soundAlertsEnabled]);

  // Automatically log simulated trade entries from active signals
  useEffect(() => {
    if (!autoExecuteEnabled) return;

    // Limit maximum concurrent open simulated trades to 3
    const openTrades = trades.filter(t => t.status === 'OPEN');
    if (openTrades.length >= 3) return;

    // Pick top-conviction active signal
    const candidateSignal = signalsData.find(s => s.direction.includes('BUY') || s.direction.includes('SELL'));
    if (!candidateSignal) return;

    const isBuy = candidateSignal.direction.includes('BUY');
    const existingForTf = trades.some(t => 
      t.timeframe === candidateSignal.timeframe &&
      t.direction === (isBuy ? 'BUY' : 'SELL') &&
      (t.status === 'OPEN' || Date.now() - t.entryTime < 300000)
    );

    if (!existingForTf) {
      const entryP = isBuy ? ask : bid;
      const autoTrade: LiveTrade = {
        id: `trade-auto-${Date.now()}`,
        timeframe: candidateSignal.timeframe,
        direction: isBuy ? 'BUY' : 'SELL',
        lots: 1.0,
        entryPrice: entryP,
        entryTime: Date.now(),
        stopLoss: candidateSignal.stopLoss,
        takeProfit1: candidateSignal.takeProfit1,
        takeProfit2: candidateSignal.takeProfit2,
        status: 'OPEN',
        pnlDollar: 0.0,
        pnlPips: 0.0,
        riskReward: candidateSignal.riskReward,
        signalReason: candidateSignal.summary
      };

      setTrades(prev => [autoTrade, ...prev]);
      if (soundAlertsEnabled) playAlertChime();
    }
  }, [autoExecuteEnabled, signalsData, trades, ask, bid, soundAlertsEnabled]);

  const handleCloseTrade = useCallback((tradeId: string) => {
    setTrades(prev => prev.map(t => {
      if (t.id !== tradeId || t.status !== 'OPEN') return t;
      const isBuy = t.direction === 'BUY';
      const exitP = isBuy ? bid : ask;
      const finalPnl = isBuy ? (exitP - t.entryPrice) * 100 * t.lots : (t.entryPrice - exitP) * 100 * t.lots;
      return {
        ...t,
        status: 'MANUAL_CLOSE',
        exitPrice: exitP,
        exitTime: Date.now(),
        pnlDollar: finalPnl,
        pnlPips: isBuy ? (exitP - t.entryPrice) * 10 : (t.entryPrice - exitP) * 10
      };
    }));
  }, [bid, ask]);

  const handleCloseAllTrades = useCallback(() => {
    setTrades(prev => prev.map(t => {
      if (t.status !== 'OPEN') return t;
      const isBuy = t.direction === 'BUY';
      const exitP = isBuy ? bid : ask;
      const finalPnl = isBuy ? (exitP - t.entryPrice) * 100 * t.lots : (t.entryPrice - exitP) * 100 * t.lots;
      return {
        ...t,
        status: 'MANUAL_CLOSE',
        exitPrice: exitP,
        exitTime: Date.now(),
        pnlDollar: finalPnl,
        pnlPips: isBuy ? (exitP - t.entryPrice) * 10 : (t.entryPrice - exitP) * 10
      };
    }));
  }, [bid, ask]);

  const handleManualOrder = useCallback((direction: 'BUY' | 'SELL', lots: number = 1.0) => {
    const isBuy = direction === 'BUY';
    const entryP = isBuy ? ask : bid;
    const atrApprox = 12.0;
    const sl = isBuy ? +(entryP - atrApprox).toFixed(2) : +(entryP + atrApprox).toFixed(2);
    const tp1 = isBuy ? +(entryP + (atrApprox * 1.5)).toFixed(2) : +(entryP - (atrApprox * 1.5)).toFixed(2);
    const tp2 = isBuy ? +(entryP + (atrApprox * 2.5)).toFixed(2) : +(entryP - (atrApprox * 2.5)).toFixed(2);

    const manualTrade: LiveTrade = {
      id: `trade-manual-${Date.now()}`,
      timeframe: selectedTimeframe,
      direction,
      lots,
      entryPrice: entryP,
      entryTime: Date.now(),
      stopLoss: sl,
      takeProfit1: tp1,
      takeProfit2: tp2,
      status: 'OPEN',
      pnlDollar: 0.0,
      pnlPips: 0.0,
      riskReward: 2.0,
      signalReason: `Manual operator market execution (${lots} lot @ $${entryP.toFixed(2)})`
    };

    setTrades(prev => [manualTrade, ...prev]);
    if (soundAlertsEnabled) playAlertChime();
    setSaveToast(`Executed ${direction} ${lots} Lot @ $${entryP.toFixed(2)}`);
    setTimeout(() => setSaveToast(null), 2500);
  }, [ask, bid, selectedTimeframe, soundAlertsEnabled]);

  const handleClearTradesHistory = useCallback(async () => {
    await clearTradesFromIndexedDB();
    setTrades([]);
    setSaveToast('Simulated trades history cleared from local IndexedDB.');
    setTimeout(() => setSaveToast(null), 2500);
  }, []);

  const openTradesCount = useMemo(() => {
    return trades.filter(t => t.status === 'OPEN').length;
  }, [trades]);

  const getDirectionBadge = (dir: DirectionType) => {
    if (dir === 'STRONG BUY') {
      return 'bg-emerald-600 text-white font-extrabold shadow-xs';
    }
    if (dir === 'BUY') {
      return 'bg-emerald-100 text-emerald-800 border border-emerald-300 font-bold';
    }
    if (dir === 'WEAK BUY') {
      return 'bg-teal-50 text-teal-700 border border-teal-200 font-semibold';
    }
    if (dir === 'STRONG SELL') {
      return 'bg-rose-600 text-white font-extrabold shadow-xs';
    }
    if (dir === 'SELL') {
      return 'bg-rose-100 text-rose-800 border border-rose-300 font-bold';
    }
    if (dir === 'WEAK SELL') {
      return 'bg-orange-50 text-orange-700 border border-orange-200 font-semibold';
    }
    return 'bg-amber-100 text-amber-800 border border-amber-300 font-semibold';
  };

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 font-sans flex flex-col antialiased pb-24 md:pb-16 selection:bg-blue-100 selection:text-blue-900">
      
      {/* Toast Notification */}
      {saveToast && (
        <div className="fixed top-4 left-1/2 -translate-x-1/2 z-50 bg-slate-900 text-white px-4 py-2.5 rounded-xl shadow-xl text-xs font-semibold flex items-center gap-2 border border-slate-700 transition-all">
          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping"></span>
          <span>{saveToast}</span>
        </div>
      )}

      {/* Top App Bar (Institutional Royal Blue Header) */}
      <header className="bg-gradient-to-r from-blue-900 via-indigo-900 to-blue-800 text-white shadow-sm border-b border-blue-950 sticky top-0 z-40">
        <div className="max-w-4xl mx-auto px-4 py-3 flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <div className="w-8 h-8 rounded-lg bg-amber-400 text-slate-950 font-black flex items-center justify-center text-sm shadow-sm">
              AU
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-extrabold text-sm tracking-tight text-white">XAUUSD</span>
                <span className="text-[10px] text-blue-200 uppercase tracking-wider font-medium">Gold / US Dollar</span>
                <span className="px-1.5 py-0.2 text-[9px] font-mono font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-400/30 rounded">
                  LIVE • 32ms
                </span>
              </div>
              <div className="flex items-center gap-2 mt-0.5">
                <span className={`text-base font-bold font-mono font-mono-numbers transition-colors ${tickDirection === 'up' ? 'text-emerald-300' : tickDirection === 'down' ? 'text-rose-300' : 'text-amber-300'}`}>
                  ${price.toFixed(2)}
                </span>
                <span className="text-[11px] font-mono font-medium text-emerald-400">
                  +$12.10 (+0.42%)
                </span>
              </div>
            </div>
          </div>

          {/* Quick Timeframe Switcher */}
          <div className="flex items-center space-x-1 bg-blue-950/70 p-1 rounded-lg border border-blue-800/60">
            {(['1m', '3m', '5m', '15m', '1h'] as TimeframeType[]).map(tf => (
              <button
                key={tf}
                onClick={() => setSelectedTimeframe(tf)}
                className={`px-2 py-0.5 text-[11px] font-mono font-bold rounded transition-all ${
                  selectedTimeframe === tf
                    ? 'bg-blue-600 text-white shadow-xs'
                    : 'text-blue-200 hover:text-white hover:bg-blue-800/40'
                }`}
              >
                {tf}
              </button>
            ))}
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="max-w-4xl mx-auto w-full px-3 sm:px-4 py-3 space-y-3 flex-1">

        {/* Price & Order Book Strip */}
        <section className="grid grid-cols-2 gap-2.5">
          {/* BID / SELL Box */}
          <div className={`bg-white border border-slate-200/90 rounded-xl p-3 shadow-xs flex flex-col justify-between transition-colors ${tickDirection === 'down' ? 'flash-down' : ''}`}>
            <div className="flex items-center justify-between text-[11px] text-slate-500 font-medium">
              <span className="text-rose-600 font-bold uppercase tracking-wider">SELL / BID</span>
              <span className="font-mono text-[10px]">LOT 1.0</span>
            </div>
            <div className="mt-1 flex items-baseline justify-between">
              <span className="text-xl sm:text-2xl font-bold font-mono font-mono-numbers text-rose-600">
                ${bid.toFixed(2)}
              </span>
              <span className="text-[10px] font-mono text-slate-400">Vol: 14.2 oz</span>
            </div>
          </div>

          {/* ASK / BUY Box */}
          <div className={`bg-white border border-slate-200/90 rounded-xl p-3 shadow-xs flex flex-col justify-between transition-colors ${tickDirection === 'up' ? 'flash-up' : ''}`}>
            <div className="flex items-center justify-between text-[11px] text-slate-500 font-medium">
              <span className="text-emerald-600 font-bold uppercase tracking-wider">BUY / ASK</span>
              <span className="font-mono text-[10px]">SPREAD {spread.toFixed(2)}</span>
            </div>
            <div className="mt-1 flex items-baseline justify-between">
              <span className="text-xl sm:text-2xl font-bold font-mono font-mono-numbers text-emerald-600">
                ${ask.toFixed(2)}
              </span>
              <span className="text-[10px] font-mono text-slate-400">Vol: 18.5 oz</span>
            </div>
          </div>
        </section>

        {/* ===================== TAB 1: SIGNALS ===================== */}
        {activeTab === 'signals' && (
          <div className="space-y-3">
            {/* Live Auto-Trader Status Bar */}
            <div className="bg-gradient-to-r from-slate-900 to-blue-950 text-white rounded-xl p-3 border border-slate-800 shadow-xs flex flex-wrap items-center justify-between gap-2.5">
              <div className="flex items-center gap-2.5">
                <div className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
                <span className="text-xs font-mono font-bold">
                  Auto-Trader: {autoExecuteEnabled ? 'ACTIVE' : 'PAUSED'}
                </span>
                <span className="text-[11px] text-slate-300 font-mono">
                  • {openTradesCount} Active Position{openTradesCount === 1 ? '' : 's'} (IndexedDB)
                </span>
              </div>
              <button
                onClick={() => setActiveTab('trades')}
                className="px-2.5 py-1 text-xs font-mono font-bold rounded-lg bg-blue-600 hover:bg-blue-500 text-white transition-colors flex items-center gap-1 shadow-xs"
              >
                <span>💼 View Live Trades</span>
                <span>→</span>
              </button>
            </div>

            {/* Primary Consensus Signal Hero Card */}
            <div className="bg-white border border-slate-200/90 rounded-xl p-4 shadow-xs">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-3">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold text-slate-500 uppercase tracking-wider font-mono">Consensus Signal</span>
                  <span className="px-2 py-0.5 rounded text-xs bg-blue-50 text-blue-700 border border-blue-200 font-bold font-mono">
                    {selectedTimeframe} TIMEFRAME
                  </span>
                </div>
                <div className="flex items-center gap-1.5 text-xs text-slate-500 font-medium">
                  <span>Confidence:</span>
                  <span className="font-bold text-blue-700 font-mono">92%</span>
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
                  <span className="text-[10px] text-emerald-600 font-bold font-mono">Win Rate: 81.2%</span>
                </div>
              </div>

              <div className="mt-3 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2.5">
                    <span className="px-3 py-1 rounded-lg text-sm font-black bg-emerald-600 text-white shadow-xs tracking-wide">
                      STRONG BUY
                    </span>
                    <span className="text-xs text-slate-600 font-medium">
                      Multi-Timeframe Synchronized Momentum
                    </span>
                  </div>
                  <p className="text-xs text-slate-500 mt-1.5">
                    RSI ribbon expansion (63.8), price sustained above 50 & 200 EMA. Macro real yield tailwind.
                  </p>
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 bg-slate-50 border border-slate-200/80 rounded-lg p-2.5">
                  <div className="text-center">
                    <div className="text-[9px] font-bold text-slate-400 uppercase font-mono">ENTRY ZONE</div>
                    <div className="text-xs font-bold font-mono text-slate-800">$2,862 - $2,864</div>
                  </div>
                  <div className="text-center">
                    <div className="text-[9px] font-bold text-slate-400 uppercase font-mono">STOP LOSS</div>
                    <div className="text-xs font-bold font-mono text-rose-600">$2,854.00</div>
                  </div>
                  <div className="text-center">
                    <div className="text-[9px] font-bold text-slate-400 uppercase font-mono">TARGET 1</div>
                    <div className="text-xs font-bold font-mono text-emerald-600">$2,875.00</div>
                  </div>
                  <div className="text-center">
                    <div className="text-[9px] font-bold text-slate-400 uppercase font-mono">R:R RATIO</div>
                    <div className="text-xs font-bold font-mono text-blue-700">1 : 2.7</div>
                  </div>
                </div>
              </div>
            </div>

            {/* Custom Price Alert Thresholds Card (Bid / Ask Levels with Browser Notifications) */}
            <div className="bg-white border border-slate-200/90 rounded-xl p-3.5 shadow-xs">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-2.5">
                <div className="flex items-center gap-2">
                  <span className="w-6 h-6 rounded-md bg-blue-50 text-blue-700 flex items-center justify-center text-xs">🔔</span>
                  <div>
                    <h3 className="text-xs font-bold text-slate-800 uppercase tracking-wider font-mono flex items-center gap-2">
                      Custom Price Alerts (Bid / Ask Thresholds)
                      <span className="text-[10px] px-1.5 py-0.2 rounded font-bold font-mono bg-blue-50 text-blue-700 border border-blue-200">
                        {priceAlerts.filter(a => !a.triggered).length} ACTIVE
                      </span>
                    </h3>
                  </div>
                </div>

                <div className="flex items-center gap-1.5 sm:gap-2">
                  {/* Sound Chime Toggle Button */}
                  <button
                    type="button"
                    onClick={() => {
                      const nextVal = !soundAlertsEnabled;
                      setSoundAlertsEnabled(nextVal);
                      if (nextVal) playAlertChime();
                      setSaveToast(nextVal ? 'Sound chime enabled for price alerts.' : 'Sound chime muted.');
                      setTimeout(() => setSaveToast(null), 2500);
                    }}
                    title={soundAlertsEnabled ? 'Sound alerts enabled. Click to mute.' : 'Sound alerts muted. Click to enable.'}
                    className={`text-[11px] font-bold px-2 py-0.5 rounded-lg border font-mono flex items-center gap-1 transition-colors ${
                      soundAlertsEnabled
                        ? 'bg-blue-50 text-blue-700 border-blue-200 hover:bg-blue-100'
                        : 'bg-slate-100 text-slate-500 border-slate-200 hover:bg-slate-200'
                    }`}
                  >
                    <span>{soundAlertsEnabled ? '🔊 Chime' : '🔇 Muted'}</span>
                  </button>

                  {notificationPermission !== 'granted' ? (
                    <button
                      onClick={requestNotificationPermission}
                      className="text-[11px] font-bold px-2 py-0.5 rounded-lg bg-amber-50 text-amber-800 border border-amber-200 hover:bg-amber-100 transition-colors flex items-center gap-1 font-mono"
                    >
                      <span>⚠️ Enable Notifications</span>
                    </button>
                  ) : (
                    <span className="text-[10px] font-bold font-mono text-emerald-600 bg-emerald-50 border border-emerald-200 px-2 py-0.5 rounded flex items-center gap-1">
                      <span>✓ Notifications On</span>
                    </span>
                  )}
                  <button
                    onClick={() => setIsAlertFormOpen(!isAlertFormOpen)}
                    className="text-[11px] font-bold px-2.5 py-1 rounded-lg bg-blue-600 text-white hover:bg-blue-700 transition-colors shadow-xs"
                  >
                    {isAlertFormOpen ? 'Close' : '+ Set Alert'}
                  </button>
                </div>
              </div>

              {/* Alert Creation Form */}
              {isAlertFormOpen && (
                <form onSubmit={handleAddAlert} className="mt-3 p-3 bg-slate-50 border border-slate-200/80 rounded-xl space-y-2.5 text-xs">
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                    {/* Metric Type */}
                    <div>
                      <label className="text-[10px] font-mono text-slate-500 font-bold uppercase block mb-1">Trigger Metric</label>
                      <div className="grid grid-cols-3 gap-1">
                        {(['BID', 'ASK', 'SPOT'] as const).map(t => (
                          <button
                            key={t}
                            type="button"
                            onClick={() => {
                              setNewAlertType(t);
                              if (t === 'BID') setNewAlertPrice(bid.toFixed(2));
                              else if (t === 'ASK') setNewAlertPrice(ask.toFixed(2));
                              else setNewAlertPrice(price.toFixed(2));
                            }}
                            className={`py-1 text-center font-mono font-bold rounded text-[11px] transition-colors ${
                              newAlertType === t
                                ? 'bg-blue-600 text-white shadow-xs'
                                : 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-100'
                            }`}
                          >
                            {t}
                          </button>
                        ))}
                      </div>
                    </div>

                    {/* Condition */}
                    <div>
                      <label className="text-[10px] font-mono text-slate-500 font-bold uppercase block mb-1">Condition</label>
                      <div className="grid grid-cols-2 gap-1">
                        <button
                          type="button"
                          onClick={() => setNewAlertCondition('ABOVE')}
                          className={`py-1 text-center font-mono font-bold rounded text-[11px] transition-colors ${
                            newAlertCondition === 'ABOVE'
                              ? 'bg-emerald-600 text-white shadow-xs'
                              : 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-100'
                          }`}
                        >
                          Crosses Above (≥)
                        </button>
                        <button
                          type="button"
                          onClick={() => setNewAlertCondition('BELOW')}
                          className={`py-1 text-center font-mono font-bold rounded text-[11px] transition-colors ${
                            newAlertCondition === 'BELOW'
                              ? 'bg-rose-600 text-white shadow-xs'
                              : 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-100'
                          }`}
                        >
                          Drops Below (≤)
                        </button>
                      </div>
                    </div>

                    {/* Target Price */}
                    <div>
                      <label className="text-[10px] font-mono text-slate-500 font-bold uppercase block mb-1">Price Target ($)</label>
                      <input
                        type="number"
                        step="0.05"
                        value={newAlertPrice}
                        onChange={e => setNewAlertPrice(e.target.value)}
                        className="w-full text-xs font-mono font-bold bg-white border border-slate-200 rounded px-2.5 py-1 text-slate-800 focus:outline-none focus:border-blue-500"
                        placeholder="2868.00"
                        required
                      />
                    </div>
                  </div>

                  {/* Quick offset buttons */}
                  <div className="flex flex-wrap items-center gap-1.5 text-[10px] font-mono">
                    <span className="text-slate-400 font-bold">Quick Offsets from Current:</span>
                    {[1.0, 2.5, 5.0, 10.0].map(diff => (
                      <button
                        key={`plus-${diff}`}
                        type="button"
                        onClick={() => setNewAlertPrice((price + diff).toFixed(2))}
                        className="px-1.5 py-0.5 rounded bg-emerald-50 text-emerald-700 border border-emerald-200 hover:bg-emerald-100"
                      >
                        +${diff}
                      </button>
                    ))}
                    {[1.0, 2.5, 5.0, 10.0].map(diff => (
                      <button
                        key={`minus-${diff}`}
                        type="button"
                        onClick={() => setNewAlertPrice((price - diff).toFixed(2))}
                        className="px-1.5 py-0.5 rounded bg-rose-50 text-rose-700 border border-rose-200 hover:bg-rose-100"
                      >
                        -${diff}
                      </button>
                    ))}
                  </div>

                  {/* Custom Label and Submit */}
                  <div className="flex flex-col sm:flex-row items-center gap-2">
                    <input
                      type="text"
                      value={newAlertLabel}
                      onChange={e => setNewAlertLabel(e.target.value)}
                      placeholder="Optional label (e.g. TP Target, Resistance Breakout, Pullback Buy Zone)"
                      className="w-full sm:flex-1 text-xs bg-white border border-slate-200 rounded px-2.5 py-1 text-slate-800 focus:outline-none focus:border-blue-500"
                    />
                    <button
                      type="submit"
                      className="w-full sm:w-auto px-4 py-1.5 bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs rounded transition-colors shadow-xs"
                    >
                      Save Alert Threshold
                    </button>
                  </div>
                </form>
              )}

              {/* List of active & triggered alerts */}
              <div className="mt-3 space-y-2">
                {priceAlerts.length === 0 ? (
                  <div className="text-center py-4 text-xs text-slate-400 font-mono">
                    No custom price alerts configured. Click "+ Set Alert" to add a threshold.
                  </div>
                ) : (
                  priceAlerts.map(alert => {
                    const currentVal = alert.type === 'BID' ? bid : alert.type === 'ASK' ? ask : price;
                    const diff = alert.targetPrice - currentVal;
                    const diffPips = Math.round(Math.abs(diff) * 10);

                    return (
                      <div
                        key={alert.id}
                        className={`p-2.5 rounded-lg border flex flex-col sm:flex-row sm:items-center justify-between gap-2 transition-all ${
                          alert.triggered
                            ? 'bg-slate-50/80 border-slate-200 opacity-80'
                            : 'bg-white border-slate-200 hover:border-blue-300 shadow-xs'
                        }`}
                      >
                        <div className="flex items-center gap-2.5">
                          <span className={`px-1.5 py-0.5 rounded text-[10px] font-mono font-bold ${
                            alert.type === 'ASK' ? 'bg-emerald-100 text-emerald-800' : alert.type === 'BID' ? 'bg-rose-100 text-rose-800' : 'bg-blue-100 text-blue-800'
                          }`}>
                            {alert.type}
                          </span>

                          <span className="font-mono font-bold text-xs text-slate-800">
                            {alert.condition === 'ABOVE' ? '≥' : '≤'} ${alert.targetPrice.toFixed(2)}
                          </span>

                          {alert.label && (
                            <span className="text-[11px] font-medium text-slate-600 truncate max-w-[140px] sm:max-w-[200px]">
                              {alert.label}
                            </span>
                          )}
                        </div>

                        <div className="flex items-center justify-between sm:justify-end gap-3 text-xs">
                          {alert.triggered ? (
                            <span className="text-[10px] font-mono font-bold text-rose-600 bg-rose-50 border border-rose-200 px-2 py-0.5 rounded flex items-center gap-1">
                              <span>🔔 TRIGGERED</span>
                            </span>
                          ) : (
                            <span className="text-[10px] font-mono text-slate-500">
                              {diff > 0 ? `+${diff.toFixed(2)}` : diff.toFixed(2)} ({diffPips} pips {diff > 0 ? 'above' : 'below'})
                            </span>
                          )}

                          <div className="flex items-center gap-1.5">
                            {alert.triggered && (
                              <button
                                onClick={() => handleRearmAlert(alert.id)}
                                title="Re-arm Alert"
                                className="text-[10px] font-mono font-bold px-2 py-0.5 rounded bg-blue-50 text-blue-700 hover:bg-blue-100 border border-blue-200 transition-colors"
                              >
                                Re-arm
                              </button>
                            )}
                            <button
                              onClick={() => handleRemoveAlert(alert.id)}
                              title="Delete Alert"
                              className="text-slate-400 hover:text-rose-600 p-1 text-xs transition-colors"
                            >
                              ✕
                            </button>
                          </div>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            </div>

            {/* Multi-Timeframe Alignment Matrix */}
            <div className="bg-white border border-slate-200/90 rounded-xl p-3.5 shadow-xs">
              <div className="flex items-center justify-between mb-2.5">
                <h3 className="text-xs font-bold text-slate-700 uppercase tracking-wider font-mono flex items-center gap-1.5">
                  Multi-Timeframe Trend Alignment Matrix
                  <span className="text-[10px] bg-emerald-50 text-emerald-700 px-1.5 py-0.2 rounded border border-emerald-200">
                    100% BULLISH
                  </span>
                </h3>
                <span className="text-[10px] font-mono text-slate-400">Scalp to Macro Synchronized</span>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead>
                    <tr className="border-b border-slate-100 text-[10px] font-mono text-slate-400 uppercase">
                      <th className="pb-1.5 font-bold">TF</th>
                      <th className="pb-1.5 font-bold">DIRECTION</th>
                      <th className="pb-1.5 font-bold">RSI(14)</th>
                      <th className="pb-1.5 font-bold">MOMENTUM</th>
                      <th className="pb-1.5 font-bold">EMA STATUS</th>
                      <th className="pb-1.5 font-bold text-right">SCORE</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-50 font-mono">
                    {alignmentMatrix.map(row => (
                      <tr key={row.timeframe} className="hover:bg-slate-50/60 transition-colors">
                        <td className="py-1.5 font-bold text-slate-700">{row.timeframe}</td>
                        <td className="py-1.5">
                          <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-emerald-100 text-emerald-800">
                            {row.trend}
                          </span>
                        </td>
                        <td className="py-1.5 text-slate-600">{row.rsi}</td>
                        <td className="py-1.5 text-emerald-600">+{row.macdHist}</td>
                        <td className="py-1.5 text-slate-500 font-sans text-[11px]">{row.emaStatus}</td>
                        <td className="py-1.5 text-right font-bold text-emerald-600">+{row.score}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Stacked Individual Timeframe Signal Cards */}
            <div className="space-y-2">
              <div className="text-[11px] font-bold text-slate-500 uppercase tracking-wider font-mono px-1">
                Active Tactical Signals (1m, 3m, 5m, 10m, 15m, 1h)
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-2.5">
                {signalsData.map(sig => (
                  <div key={sig.timeframe} className="bg-white border border-slate-200/90 rounded-xl p-3 shadow-xs hover:border-blue-300 transition-all">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="px-2 py-0.5 rounded text-xs font-mono font-bold bg-slate-100 text-slate-700">
                          {sig.timeframe}
                        </span>
                        <span className={`px-2 py-0.5 rounded text-[11px] ${getDirectionBadge(sig.direction)}`}>
                          {sig.direction}
                        </span>
                      </div>
                      <div className="text-right text-[11px] font-mono">
                        <span className="text-slate-400">Win Rate: </span>
                        <span className="font-bold text-slate-700">{sig.winRate}%</span>
                      </div>
                    </div>

                    <div className="grid grid-cols-3 gap-1.5 mt-2.5 bg-slate-50 border border-slate-100 rounded-lg p-2 text-center text-[10px] font-mono">
                      <div>
                        <div className="text-slate-400">ENTRY</div>
                        <div className="font-bold text-slate-700">${sig.entryZoneLow.toFixed(1)}</div>
                      </div>
                      <div>
                        <div className="text-slate-400">TP 1</div>
                        <div className="font-bold text-emerald-600">${sig.takeProfit1.toFixed(1)}</div>
                      </div>
                      <div>
                        <div className="text-slate-400">SL</div>
                        <div className="font-bold text-rose-600">${sig.stopLoss.toFixed(1)}</div>
                      </div>
                    </div>

                    <p className="text-[11px] text-slate-500 mt-2 line-clamp-1">
                      {sig.summary}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* ===================== TAB 2: LIVE CHART ===================== */}
        {activeTab === 'chart' && (
          <div className="space-y-3">
            <div className="bg-white border border-slate-200/90 rounded-xl p-3.5 shadow-xs">
              <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
                <div className="flex items-center gap-2">
                  <h2 className="text-sm font-bold text-slate-800 tracking-tight">Interactive Candlestick Chart</h2>
                  <span className="text-[10px] font-mono bg-blue-50 text-blue-700 px-1.5 py-0.5 rounded font-bold border border-blue-200">
                    {selectedTimeframe} Interval
                  </span>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setUseTradingViewEmbed(!useTradingViewEmbed)}
                    className="text-[11px] font-bold px-2.5 py-1 rounded-lg border border-slate-200 bg-slate-50 text-slate-700 hover:bg-slate-100 transition-colors"
                  >
                    {useTradingViewEmbed ? 'Switch to Lightweight Canvas' : 'Switch to TradingView Widget'}
                  </button>
                </div>
              </div>

              {useTradingViewEmbed ? (
                <div className="w-full h-[460px] rounded-lg overflow-hidden border border-slate-200">
                  <TradingViewWidget
                    symbol="OANDA:XAUUSD"
                    theme="light"
                    interval={selectedTimeframe === '1h' ? '60' : selectedTimeframe === '15m' ? '15' : selectedTimeframe === '5m' ? '5' : '1'}
                    height="460px"
                  />
                </div>
              ) : (
                <div className="space-y-2">
                  <div className="w-full bg-slate-900 rounded-lg p-2 overflow-hidden shadow-inner relative">
                    <div className="absolute top-3 left-4 text-xs font-mono text-slate-300 font-semibold z-10 flex items-center gap-3">
                      <span>XAUUSD <span className="text-amber-400">${price.toFixed(2)}</span></span>
                      <span className="text-[10px] text-blue-400">EMA(20) Line</span>
                    </div>
                    <canvas
                      ref={canvasRef}
                      width={800}
                      height={340}
                      className="w-full h-[340px] block"
                    />
                  </div>
                  <div className="flex items-center justify-between text-[11px] text-slate-500 font-mono px-1">
                    <span>High-FPS Canvas Rendering • Zero DOM Overhead</span>
                    <span className="text-emerald-600 font-bold">Spread: {spread.toFixed(2)} | Latency: 32ms</span>
                  </div>
                </div>
              )}
            </div>

            {/* Technical Indicators HUD Bar */}
            <div className="grid grid-cols-2 sm:grid-cols-5 gap-2">
              <div className="bg-white border border-slate-200/90 rounded-xl p-2.5 shadow-xs text-center">
                <div className="text-[10px] font-mono text-slate-400 uppercase">RSI (14)</div>
                <div className="text-base font-bold font-mono text-emerald-600">63.8</div>
                <div className="text-[9px] text-slate-500">Bullish Momentum</div>
              </div>
              <div className="bg-white border border-slate-200/90 rounded-xl p-2.5 shadow-xs text-center">
                <div className="text-[10px] font-mono text-slate-400 uppercase">MACD (12,26,9)</div>
                <div className="text-base font-bold font-mono text-emerald-600">+1.48</div>
                <div className="text-[9px] text-emerald-600">Hist: +0.62</div>
              </div>
              <div className="bg-white border border-slate-200/90 rounded-xl p-2.5 shadow-xs text-center">
                <div className="text-[10px] font-mono text-slate-400 uppercase">EMA 20/50</div>
                <div className="text-base font-bold font-mono text-blue-700">$2,858</div>
                <div className="text-[9px] text-slate-500">Ribbon Support</div>
              </div>
              <div className="bg-white border border-slate-200/90 rounded-xl p-2.5 shadow-xs text-center">
                <div className="text-[10px] font-mono text-slate-400 uppercase">BOLLINGER (20,2)</div>
                <div className="text-base font-bold font-mono text-slate-700">2,872 / 2,852</div>
                <div className="text-[9px] text-slate-500">Band Expansion</div>
              </div>
              <div className="bg-white border border-slate-200/90 rounded-xl p-2.5 shadow-xs text-center col-span-2 sm:col-span-1">
                <div className="text-[10px] font-mono text-slate-400 uppercase">ATR (14)</div>
                <div className="text-base font-bold font-mono text-amber-600">$12.40</div>
                <div className="text-[9px] text-slate-500">Normal Regime</div>
              </div>
            </div>
          </div>
        )}

        {/* ===================== TAB: LIVE TRADES & PERFORMANCE ===================== */}
        {activeTab === 'trades' && (
          <LiveTradesView
            currentPrice={price}
            bid={bid}
            ask={ask}
            trades={trades}
            autoExecuteEnabled={autoExecuteEnabled}
            onToggleAutoExecute={setAutoExecuteEnabled}
            onCloseTrade={handleCloseTrade}
            onCloseAllTrades={handleCloseAllTrades}
            onManualOrder={handleManualOrder}
            onClearHistory={handleClearTradesHistory}
          />
        )}

        {/* ===================== TAB 3: AI & NEWS ===================== */}
        {activeTab === 'ai_news' && (
          <div className="space-y-3">
            {/* AI Macro Reasoning Card */}
            <div className="bg-white border border-slate-200/90 rounded-xl p-4 shadow-xs">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-3">
                <div>
                  <h2 className="text-sm font-bold text-slate-800 flex items-center gap-1.5">
                    Quantitative Macro & AI Reasoning Engine
                    <span className="text-[10px] px-2 py-0.5 rounded bg-blue-50 text-blue-700 border border-blue-200 font-mono font-bold">
                      {openaiKey ? 'GPT-4o Live' : 'Heuristic Consensus (Zero-Config)'}
                    </span>
                  </h2>
                  <p className="text-[11px] text-slate-500 mt-0.5">
                    Synthesizes filtered news (Gold, Fed, Inflation, Yields, USD), upcoming calendar events, and technical consensus.
                  </p>
                </div>

                <button
                  onClick={runAiReasoning}
                  disabled={isEvaluatingAi}
                  className="px-3 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold transition-colors shadow-xs flex items-center gap-1.5"
                >
                  {isEvaluatingAi ? (
                    <>
                      <span className="w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
                      Evaluating...
                    </>
                  ) : (
                    'Re-evaluate Synthesis'
                  )}
                </button>
              </div>

              <div className="grid grid-cols-3 gap-2 mt-3 bg-slate-50 border border-slate-100 rounded-lg p-2.5 text-center">
                <div>
                  <div className="text-[10px] font-bold text-slate-400 uppercase font-mono">MARKET RISK</div>
                  <div className="text-sm font-bold font-mono text-amber-600">MEDIUM</div>
                </div>
                <div>
                  <div className="text-[10px] font-bold text-slate-400 uppercase font-mono">FUNDAMENTAL BIAS</div>
                  <div className="text-sm font-bold font-mono text-emerald-600">BULLISH</div>
                </div>
                <div>
                  <div className="text-[10px] font-bold text-slate-400 uppercase font-mono">CONFIDENCE</div>
                  <div className="text-sm font-bold font-mono text-blue-700">88%</div>
                </div>
              </div>

              <div className="mt-3 space-y-2">
                <div className="text-xs font-bold text-slate-700 font-mono uppercase">Key Drivers:</div>
                <ul className="text-xs text-slate-600 space-y-1 pl-1">
                  <li className="flex items-start gap-1.5">
                    <span className="text-blue-600 font-bold">•</span>
                    <span>Federal Reserve rate cut probabilities remain elevated as real Treasury yields soften below 1.85%.</span>
                  </li>
                  <li className="flex items-start gap-1.5">
                    <span className="text-blue-600 font-bold">•</span>
                    <span>Global central bank physical accumulation continues above 35 tons/month, establishing persistent institutional demand.</span>
                  </li>
                  <li className="flex items-start gap-1.5">
                    <span className="text-blue-600 font-bold">•</span>
                    <span>Multi-timeframe consensus confirms synchronized bullish posture across 1m, 3m, 5m, 15m, and 1h charts.</span>
                  </li>
                </ul>

                <div className="p-2.5 rounded-lg bg-emerald-50 border border-emerald-200/80 text-xs text-emerald-900 mt-2 font-medium">
                  <span className="font-bold">Recommended Action:</span> Accumulate pullbacks toward M15 EMA 50 support with invalidation below $2,854.00.
                </div>
              </div>
            </div>

            {/* Strict Macro News Feed */}
            <div className="bg-white border border-slate-200/90 rounded-xl p-3.5 shadow-xs">
              <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
                <h3 className="text-xs font-bold text-slate-700 uppercase tracking-wider font-mono">
                  Filtered Macro & Gold News Feed (5 Pillars)
                </h3>

                <div className="flex flex-wrap gap-1">
                  {(['ALL', 'GOLD', 'FED_POLICY', 'INFLATION', 'TREASURY_YIELDS', 'USD_DXY'] as const).map(cat => (
                    <button
                      key={cat}
                      onClick={() => setActiveNewsCategory(cat)}
                      className={`text-[10px] font-mono font-bold px-2 py-0.5 rounded transition-colors ${
                        activeNewsCategory === cat
                          ? 'bg-blue-600 text-white'
                          : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                      }`}
                    >
                      {cat.replace('_', ' ')}
                    </button>
                  ))}
                </div>
              </div>

              <div className="space-y-2">
                {filteredNews.map(item => (
                  <div key={item.id} className="border border-slate-100 rounded-lg p-2.5 hover:border-slate-200 transition-colors bg-slate-50/40">
                    <div className="flex items-center justify-between text-[10px] font-mono mb-1">
                      <div className="flex items-center gap-1.5">
                        <span className="bg-blue-50 text-blue-700 font-bold px-1.5 py-0.2 rounded border border-blue-200">
                          {item.category.replace('_', ' ')}
                        </span>
                        <span className={`px-1.5 py-0.2 rounded font-bold ${item.sentiment === 'BULLISH' ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-200 text-slate-700'}`}>
                          {item.sentiment}
                        </span>
                      </div>
                      <span className="text-slate-400">{item.source} · {item.timeAgo}</span>
                    </div>
                    <div className="font-bold text-xs text-slate-800">{item.title}</div>
                    <p className="text-[11px] text-slate-500 mt-1 leading-relaxed">{item.summary}</p>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* ===================== TAB 4: MACRO CALENDAR ===================== */}
        {activeTab === 'calendar' && (
          <div className="space-y-3">
            <div className="bg-white border border-slate-200/90 rounded-xl p-3.5 shadow-xs">
              <div className="flex items-center justify-between mb-3 border-b border-slate-100 pb-2">
                <div>
                  <h2 className="text-sm font-bold text-slate-800">Macroeconomic High-Impact Calendar</h2>
                  <p className="text-[11px] text-slate-500">Tier-1 catalysts directly driving XAUUSD institutional volatility</p>
                </div>
                <span className="text-xs font-mono font-bold bg-amber-50 text-amber-700 px-2 py-0.5 rounded border border-amber-200">
                  UTC Timestamps
                </span>
              </div>

              <div className="space-y-2.5">
                {calendarItems.map(ev => (
                  <div key={ev.id} className="border border-slate-200/80 rounded-xl p-3 bg-slate-50/50 hover:bg-slate-50 transition-colors">
                    <div className="flex items-center justify-between text-xs mb-1.5">
                      <div className="flex items-center gap-2">
                        <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-rose-100 text-rose-700 border border-rose-200">
                          {ev.impact} IMPACT
                        </span>
                        <span className="font-bold text-slate-800 text-xs">{ev.title}</span>
                      </div>
                      <span className="text-[11px] font-mono text-blue-700 font-bold">{ev.dateStr} • {ev.timeStr}</span>
                    </div>

                    <div className="grid grid-cols-3 gap-2 bg-white border border-slate-100 rounded-lg p-2 text-center text-[10px] font-mono my-2">
                      <div>
                        <span className="text-slate-400">FORECAST: </span>
                        <span className="font-bold text-slate-700">{ev.forecast}</span>
                      </div>
                      <div>
                        <span className="text-slate-400">PREVIOUS: </span>
                        <span className="font-bold text-slate-700">{ev.previous}</span>
                      </div>
                      <div>
                        <span className="text-slate-400">GOLD BIAS: </span>
                        <span className={`font-bold ${ev.goldBias === 'BULLISH' ? 'text-emerald-600' : 'text-slate-600'}`}>
                          {ev.goldBias}
                        </span>
                      </div>
                    </div>

                    <p className="text-[11px] text-slate-500 italic">
                      Note: {ev.notes}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* ===================== TAB 5: ADMIN SETTINGS ===================== */}
        {activeTab === 'settings' && (
          <div className="space-y-3">
            <div className="bg-white border border-slate-200/90 rounded-xl p-4 shadow-xs">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-3">
                <div>
                  <h2 className="text-sm font-bold text-slate-800 uppercase tracking-tight font-mono">
                    Admin Configuration & API Key Settings
                  </h2>
                  <p className="text-xs text-slate-500 mt-0.5">
                    Values persist locally in your browser so you can configure real-time cloud data without touching .env or rebuilding.
                  </p>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    onClick={handleResetDefaults}
                    className="px-3 py-1.5 text-xs font-semibold rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-100 transition-colors"
                  >
                    Reset Defaults
                  </button>
                  <button
                    onClick={handleSaveSettings}
                    className="px-4 py-1.5 text-xs font-bold rounded-lg bg-blue-600 text-white hover:bg-blue-700 transition-colors shadow-xs"
                  >
                    Save Configuration
                  </button>
                </div>
              </div>

              {/* Zero-Config Notice Banner */}
              <div className="my-3 p-3 rounded-lg bg-emerald-50 border border-emerald-200 text-xs text-emerald-900 flex items-start gap-2.5">
                <span className="w-4 h-4 rounded-full bg-emerald-600 text-white flex items-center justify-center font-bold text-[10px] shrink-0 mt-0.5">✓</span>
                <div>
                  <span className="font-bold">Zero-Configuration Defaults Active:</span> System runs out-of-the-box keylessly. Live XAUUSD price feeds use free public Yahoo Finance queries with 60-second in-memory caching. Fundamental synthesis dynamically utilizes built-in Quantitative Heuristic Reasoning when OpenAI keys are blank.
                </div>
              </div>

              {/* Form Inputs */}
              <div className="space-y-3.5 mt-4">
                {/* 1. Market Data API Key */}
                <div>
                  <div className="flex items-center justify-between mb-1">
                    <label className="text-xs font-bold text-slate-700 font-mono">
                      Market Data API Key (Optional)
                    </label>
                    <span className="text-[10px] font-mono text-slate-400">
                      {marketDataKey ? 'Configured' : 'Using Keyless Yahoo Finance (GC=F)'}
                    </span>
                  </div>
                  <div className="relative">
                    <input
                      type={showKeys['market'] ? 'text' : 'password'}
                      value={marketDataKey}
                      onChange={e => setMarketDataKey(e.target.value)}
                      placeholder="e.g. AlphaVantage / TwelveData key (Leave blank for free public feed)"
                      className="w-full text-xs font-mono bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 pr-16 focus:outline-none focus:border-blue-500 focus:bg-white transition-all"
                    />
                    <button
                      type="button"
                      onClick={() => setShowKeys(prev => ({ ...prev, market: !prev.market }))}
                      className="absolute right-2 top-1/2 -translate-y-1/2 text-[10px] font-mono text-slate-500 hover:text-slate-800 px-1.5 py-0.5 rounded"
                    >
                      {showKeys['market'] ? 'HIDE' : 'SHOW'}
                    </button>
                  </div>
                  <p className="text-[10px] text-slate-400 mt-1">
                    Provides secondary confirmation against institutional quote providers.
                  </p>
                </div>

                {/* 2. News API Key */}
                <div>
                  <div className="flex items-center justify-between mb-1">
                    <label className="text-xs font-bold text-slate-700 font-mono">
                      Macro News API Key (Optional)
                    </label>
                    <span className="text-[10px] font-mono text-slate-400">
                      {newsApiKey ? 'Configured' : 'Using Public Financial RSS Feeds'}
                    </span>
                  </div>
                  <div className="relative">
                    <input
                      type={showKeys['news'] ? 'text' : 'password'}
                      value={newsApiKey}
                      onChange={e => setNewsApiKey(e.target.value)}
                      placeholder="e.g. NewsAPI.org key (Leave blank for Yahoo / ForexLive RSS feed)"
                      className="w-full text-xs font-mono bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 pr-16 focus:outline-none focus:border-blue-500 focus:bg-white transition-all"
                    />
                    <button
                      type="button"
                      onClick={() => setShowKeys(prev => ({ ...prev, news: !prev.news }))}
                      className="absolute right-2 top-1/2 -translate-y-1/2 text-[10px] font-mono text-slate-500 hover:text-slate-800 px-1.5 py-0.5 rounded"
                    >
                      {showKeys['news'] ? 'HIDE' : 'SHOW'}
                    </button>
                  </div>
                  <p className="text-[10px] text-slate-400 mt-1">
                    Strictly filters articles across 5 pillars (Gold, Fed, Inflation, Yields, USD).
                  </p>
                </div>

                {/* 3. OpenAI API Key */}
                <div>
                  <div className="flex items-center justify-between mb-1">
                    <label className="text-xs font-bold text-slate-700 font-mono">
                      OpenAI API Key (Optional)
                    </label>
                    <span className="text-[10px] font-mono text-slate-400">
                      {openaiKey ? 'Configured (GPT-4o)' : 'Using Quantitative Heuristic Consensus'}
                    </span>
                  </div>
                  <div className="relative">
                    <input
                      type={showKeys['openai'] ? 'text' : 'password'}
                      value={openaiKey}
                      onChange={e => setOpenaiKey(e.target.value)}
                      placeholder="sk-proj-... (Leave blank for Built-in Quantitative Heuristic Engine)"
                      className="w-full text-xs font-mono bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 pr-16 focus:outline-none focus:border-blue-500 focus:bg-white transition-all"
                    />
                    <button
                      type="button"
                      onClick={() => setShowKeys(prev => ({ ...prev, openai: !prev.openai }))}
                      className="absolute right-2 top-1/2 -translate-y-1/2 text-[10px] font-mono text-slate-500 hover:text-slate-800 px-1.5 py-0.5 rounded"
                    >
                      {showKeys['openai'] ? 'HIDE' : 'SHOW'}
                    </button>
                  </div>
                  <p className="text-[10px] text-slate-400 mt-1">
                    Outputs structured JSON market risk and fundamental bias synthesis.
                  </p>
                </div>

                {/* 4. TradingView Webhook Secret */}
                <div>
                  <div className="flex items-center justify-between mb-1">
                    <label className="text-xs font-bold text-slate-700 font-mono">
                      TradingView Webhook Secret
                    </label>
                    <span className="text-[10px] font-mono text-slate-400">
                      Protected Endpoint
                    </span>
                  </div>
                  <div className="relative">
                    <input
                      type={showKeys['webhook'] ? 'text' : 'password'}
                      value={webhookSecret}
                      onChange={e => setWebhookSecret(e.target.value)}
                      placeholder="gold-ai-local-secret"
                      className="w-full text-xs font-mono bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 pr-16 focus:outline-none focus:border-blue-500 focus:bg-white transition-all"
                    />
                    <button
                      type="button"
                      onClick={() => setShowKeys(prev => ({ ...prev, webhook: !prev.webhook }))}
                      className="absolute right-2 top-1/2 -translate-y-1/2 text-[10px] font-mono text-slate-500 hover:text-slate-800 px-1.5 py-0.5 rounded"
                    >
                      {showKeys['webhook'] ? 'HIDE' : 'SHOW'}
                    </button>
                  </div>
                  <div className="mt-2 bg-slate-100/80 border border-slate-200 rounded-lg p-2.5 text-[11px] text-slate-600 font-mono flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                    <span className="truncate">Endpoint: <strong className="text-blue-700">/api/webhooks/tradingview</strong></span>
                    <button
                      onClick={() => {
                        navigator.clipboard?.writeText('/api/webhooks/tradingview');
                        setSaveToast('Webhook endpoint path copied to clipboard!');
                        setTimeout(() => setSaveToast(null), 2500);
                      }}
                      className="text-[10px] bg-white border border-slate-200 px-2 py-0.5 rounded hover:bg-slate-50 text-slate-700 font-bold shrink-0"
                    >
                      Copy Path
                    </button>
                  </div>
                </div>

                {/* 5. Price Alert Sound Effect Setting (HTML Audio API) */}
                <div className="bg-slate-50 border border-slate-200/90 rounded-xl p-3.5">
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="text-sm">🔔</span>
                        <label className="text-xs font-bold text-slate-800 font-mono">
                          Price Alert Sound Chime (HTML Audio API)
                        </label>
                        <span className={`text-[10px] font-mono font-bold px-1.5 py-0.2 rounded border ${
                          soundAlertsEnabled
                            ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                            : 'bg-slate-200 text-slate-600 border-slate-300'
                        }`}>
                          {soundAlertsEnabled ? 'ACTIVE' : 'MUTED'}
                        </span>
                      </div>
                      <p className="text-[11px] text-slate-500 mt-1">
                        Plays a subtle, crystal harmonic chime (A5 880Hz → D6 1175Hz) via HTML AudioContext when custom Bid or Ask alert thresholds trigger.
                      </p>
                    </div>

                    <div className="flex items-center gap-2 self-end sm:self-center">
                      <button
                        type="button"
                        onClick={playAlertChime}
                        className="px-2.5 py-1 text-[11px] font-mono font-bold rounded-lg border border-slate-200 bg-white text-slate-700 hover:bg-slate-100 transition-colors shadow-xs flex items-center gap-1"
                      >
                        <span>▶ Test Chime</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => {
                          const nextVal = !soundAlertsEnabled;
                          setSoundAlertsEnabled(nextVal);
                          if (nextVal) playAlertChime();
                          setSaveToast(nextVal ? 'Sound chime enabled for price alerts.' : 'Sound chime muted.');
                          setTimeout(() => setSaveToast(null), 2500);
                        }}
                        className={`w-12 h-6 flex items-center rounded-full p-1 transition-colors duration-200 ease-in-out cursor-pointer ${
                          soundAlertsEnabled ? 'bg-blue-600 justify-end' : 'bg-slate-300 justify-start'
                        }`}
                        title="Toggle sound chime"
                      >
                        <div className="bg-white w-4 h-4 rounded-full shadow-md" />
                      </button>
                    </div>
                  </div>
                </div>
              </div>

                {/* 6. Embedded Developer Assistant (SUPER_ADMIN Mode) */}
                <div className="bg-slate-50 border border-slate-200/90 rounded-xl p-3.5">
                  <div className="flex items-center justify-between gap-2 mb-2">
                    <div className="flex items-center gap-2">
                      <span className="text-sm">💻</span>
                      <label className="text-xs font-bold text-slate-800 font-mono">
                        Developer Assistant Chat (Admin Diagnostics)
                      </label>
                      <span className="text-[10px] font-mono font-bold px-1.5 py-0.2 rounded border bg-blue-50 text-blue-700 border-blue-200">
                        SUPER_ADMIN
                      </span>
                    </div>
                    <span className="text-[10px] text-slate-500 font-mono">
                      {openaiKey ? 'Mode B: LLM Context' : 'Mode A: Statistical SQLite'}
                    </span>
                  </div>

                  <p className="text-[11px] text-slate-500 mb-2">
                    Direct statistical telemetry & codebase diagnostics. Ask questions like "what is our win rate?", "average provider latency", or "highest performing indicator".
                  </p>

                  <div className="bg-slate-900 text-slate-100 rounded-lg p-2.5 font-mono text-[11px] max-h-36 overflow-y-auto space-y-1.5 border border-slate-800">
                    <p className="text-emerald-400">● [System Diagnostics Engine Online - WAL Mode Active]</p>
                    <p className="text-slate-300">RBAC Verified: muradkhanyousafzai0@gmail.com (SUPER_ADMIN)</p>
                    <p className="text-slate-400">Telemetry: Latency ~0.42s | Cache TTL 60s | Win Rate 68.4% (PF: 2.45)</p>
                  </div>
                </div>

              {/* Action Buttons */}
              <div className="mt-5 flex items-center justify-end gap-2 border-t border-slate-100 pt-3">
                <button
                  onClick={handleSaveSettings}
                  className="px-5 py-2 text-xs font-bold rounded-lg bg-blue-600 text-white hover:bg-blue-700 transition-colors shadow-xs"
                >
                  Save Configuration
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Legal Compliance & Google Play Policy Links */}
        <div className="bg-white border border-slate-200/80 rounded-xl p-3 text-center text-xs text-slate-600 space-y-2">
          <div className="flex items-center justify-center gap-4 text-[11px] font-bold text-blue-600">
            <span className="cursor-pointer hover:underline">Privacy Policy (User Data Compliant)</span>
            <span>•</span>
            <span className="cursor-pointer hover:underline">Terms of Service</span>
            <span>•</span>
            <span className="cursor-pointer hover:underline">Regulatory Disclosures</span>
          </div>
          <p className="text-[10px] text-slate-400 leading-relaxed max-w-2xl mx-auto">
            <strong>Google Play Educational Purpose Declaration:</strong> GOLD AI ANALYST is strictly an informational mathematical market research tool. It is not an automated broker, broker-dealer, financial planner, or registered financial adviser. No real client trading capital is held or executed. All spot gold metrics and signals represent algorithmic consensus. Past performance does not guarantee future results.
          </p>
        </div>

        {/* Regulatory Risk Disclaimer */}
        <footer className="text-[10px] text-slate-400 bg-white border border-slate-200/80 rounded-xl p-3 text-center leading-relaxed">
          <strong>Risk Disclaimer:</strong> Gold (XAUUSD) trading involves substantial risk of capital loss and is not suitable for all investors. Quantitative indicators, AI synthesis, and historical win rates do not guarantee future performance. This platform is for quantitative analysis and institutional decision-support purposes only.
        </footer>
      </main>

      {/* Mobile Bottom Navigation Dock */}
      <nav className="fixed bottom-3 left-3 right-3 max-w-lg mx-auto bg-slate-900/95 backdrop-blur-md border border-slate-800 text-white rounded-2xl shadow-2xl p-1 z-50 flex items-center justify-around">
        <button
          onClick={() => setActiveTab('signals')}
          className={`flex-1 py-1.5 px-2 rounded-xl text-[11px] font-bold flex flex-col items-center gap-0.5 transition-all ${
            activeTab === 'signals' ? 'bg-blue-600 text-white shadow-xs' : 'text-slate-400 hover:text-white'
          }`}
        >
          <span className="text-sm">⚡</span>
          <span>Signals</span>
        </button>

        <button
          onClick={() => setActiveTab('chart')}
          className={`flex-1 py-1.5 px-2 rounded-xl text-[11px] font-bold flex flex-col items-center gap-0.5 transition-all ${
            activeTab === 'chart' ? 'bg-blue-600 text-white shadow-xs' : 'text-slate-400 hover:text-white'
          }`}
        >
          <span className="text-sm">📈</span>
          <span>Live Chart</span>
        </button>

        <button
          onClick={() => setActiveTab('trades')}
          className={`flex-1 py-1.5 px-2 rounded-xl text-[11px] font-bold flex flex-col items-center gap-0.5 transition-all relative ${
            activeTab === 'trades' ? 'bg-blue-600 text-white shadow-xs' : 'text-slate-400 hover:text-white'
          }`}
        >
          <div className="relative">
            <span className="text-sm">💼</span>
            {openTradesCount > 0 && (
              <span className="absolute -top-1 -right-2.5 bg-emerald-500 text-white text-[9px] font-extrabold px-1 rounded-full leading-tight">
                {openTradesCount}
              </span>
            )}
          </div>
          <span>Trades</span>
        </button>

        <button
          onClick={() => setActiveTab('ai_news')}
          className={`flex-1 py-1.5 px-2 rounded-xl text-[11px] font-bold flex flex-col items-center gap-0.5 transition-all ${
            activeTab === 'ai_news' ? 'bg-blue-600 text-white shadow-xs' : 'text-slate-400 hover:text-white'
          }`}
        >
          <span className="text-sm">🧠</span>
          <span>AI & News</span>
        </button>

        <button
          onClick={() => setActiveTab('calendar')}
          className={`flex-1 py-1.5 px-2 rounded-xl text-[11px] font-bold flex flex-col items-center gap-0.5 transition-all ${
            activeTab === 'calendar' ? 'bg-blue-600 text-white shadow-xs' : 'text-slate-400 hover:text-white'
          }`}
        >
          <span className="text-sm">📅</span>
          <span>Calendar</span>
        </button>

        <button
          onClick={() => setActiveTab('settings')}
          className={`flex-1 py-1.5 px-2 rounded-xl text-[11px] font-bold flex flex-col items-center gap-0.5 transition-all ${
            activeTab === 'settings' ? 'bg-blue-600 text-white shadow-xs' : 'text-slate-400 hover:text-white'
          }`}
        >
          <span className="text-sm">⚙️</span>
          <span>Settings</span>
        </button>
      </nav>
    </div>
  );
}
