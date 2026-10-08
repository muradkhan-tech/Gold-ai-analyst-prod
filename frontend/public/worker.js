/**
 * Web Worker for Extreme Speed Quantitative Indicator Math & Parsing.
 * Offloads compute-heavy mathematical operations from the main React thread
 * to maintain a locked 60 FPS UI.
 */

self.onmessage = function (e) {
  const { type, payload } = e.data;

  if (type === 'CALCULATE_INDICATORS') {
    const { candles } = payload;
    const closes = candles.map(c => c.close);
    const highs = candles.map(c => c.high);
    const lows = candles.map(c => c.low);

    const rsi14 = computeRSI(closes, 14);
    const ema20 = computeEMA(closes, 20);
    const ema50 = computeEMA(closes, 50);
    const ema200 = computeEMA(closes, 200);
    const bb = computeBollingerBands(closes, 20, 2);
    const macd = computeMACD(closes, 12, 26, 9);
    const atr14 = computeATR(highs, lows, closes, 14);

    self.postMessage({
      type: 'INDICATORS_COMPUTED',
      payload: {
        rsi14: rsi14[rsi14.length - 1] || 50.0,
        ema20: ema20[ema20.length - 1] || closes[closes.length - 1],
        ema50: ema50[ema50.length - 1] || closes[closes.length - 1],
        ema200: ema200[ema200.length - 1] || closes[closes.length - 1],
        bbUpper: bb.upper[bb.upper.length - 1] || 0,
        bbLower: bb.lower[bb.lower.length - 1] || 0,
        bbMiddle: bb.middle[bb.middle.length - 1] || 0,
        macdLine: macd.line[macd.line.length - 1] || 0,
        macdHist: macd.hist[macd.hist.length - 1] || 0,
        atr14: atr14[atr14.length - 1] || 8.5
      }
    });
  }
};

function computeEMA(data, period) {
  const k = 2 / (period + 1);
  const ema = new Array(data.length);
  ema[0] = data[0];
  for (let i = 1; i < data.length; i++) {
    ema[i] = data[i] * k + ema[i - 1] * (1 - k);
  }
  return ema;
}

function computeRSI(data, period = 14) {
  if (data.length < period + 1) return new Array(data.length).fill(50);
  const rsi = new Array(data.length).fill(50);
  let gains = 0;
  let losses = 0;

  for (let i = 1; i <= period; i++) {
    const diff = data[i] - data[i - 1];
    if (diff >= 0) gains += diff;
    else losses -= diff;
  }

  let avgGain = gains / period;
  let avgLoss = losses / period;
  rsi[period] = avgLoss === 0 ? 100 : 100 - (100 / (1 + avgGain / avgLoss));

  for (let i = period + 1; i < data.length; i++) {
    const diff = data[i] - data[i - 1];
    const gain = diff > 0 ? diff : 0;
    const loss = diff < 0 ? -diff : 0;

    avgGain = (avgGain * (period - 1) + gain) / period;
    avgLoss = (avgLoss * (period - 1) + loss) / period;

    if (avgLoss === 0) {
      rsi[i] = 100;
    } else {
      const rs = avgGain / avgLoss;
      rsi[i] = 100 - (100 / (1 + rs));
    }
  }
  return rsi;
}

function computeBollingerBands(data, period = 20, multiplier = 2) {
  const upper = new Array(data.length);
  const middle = new Array(data.length);
  const lower = new Array(data.length);

  for (let i = 0; i < data.length; i++) {
    if (i < period - 1) {
      middle[i] = data[i];
      upper[i] = data[i];
      lower[i] = data[i];
      continue;
    }
    let sum = 0;
    for (let j = 0; j < period; j++) {
      sum += data[i - j];
    }
    const sma = sum / period;
    middle[i] = sma;

    let variance = 0;
    for (let j = 0; j < period; j++) {
      variance += Math.pow(data[i - j] - sma, 2);
    }
    const std = Math.sqrt(variance / period);
    upper[i] = sma + std * multiplier;
    lower[i] = sma - std * multiplier;
  }
  return { upper, middle, lower };
}

function computeMACD(data, fast = 12, slow = 26, signal = 9) {
  const emaFast = computeEMA(data, fast);
  const emaSlow = computeEMA(data, slow);
  const macdLine = emaFast.map((f, i) => f - emaSlow[i]);
  const signalLine = computeEMA(macdLine, signal);
  const hist = macdLine.map((m, i) => m - signalLine[i]);
  return { line: macdLine, signal: signalLine, hist };
}

function computeATR(highs, lows, closes, period = 14) {
  const tr = new Array(closes.length);
  tr[0] = highs[0] - lows[0];
  for (let i = 1; i < closes.length; i++) {
    const hl = highs[i] - lows[i];
    const hc = Math.abs(highs[i] - closes[i - 1]);
    const lc = Math.abs(lows[i] - closes[i - 1]);
    tr[i] = Math.max(hl, hc, lc);
  }
  return computeEMA(tr, period);
}
