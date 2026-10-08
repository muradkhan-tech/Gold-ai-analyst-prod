import React, { useState } from 'react';

export interface CalendarEvent {
  id: number;
  title: string;
  country: string;
  impact: 'HIGH' | 'MEDIUM' | 'LOW';
  date: string;
  time: string;
  forecast: string;
  previous: string;
  goldBias: 'BULLISH' | 'BEARISH' | 'NEUTRAL';
}

export interface NewsItem {
  id: number;
  title: string;
  source: string;
  summary: string;
  category: 'GOLD' | 'FED_POLICY' | 'INFLATION' | 'TREASURY_YIELDS' | 'USD_DXY';
  sentiment: 'BULLISH' | 'BEARISH' | 'NEUTRAL';
  time: string;
}

export interface AiMarketAnalysis {
  marketRisk: 'High' | 'Medium' | 'Low';
  fundamentalBias: 'Bullish' | 'Bearish' | 'Neutral';
  confidenceScore: number;
  keyDrivers: string[];
  catalystImpact: string;
  recommendedAction: string;
  detailedSynthesis: string;
  llmProvider?: string;
  modelName?: string;
}

interface AiNewsPanelProps {
  analysis: AiMarketAnalysis | null;
  calendarEvents: CalendarEvent[];
  newsArticles: NewsItem[];
  onTriggerAiReasoning?: () => void;
  isReasoning?: boolean;
}

