import React, { useState, useMemo } from 'react';

export interface LiveTrade {
  id: string;
  timeframe: string;
  direction: 'BUY' | 'SELL';
  lots: number;
  entryPrice: number;
  entryTime: number;
  stopLoss: number;
  takeProfit1: number;
  takeProfit2: number;
  exitPrice?: number;
  exitTime?: number;
  status: 'OPEN' | 'TP1_HIT' | 'TP2_HIT' | 'SL_HIT' | 'MANUAL_CLOSE';
  pnlDollar: number; // floating or realized PnL in USD
  pnlPips: number;
  riskReward: number;
  signalReason: string;
}

interface LiveTradesViewProps {
  currentPrice: number;
  bid: number;
  ask: number;
  trades: LiveTrade[];
  autoExecuteEnabled: boolean;
  onToggleAutoExecute: (enabled: boolean) => void;
  onCloseTrade: (tradeId: string) => void;
  onCloseAllTrades: () => void;
  onManualOrder: (direction: 'BUY' | 'SELL', lots: number, slOffset?: number, tpOffset?: number) => void;
  onClearHistory: () => void;
}

export function LiveTradesView({
  currentPrice,
  bid,
  ask,
  trades,
  autoExecuteEnabled,
  onToggleAutoExecute,
  onCloseTrade,
  onCloseAllTrades,
  onManualOrder,
  onClearHistory
}: LiveTradesViewProps) {
  const [filter, setFilter] = useState<'ALL' | 'OPEN' | 'CLOSED'>('ALL');
  const [lotInput, setLotInput] = useState<number>(1.0);

  // Performance Metrics Calculations
  const metrics = useMemo(() => {
    const closedTrades = trades.filter(t => t.status !== 'OPEN');
    const openTrades = trades.filter(t => t.status === 'OPEN');

    const totalClosed = closedTrades.length;
    const wins = closedTrades.filter(t => t.pnlDollar > 0);
    const losses = closedTrades.filter(t => t.pnlDollar < 0);

    const winRate = totalClosed > 0 ? (wins.length / totalClosed) * 100 : 0;

    const grossProfit = wins.reduce((acc, t) => acc + t.pnlDollar, 0);
    const grossLoss = Math.abs(losses.reduce((acc, t) => acc + t.pnlDollar, 0));
    const netRealizedPnl = grossProfit - grossLoss;

    const profitFactor = grossLoss > 0 ? grossProfit / grossLoss : (grossProfit > 0 ? 9.99 : 1.0);

    const floatingPnl = openTrades.reduce((acc, t) => acc + t.pnlDollar, 0);
    const startingBalance = 10000.0;
    const currentEquity = startingBalance + netRealizedPnl + floatingPnl;

    const totalPips = closedTrades.reduce((acc, t) => acc + t.pnlPips, 0);
    const floatingPips = openTrades.reduce((acc, t) => acc + t.pnlPips, 0);

    const avgWin = wins.length > 0 ? grossProfit / wins.length : 0;
    const avgLoss = losses.length > 0 ? grossLoss / losses.length : 0;

    return {
      startingBalance,
      currentEquity,
      netRealizedPnl,
      floatingPnl,
      totalClosed,
      openCount: openTrades.length,
      winRate,
      winsCount: wins.length,
      lossesCount: losses.length,
      profitFactor,
      totalPips,
      floatingPips,
      avgWin,
      avgLoss
    };
  }, [trades]);

  const filteredTrades = useMemo(() => {
    switch (filter) {
      case 'OPEN':
        return trades.filter(t => t.status === 'OPEN');
      case 'CLOSED':
        return trades.filter(t => t.status !== 'OPEN');
      default:
        return trades;
    }
  }, [trades, filter]);

  const formatCurrency = (val: number) => {
    const sign = val >= 0 ? '+' : '';
    return `${sign}$${val.toFixed(2)}`;
  };

  const formatPips = (val: number) => {
    const sign = val >= 0 ? '+' : '';
    return `${sign}${val.toFixed(1)} pips`;
  };

  const getStatusBadge = (status: LiveTrade['status']) => {
    switch (status) {
      case 'OPEN':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-blue-50 text-blue-700 border border-blue-200">
            <span className="w-1.5 h-1.5 rounded-full bg-blue-600 animate-pulse" />
            OPEN
          </span>
        );
      case 'TP1_HIT':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">
            ✓ TP1 REACHED
          </span>
        );
      case 'TP2_HIT':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-100 text-emerald-800 border border-emerald-300 shadow-xs">
            ★ FULL TP2 TARGET
          </span>
        );
      case 'SL_HIT':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-rose-50 text-rose-700 border border-rose-200">
            ✕ STOP LOSS HIT
          </span>
        );
      case 'MANUAL_CLOSE':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-700 border border-slate-200">
            ⚑ CLOSED AT MARKET
          </span>
        );
    }
  };

  return (
    <div className="space-y-4">
      {/* 1. Header & Live Controller Bar */}
      <div className="bg-white border border-slate-200/90 rounded-xl p-4 shadow-xs">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-100 pb-3">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-lg">💼</span>
              <h2 className="text-sm font-bold text-slate-900 uppercase tracking-tight font-mono">
                Live Simulated Trades & Automated Execution
              </h2>
              <span className="px-1.5 py-0.5 rounded text-[10px] font-mono font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">
                IndexedDB Persistent
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-0.5">
              Automatically evaluates active signal entries, monitors live Bid/Ask ticks, logs TP/SL exits to browser IndexedDB, and tracks quantitative equity.
            </p>
          </div>

          {/* Auto-Execution Switch */}
          <div className="flex items-center gap-2.5 self-start sm:self-auto">
            <span className="text-xs font-mono font-bold text-slate-700">
              Auto-Execute Signals:
            </span>
            <button
              onClick={() => onToggleAutoExecute(!autoExecuteEnabled)}
              className={`w-12 h-6 flex items-center rounded-full p-1 transition-colors duration-200 ease-in-out cursor-pointer ${
                autoExecuteEnabled ? 'bg-emerald-600 justify-end' : 'bg-slate-300 justify-start'
              }`}
              title={autoExecuteEnabled ? 'Auto-execution active' : 'Auto-execution paused'}
            >
              <div className="bg-white w-4 h-4 rounded-full shadow-md" />
            </button>
            <span className={`text-[10px] font-mono font-bold px-1.5 py-0.5 rounded border ${
              autoExecuteEnabled
                ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                : 'bg-slate-100 text-slate-600 border-slate-200'
            }`}>
              {autoExecuteEnabled ? 'ACTIVE' : 'PAUSED'}
            </span>
          </div>
        </div>

        {/* Quick Manual Order Bar */}
        <div className="mt-3 flex flex-wrap items-center justify-between gap-2.5 pt-1">
          <div className="flex items-center gap-2">
            <span className="text-xs font-mono text-slate-500">Lot Size:</span>
            <input
              type="number"
              step="0.1"
              min="0.1"
              max="10.0"
              value={lotInput}
              onChange={e => setLotInput(parseFloat(e.target.value) || 1.0)}
              className="w-16 px-2 py-1 text-xs font-mono font-bold bg-slate-50 border border-slate-200 rounded-lg text-center"
            />
            <button
              onClick={() => onManualOrder('BUY', lotInput)}
              className="px-3 py-1.5 text-xs font-bold font-mono rounded-lg bg-emerald-600 text-white hover:bg-emerald-700 transition-colors shadow-xs flex items-center gap-1"
            >
              <span>▲ Instant BUY</span>
              <span className="text-[10px] opacity-80">(${ask.toFixed(2)})</span>
            </button>
            <button
              onClick={() => onManualOrder('SELL', lotInput)}
              className="px-3 py-1.5 text-xs font-bold font-mono rounded-lg bg-rose-600 text-white hover:bg-rose-700 transition-colors shadow-xs flex items-center gap-1"
            >
              <span>▼ Instant SELL</span>
              <span className="text-[10px] opacity-80">(${bid.toFixed(2)})</span>
            </button>
          </div>

          <div className="flex items-center gap-2">
            {metrics.openCount > 0 && (
              <button
                onClick={onCloseAllTrades}
                className="px-2.5 py-1 text-xs font-bold font-mono rounded-lg border border-rose-200 bg-rose-50 text-rose-700 hover:bg-rose-100 transition-colors"
              >
                Close All ({metrics.openCount})
              </button>
            )}
            <button
              onClick={onClearHistory}
              className="px-2.5 py-1 text-xs font-mono rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 transition-colors"
            >
              Reset History
            </button>
          </div>
        </div>
      </div>

      {/* 2. Quantitative Performance Summary Metric Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
        {/* Equity & Balance */}
        <div className="bg-white border border-slate-200/90 rounded-xl p-3 shadow-xs">
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider font-mono">
            VIRTUAL EQUITY
          </span>
          <div className="mt-1 flex items-baseline justify-between">
            <span className="text-base sm:text-lg font-bold font-mono text-slate-900">
              ${metrics.currentEquity.toFixed(2)}
            </span>
            <span className={`text-[10px] font-bold font-mono ${metrics.floatingPnl >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
              {formatCurrency(metrics.floatingPnl)}
            </span>
          </div>
          <span className="text-[10px] text-slate-400 font-mono">
            Start: ${metrics.startingBalance.toFixed(2)}
          </span>
        </div>

        {/* Realized Net PnL */}
        <div className="bg-white border border-slate-200/90 rounded-xl p-3 shadow-xs">
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider font-mono">
            REALIZED NET P&L
          </span>
          <div className="mt-1 flex items-baseline justify-between">
            <span className={`text-base sm:text-lg font-bold font-mono ${metrics.netRealizedPnl >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
              {formatCurrency(metrics.netRealizedPnl)}
            </span>
            <span className={`text-[10px] font-bold font-mono ${metrics.totalPips >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
              {formatPips(metrics.totalPips)}
            </span>
          </div>
          <span className="text-[10px] text-slate-400 font-mono">
            {metrics.totalClosed} closed positions
          </span>
        </div>

        {/* Win Rate */}
        <div className="bg-white border border-slate-200/90 rounded-xl p-3 shadow-xs">
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider font-mono">
            WIN RATE %
          </span>
          <div className="mt-1 flex items-baseline justify-between">
            <span className="text-base sm:text-lg font-bold font-mono text-blue-700">
              {metrics.winRate.toFixed(1)}%
            </span>
            <span className="text-[10px] font-mono text-slate-500">
              {metrics.winsCount}W / {metrics.lossesCount}L
            </span>
          </div>
          <span className="text-[10px] text-slate-400 font-mono">
            Avg W: +${metrics.avgWin.toFixed(1)} | L: -${metrics.avgLoss.toFixed(1)}
          </span>
        </div>

        {/* Profit Factor */}
        <div className="bg-white border border-slate-200/90 rounded-xl p-3 shadow-xs">
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider font-mono">
            PROFIT FACTOR
          </span>
          <div className="mt-1 flex items-baseline justify-between">
            <span className={`text-base sm:text-lg font-bold font-mono ${metrics.profitFactor >= 1.5 ? 'text-emerald-600' : 'text-slate-800'}`}>
              {metrics.profitFactor.toFixed(2)}
            </span>
            <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-slate-100 text-slate-600">
              {metrics.openCount} Active
            </span>
          </div>
          <span className="text-[10px] text-slate-400 font-mono">
            Target benchmark: &gt; 1.80
          </span>
        </div>
      </div>

      {/* 3. Filter Navigation & Trades List */}
      <div className="bg-white border border-slate-200/90 rounded-xl p-4 shadow-xs space-y-3">
        <div className="flex items-center justify-between border-b border-slate-100 pb-2.5">
          <div className="flex items-center gap-1.5">
            <button
              onClick={() => setFilter('ALL')}
              className={`px-3 py-1 text-xs font-mono font-bold rounded-lg transition-colors ${
                filter === 'ALL'
                  ? 'bg-blue-600 text-white'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              All Trades ({trades.length})
            </button>
            <button
              onClick={() => setFilter('OPEN')}
              className={`px-3 py-1 text-xs font-mono font-bold rounded-lg transition-colors ${
                filter === 'OPEN'
                  ? 'bg-blue-600 text-white'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              Open Positions ({metrics.openCount})
            </button>
            <button
              onClick={() => setFilter('CLOSED')}
              className={`px-3 py-1 text-xs font-mono font-bold rounded-lg transition-colors ${
                filter === 'CLOSED'
                  ? 'bg-blue-600 text-white'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              Closed History ({metrics.totalClosed})
            </button>
          </div>

          <div className="text-[11px] font-mono text-slate-400">
            Spot: <span className="font-bold text-slate-700">${currentPrice.toFixed(2)}</span>
          </div>
        </div>

        {/* Empty State */}
        {filteredTrades.length === 0 ? (
          <div className="py-12 text-center text-slate-400 font-mono space-y-2">
            <span className="text-3xl block">📊</span>
            <p className="text-xs">No {filter.toLowerCase()} trades logged yet.</p>
            <p className="text-[11px] text-slate-400 max-w-sm mx-auto">
              {autoExecuteEnabled
                ? 'Auto-execution is listening for algorithmic signals. A simulated order will log automatically upon next signal confluence.'
                : 'Click "Instant BUY" or "Instant SELL" above to test simulated executions.'}
            </p>
          </div>
        ) : (
          <div className="space-y-2.5">
            {filteredTrades.map(trade => {
              const isOpen = trade.status === 'OPEN';
              const isBuy = trade.direction === 'BUY';
              const currentRefPrice = isBuy ? bid : ask;

              const distanceToTp1 = isBuy ? trade.takeProfit1 - currentRefPrice : currentRefPrice - trade.takeProfit1;
              const distanceToSl = isBuy ? currentRefPrice - trade.stopLoss : trade.stopLoss - currentRefPrice;

              return (
                <div
                  key={trade.id}
                  className={`border rounded-xl p-3 transition-colors ${
                    isOpen
                      ? 'border-blue-200 bg-blue-50/20'
                      : trade.pnlDollar >= 0
                      ? 'border-emerald-200/80 bg-slate-50/50'
                      : 'border-rose-200/80 bg-slate-50/50'
                  }`}
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span className={`px-2 py-0.5 rounded text-[10px] font-mono font-black ${
                        isBuy ? 'bg-emerald-600 text-white' : 'bg-rose-600 text-white'
                      }`}>
                        {trade.direction} {trade.lots} LOT
                      </span>
                      <span className="text-xs font-mono font-bold text-slate-800">
                        XAUUSD ({trade.timeframe})
                      </span>
                      {getStatusBadge(trade.status)}
                    </div>

                    {/* Floating or Realized PnL */}
                    <div className="flex items-center gap-3">
                      <div className="text-right">
                        <div className={`text-xs sm:text-sm font-mono font-black ${
                          trade.pnlDollar >= 0 ? 'text-emerald-600' : 'text-rose-600'
                        }`}>
                          {formatCurrency(trade.pnlDollar)}
                        </div>
                        <div className={`text-[10px] font-mono ${
                          trade.pnlPips >= 0 ? 'text-emerald-600' : 'text-rose-600'
                        }`}>
                          {formatPips(trade.pnlPips)}
                        </div>
                      </div>

                      {isOpen && (
                        <button
                          onClick={() => onCloseTrade(trade.id)}
                          className="px-2.5 py-1 text-[11px] font-mono font-bold rounded-lg border border-slate-200 bg-white hover:bg-slate-100 text-slate-700 shadow-xs transition-colors"
                        >
                          Close @ Market
                        </button>
                      )}
                    </div>
                  </div>

                  {/* Level Details Bar */}
                  <div className="mt-2.5 grid grid-cols-2 sm:grid-cols-4 gap-2 bg-white border border-slate-100 rounded-lg p-2 text-[10px] font-mono">
                    <div>
                      <span className="text-slate-400">ENTRY: </span>
                      <span className="font-bold text-slate-700">${trade.entryPrice.toFixed(2)}</span>
                    </div>
                    <div>
                      <span className="text-slate-400">STOP LOSS: </span>
                      <span className="font-bold text-rose-600">${trade.stopLoss.toFixed(2)}</span>
                      {isOpen && (
                        <span className="text-slate-400 text-[9px]"> ({distanceToSl >= 0 ? `${(distanceToSl * 10).toFixed(0)}p buffer` : 'BREACHED'})</span>
                      )}
                    </div>
                    <div>
                      <span className="text-slate-400">TARGET 1: </span>
                      <span className="font-bold text-emerald-600">${trade.takeProfit1.toFixed(2)}</span>
                      {isOpen && (
                        <span className="text-slate-400 text-[9px]"> ({distanceToTp1 >= 0 ? `${(distanceToTp1 * 10).toFixed(0)}p left` : 'HIT'})</span>
                      )}
                    </div>
                    <div>
                      <span className="text-slate-400">TARGET 2: </span>
                      <span className="font-bold text-emerald-600">${trade.takeProfit2.toFixed(2)}</span>
                    </div>
                  </div>

                  {/* Footer Timestamps & Confluence Notes */}
                  <div className="mt-2 flex items-center justify-between text-[10px] font-mono text-slate-400">
                    <span className="truncate max-w-md">
                      Reason: <strong className="text-slate-600">{trade.signalReason}</strong>
                    </span>
                    <span>
                      {new Date(trade.entryTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                      {trade.exitTime && (
                        <> → {new Date(trade.exitTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}</>
                      )}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
