import React, { useEffect, useRef, memo } from 'react';

interface TradingViewWidgetProps {
  symbol?: string;
  theme?: 'dark' | 'light';
  interval?: string;
  height?: number | string;
}

function TradingViewWidgetComponent({
  symbol = 'OANDA:XAUUSD',
  theme = 'dark',
  interval = '15',
  height = '540px'
}: TradingViewWidgetProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    // Clear any previous widget instance
    containerRef.current.innerHTML = '';

    const script = document.createElement('script');
    script.src = 'https://s3.tradingview.com/external-embedding/embed-widget-advanced-chart.js';
    script.type = 'text/javascript';
    script.async = true;
    script.innerHTML = JSON.stringify({
      autosize: true,
      symbol: symbol,
      interval: interval,
      timezone: 'Etc/UTC',
      theme: theme,
      style: '1',
      locale: 'en',
      enable_publishing: false,
      allow_symbol_change: true,
      calendar: false,
      studies: [
        'STD;EMA',
        'STD;RSI',
        'STD;MACD'
      ],
      support_host: 'https://www.tradingview.com',
      hide_top_toolbar: false,
      hide_legend: false,
      save_image: false,
      backgroundColor: 'rgba(2, 6, 23, 1)',
      gridColor: 'rgba(30, 41, 59, 0.4)'
    });

    const widgetContainer = document.createElement('div');
    widgetContainer.className = 'tradingview-widget-container__widget';
    widgetContainer.style.height = '100%';
    widgetContainer.style.width = '100%';

    containerRef.current.appendChild(widgetContainer);
    containerRef.current.appendChild(script);

    return () => {
      if (containerRef.current) {
        containerRef.current.innerHTML = '';
      }
    };
  }, [symbol, theme, interval]);

  return (
    <div className="w-full bg-slate-950 rounded-xl overflow-hidden border border-slate-800 shadow-2xl relative">
      <div 
        ref={containerRef} 
        style={{ height }}
        className="w-full"
      />
    </div>
  );
}

export const TradingViewWidget = memo(TradingViewWidgetComponent);
export default TradingViewWidget;