export function AiNewsPanel({
  analysis,
  calendarEvents,
  newsArticles,
  onTriggerAiReasoning,
  isReasoning = false
}: AiNewsPanelProps) {
  const [activeSubTab, setActiveSubTab] = useState<'reasoning' | 'calendar' | 'news'>('reasoning');
  const [newsFilter, setNewsFilter] = useState<string>('ALL');

  const filteredNews = newsFilter === 'ALL'
    ? newsArticles
    : newsArticles.filter(n => n.category === newsFilter);

  return (
    <div className="space-y-6">
      {/* Sub-Navigation & Action */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 bg-slate-900/60 p-4 rounded-xl border border-slate-800 backdrop-blur">
        <div className="flex items-center space-x-1.5 bg-slate-950 p-1 rounded-lg border border-slate-800">
          <button
            onClick={() => setActiveSubTab('reasoning')}
            className={`px-3.5 py-1.5 text-xs font-bold rounded-md transition-colors ${
              activeSubTab === 'reasoning'
                ? 'bg-amber-500 text-slate-950 shadow'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            AI REASONING SYNTHESIS
          </button>
          <button
            onClick={() => setActiveSubTab('calendar')}
            className={`px-3.5 py-1.5 text-xs font-bold rounded-md transition-colors ${
              activeSubTab === 'calendar'
                ? 'bg-amber-500 text-slate-950 shadow'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            ECONOMIC CALENDAR
          </button>
          <button
            onClick={() => setActiveSubTab('news')}
            className={`px-3.5 py-1.5 text-xs font-bold rounded-md transition-colors ${
              activeSubTab === 'news'
                ? 'bg-amber-500 text-slate-950 shadow'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            FILTERED NEWS ({newsArticles.length})
          </button>
        </div>

        {onTriggerAiReasoning && (
          <button
            onClick={onTriggerAiReasoning}
            disabled={isReasoning}
            className="px-4 py-2 bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-400 hover:to-amber-500 text-slate-950 font-bold text-xs rounded-lg shadow-md transition-all flex items-center gap-2 self-stretch sm:self-auto justify-center"
          >
            {isReasoning ? (
              <>
                <span className="w-3.5 h-3.5 border-2 border-slate-950 border-t-transparent rounded-full animate-spin"></span>
                Synthesizing Macro Factors...
              </>
            ) : (
              <>
                <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M13 10V3L4 14h7v7l9-11h-7z" />
                </svg>
                Execute AI Macro Analysis
              </>
            )}
          </button>
        )}
      </div>

      {/* 1. AI REASONING SYNTHESIS VIEW */}
      {activeSubTab === 'reasoning' && (
        <div className="space-y-6">
          {analysis ? (
            <div className="bg-slate-900/80 border border-amber-500/30 rounded-xl p-6 shadow-2xl space-y-6">
              {/* Header Badge Row */}
              <div className="flex flex-wrap items-center justify-between gap-4 pb-4 border-b border-slate-800">
                <div>
                  <h3 className="text-lg font-bold text-white flex items-center gap-2">
                    AI INSTITUTIONAL MACRO REASONING
                    <span className="text-xs bg-amber-500/20 text-amber-400 px-2.5 py-0.5 rounded border border-amber-500/40 font-mono">
                      {analysis.llmProvider || 'OpenAI'} · {analysis.modelName || 'gpt-4o'}
                    </span>
                  </h3>
                  <p className="text-xs text-slate-400 mt-1">
                    Structured multi-factor evaluation combining technical alignment, Fed rate-cut probabilities, real yields, and central bank bullion demand.
                  </p>
                </div>

                <div className="flex items-center gap-2 font-mono text-xs">
                  <span className="text-slate-400">SYNTHESIS CONFIDENCE:</span>
                  <span className="text-amber-400 font-bold text-sm bg-amber-500/10 px-2 py-0.5 rounded border border-amber-500/30">
                    {analysis.confidenceScore}%
                  </span>
                </div>
              </div>

              {/* Metric Pillars */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="bg-slate-950/70 p-4 rounded-xl border border-slate-800">
                  <div className="text-[10px] font-mono font-bold text-slate-400">MARKET RISK ASSESSMENT</div>
                  <div className="text-xl font-bold font-mono text-amber-400 mt-1">
                    {analysis.marketRisk.toUpperCase()}
                  </div>
                  <div className="text-xs text-slate-500 mt-0.5">
                    {analysis.marketRisk === 'High' ? 'Tier-1 volatility event approaching' : 'Normal orderly volatility environment'}
                  </div>
                </div>

                <div className="bg-slate-950/70 p-4 rounded-xl border border-slate-800">
                  <div className="text-[10px] font-mono font-bold text-slate-400">FUNDAMENTAL BIAS</div>
                  <div className={`text-xl font-bold font-mono mt-1 ${
                    analysis.fundamentalBias === 'Bullish' ? 'text-emerald-400' : analysis.fundamentalBias === 'Bearish' ? 'text-rose-400' : 'text-amber-400'
                  }`}>
                    {analysis.fundamentalBias.toUpperCase()}
                  </div>
                  <div className="text-xs text-slate-500 mt-0.5">
                    Macro catalyst intermarket trajectory
                  </div>
                </div>

                <div className="bg-slate-950/70 p-4 rounded-xl border border-slate-800">
                  <div className="text-[10px] font-mono font-bold text-slate-400">CONFIDENCE SCORE</div>
                  <div className="text-xl font-bold font-mono text-emerald-400 mt-1">
                    {analysis.confidenceScore.toFixed(0)}%
                  </div>
                  <div className="text-xs text-slate-500 mt-0.5">
                    High statistical confluence alignment
                  </div>
                </div>
              </div>

              {/* Key Drivers */}
              <div className="bg-slate-950/50 p-4 rounded-xl border border-slate-800 space-y-2">
                <div className="text-xs font-mono font-bold text-amber-400">PRIMARY MACROECONOMIC DRIVERS</div>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-1">
                  {analysis.keyDrivers.map((driver, idx) => (
                    <div key={idx} className="bg-slate-900/60 p-3 rounded-lg border border-slate-800/80 text-xs text-slate-300 flex items-start gap-2">
                      <span className="text-amber-400 font-bold">0{idx + 1}.</span>
                      <span>{driver}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Catalyst Impact & Tactical Guidance */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="bg-amber-500/10 border border-amber-500/25 p-4 rounded-xl">
                  <div className="text-xs font-mono font-bold text-amber-400 mb-1">NEAR-TERM CATALYST IMPACT</div>
                  <p className="text-xs text-amber-200/90 leading-relaxed">
                    {analysis.catalystImpact}
                  </p>
                </div>

                <div className="bg-emerald-500/10 border border-emerald-500/25 p-4 rounded-xl">
                  <div className="text-xs font-mono font-bold text-emerald-400 mb-1">QUANT TACTICAL GUIDANCE</div>
                  <p className="text-xs text-emerald-200/90 leading-relaxed">
                    {analysis.recommendedAction}
                  </p>
                </div>
              </div>

              {/* Detailed Synthesis */}
              <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-2">
                <div className="text-xs font-mono font-bold text-slate-400">EXHAUSTIVE INTERMARKET SYNTHESIS</div>
                <p className="text-xs text-slate-300 leading-relaxed italic">
                  "{analysis.detailedSynthesis}"
                </p>
              </div>
            </div>
          ) : (
            <div className="bg-slate-900/60 p-12 text-center rounded-xl border border-slate-800 space-y-3">
              <div className="text-amber-400 text-3xl">✦</div>
              <h3 className="text-base font-bold text-white">No AI Synthesis Generated Yet</h3>
              <p className="text-xs text-slate-400 max-w-md mx-auto">
                Click "Execute AI Macro Analysis" above to evaluate real-time technical indicators, news sentiment, and calendar releases via OpenAI / Gemini.
              </p>
            </div>
          )}
        </div>
      )}

      {/* 2. ECONOMIC CALENDAR VIEW */}
      {activeSubTab === 'calendar' && (
        <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-5 shadow-2xl space-y-4">
          <div className="flex items-center justify-between pb-3 border-b border-slate-800">
            <div>
              <h3 className="text-base font-bold text-white">UPCOMING MACROECONOMIC CALENDAR</h3>
              <p className="text-xs text-slate-400 mt-0.5">High-volatility releases influencing US Dollar, Real Yields, and Spot Gold</p>
            </div>
            <span className="text-xs font-mono text-amber-400 bg-amber-500/10 px-2.5 py-1 rounded border border-amber-500/20">
              UTC SCHEDULE
            </span>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-950/80 font-mono text-slate-400 border-b border-slate-800">
                <tr>
                  <th className="py-2.5 px-3">DATE / TIME</th>
                  <th className="py-2.5 px-3">EVENT</th>
                  <th className="py-2.5 px-3 text-center">IMPACT</th>
                  <th className="py-2.5 px-3 text-right">FORECAST</th>
                  <th className="py-2.5 px-3 text-right">PREVIOUS</th>
                  <th className="py-2.5 px-3 text-center">GOLD BIAS</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {calendarEvents.map((ev) => (
                  <tr key={ev.id} className="hover:bg-slate-800/30 transition-colors">
                    <td className="py-3 px-3 font-mono text-slate-400 whitespace-nowrap">
                      {ev.date} · {ev.time}
                    </td>
                    <td className="py-3 px-3 font-medium text-white">
                      <div className="flex items-center gap-1.5">
                        <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-slate-800 text-slate-300">
                          {ev.country}
                        </span>
                        <span>{ev.title}</span>
                      </div>
                    </td>
                    <td className="py-3 px-3 text-center">
                      <span className={`text-[10px] font-mono font-bold px-2 py-0.5 rounded ${
                        ev.impact === 'HIGH' ? 'bg-rose-500/20 text-rose-400 border border-rose-500/30' : 'bg-amber-500/20 text-amber-400'
                      }`}>
                        {ev.impact}
                      </span>
                    </td>
                    <td className="py-3 px-3 text-right font-mono text-slate-300">{ev.forecast}</td>
                    <td className="py-3 px-3 text-right font-mono text-slate-400">{ev.previous}</td>
                    <td className="py-3 px-3 text-center">
                      <span className={`text-[10px] font-mono font-bold px-2 py-0.5 rounded ${
                        ev.goldBias === 'BULLISH'
                          ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                          : ev.goldBias === 'BEARISH'
                          ? 'bg-rose-500/20 text-rose-400 border border-rose-500/30'
                          : 'bg-slate-800 text-slate-300'
                      }`}>
                        {ev.goldBias}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* 3. FILTERED NEWS VIEW */}
      {activeSubTab === 'news' && (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3 bg-slate-900/60 p-4 rounded-xl border border-slate-800">
            <div>
              <h3 className="text-base font-bold text-white">STRICTLY FILTERED FINANCIAL NEWS</h3>
              <p className="text-xs text-slate-400">Strict keyword isolation: Gold, Fed, Inflation, Yields, USD</p>
            </div>

            <div className="flex flex-wrap gap-1">
              {(['ALL', 'GOLD', 'FED_POLICY', 'INFLATION', 'TREASURY_YIELDS', 'USD_DXY'] as const).map(cat => (
                <button
                  key={cat}
                  onClick={() => setNewsFilter(cat)}
                  className={`text-[10px] font-mono font-bold px-2.5 py-1 rounded transition-colors ${
                    newsFilter === cat
                      ? 'bg-amber-500 text-slate-950'
                      : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
                  }`}
                >
                  {cat.replace('_', ' ')}
                </button>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {filteredNews.map((item) => (
              <div key={item.id} className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 space-y-2 hover:border-slate-700 transition-all shadow-lg">
                <div className="flex items-center justify-between text-xs">
                  <div className="flex items-center gap-1.5">
                    <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded bg-amber-500/20 text-amber-400">
                      {item.category.replace('_', ' ')}
                    </span>
                    <span className={`text-[10px] font-mono font-bold px-2 py-0.5 rounded ${
                      item.sentiment === 'BULLISH' ? 'bg-emerald-500/20 text-emerald-400' : 'bg-slate-800 text-slate-300'
                    }`}>
                      {item.sentiment}
                    </span>
                  </div>
                  <span className="text-[10px] font-mono text-slate-400">{item.source} · {item.time}</span>
                </div>

                <h4 className="text-sm font-bold text-white hover:text-amber-400 transition-colors">
                  {item.title}
                </h4>

                <p className="text-xs text-slate-300 leading-relaxed line-clamp-3">
                  {item.summary}
                </p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export default AiNewsPanel;
