import React from 'react';

export interface TimeframeSignal {
  timeframe: string;
  name: string;
  direction: 'STRONG BUY' | 'BUY' | 'WEAK BUY' | 'WAIT' | 'WEAK SELL' | 'SELL' | 'STRONG SELL';
  entryPrice: number;
  entryZoneLow: number;
  entryZoneHigh: number;
  takeProfit1: number;
  takeProfit2: number;
  stopLoss: number;
  riskReward: number;
  historicalWinRate: number; // e.g. 74.2%
  confidenceScore: number;
  status: string;
  summary: string;
}

interface SignalPanelProps {
  currentPrice: number;
  signals: TimeframeSignal[];
  onRefresh?: () => void;
  isScanning?: boolean;
}

export function SignalPanel({
  currentPrice,
  signals,
  onRefresh,
  isScanning = false
}: SignalPanelProps) {
  const getBadgeStyle = (direction: string) => {
    switch (direction) {
      case 'STRONG BUY':
        return 'bg-emerald-500 text-slate-950 border-emerald-400 font-black shadow-lg shadow-emerald-500/20';
      case 'BUY':
        return 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40 font-bold';
      case 'WEAK BUY':
        return 'bg-teal-500/20 text-teal-300 border-teal-500/40 font-medium';
      case 'STRONG SELL':
        return 'bg-rose-600 text-white border-rose-500 font-black shadow-lg shadow-rose-600/20';
      case 'SELL':
        return 'bg-rose-500/20 text-rose-400 border-rose-500/40 font-bold';
      case 'WEAK SELL':
        return 'bg-orange-500/20 text-orange-300 border-orange-500/40 font-medium';
      default:
        return 'bg-amber-500/20 text-amber-400 border-amber-500/40 font-bold';
    }
  };

  const getBorderAccent = (direction: string) => {
    if (direction.includes('BUY')) return 'border-emerald-500/30 hover:border-emerald-500/60';
    if (direction.includes('SELL')) return 'border-rose-500/30 hover:border-rose-500/60';
    return 'border-slate-800 hover:border-amber-500/40';
  };

  return (
    <div className="space-y-6">
      {/* Header Bar */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 bg-slate-900/60 p-4 rounded-xl border border-slate-800 backdrop-blur">
        <div>
          <h2 className="text-lg font-bold text-white flex items-center gap-2">
            MULTI-TIMEFRAME QUANTITATIVE SIGNALS
            <span className="text-xs bg-amber-500/20 text-amber-400 px-2.5 py-0.5 rounded border border-amber-500/40 font-mono">
              6 TIMEFRAMES (1M - 1H)
            </span>
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            Institutional algorithmic signals with exact entry zones, risk-managed stops, take profits, and historical win rates.
          </p>
        </div>

        {onRefresh && (
          <button
            onClick={onRefresh}
            disabled={isScanning}
            className="px-4 py-2 bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold text-xs rounded-lg shadow-md transition-colors flex items-center gap-2 self-stretch sm:self-auto justify-center"
          >
            {isScanning ? (
              <>
                <span className="w-3.5 h-3.5 border-2 border-slate-950 border-t-transparent rounded-full animate-spin"></span>
                Evaluating Confluence...
              </>
            ) : (
              <>
                <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                </svg>
                Rescan Timeframes
              </>
            )}
          </button>
        )}
      </div>

      {/* 6 Timeframe Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {signals.map((sig) => {
          const isBuy = sig.direction.includes('BUY');
          const isSell = sig.direction.includes('SELL');

          return (
            <div
              key={sig.timeframe}
              className={`bg-slate-900/80 rounded-xl p-5 border transition-all duration-200 shadow-xl flex flex-col justify-between ${getBorderAccent(
                sig.direction
              )}`}
            >
              <div>
                {/* Card Top: TF label & Direction badge */}
                <div className="flex items-center justify-between pb-3 border-b border-slate-800">
                  <div className="flex items-center gap-2.5">
                    <span className="w-8 h-8 rounded-lg bg-slate-800 border border-slate-700 flex items-center justify-center font-mono font-bold text-amber-400 text-sm">
                      {sig.timeframe}
                    </span>
                    <div>
                      <div className="text-xs font-semibold text-slate-300">{sig.name}</div>
                      <div className="text-[10px] text-slate-500 font-mono">STATUS: {sig.status}</div>
                    </div>
                  </div>

                  <span
                    className={`px-2.5 py-1 text-xs rounded-md border font-mono tracking-wide ${getBadgeStyle(
                      sig.direction
                    )}`}
                  >
                    {sig.direction}
                  </span>
                </div>

                {/* Entry Zone & Trigger Price */}
                <div className="my-4 bg-slate-950/70 p-3 rounded-lg border border-slate-800/80 space-y-1.5">
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-slate-400 font-mono text-[11px]">EXACT ENTRY ZONE</span>
                    <span className="text-amber-400 font-mono font-bold">
                      ${sig.entryZoneLow.toFixed(2)} - ${sig.entryZoneHigh.toFixed(2)}
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-slate-500 font-mono text-[11px]">TRIGGER PRICE</span>
                    <span className="text-white font-mono font-bold">${sig.entryPrice.toFixed(2)}</span>
                  </div>
                </div>

                {/* TP & SL Levels Grid */}
                <div className="grid grid-cols-3 gap-2 text-center my-3">
                  <div className="bg-slate-950/50 p-2 rounded border border-rose-950/40">
                    <div className="text-[10px] text-rose-400 font-mono font-semibold">STOP LOSS</div>
                    <div className="text-xs font-mono font-bold text-rose-400 mt-0.5">
                      ${sig.stopLoss.toFixed(2)}
                    </div>
                  </div>

                  <div className="bg-slate-950/50 p-2 rounded border border-emerald-950/40">
                    <div className="text-[10px] text-emerald-400 font-mono font-semibold">TAKE PROFIT 1</div>
                    <div className="text-xs font-mono font-bold text-emerald-400 mt-0.5">
                      ${sig.takeProfit1.toFixed(2)}
                    </div>
                  </div>

                  <div className="bg-slate-950/50 p-2 rounded border border-emerald-950/40">
                    <div className="text-[10px] text-emerald-400 font-mono font-semibold">TAKE PROFIT 2</div>
                    <div className="text-xs font-mono font-bold text-emerald-300 mt-0.5">
                      ${sig.takeProfit2.toFixed(2)}
                    </div>
                  </div>
                </div>

                {/* R:R and Win Rate stats */}
                <div className="grid grid-cols-2 gap-2 my-3 text-xs bg-slate-800/30 p-2.5 rounded-lg border border-slate-800">
                  <div>
                    <div className="text-[10px] text-slate-400 font-mono">RISK : REWARD</div>
                    <div className="font-mono font-bold text-amber-400 text-sm">1 : {sig.riskReward.toFixed(1)}</div>
                  </div>
                  <div>
                    <div className="text-[10px] text-slate-400 font-mono">HISTORICAL WIN RATE</div>
                    <div className="font-mono font-bold text-emerald-400 text-sm flex items-center gap-1">
                      {sig.historicalWinRate.toFixed(1)}%
                      <span className="text-[10px] text-slate-500 font-normal">verified</span>
                    </div>
                  </div>
                </div>

                {/* Confluence Rationale */}
                <p className="text-[11px] text-slate-300 italic bg-slate-950/30 p-2 rounded border border-slate-800/50 line-clamp-2">
                  "{sig.summary}"
                </p>
              </div>

              {/* Confidence Progress Bar */}
              <div className="mt-4 pt-3 border-t border-slate-800/80">
                <div className="flex justify-between items-center text-[10px] font-mono text-slate-400 mb-1">
                  <span>CONFIDENCE CONFLUENCE</span>
                  <span className="text-amber-400 font-bold">{sig.confidenceScore.toFixed(0)}%</span>
                </div>
                <div className="w-full bg-slate-800 h-1.5 rounded-full overflow-hidden">
                  <div
                    className={`h-full transition-all duration-500 ${
                      isBuy ? 'bg-emerald-400' : isSell ? 'bg-rose-500' : 'bg-amber-400'
                    }`}
                    style={{ width: `${Math.min(100, sig.confidenceScore)}%` }}
                  />
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default SignalPanel;
