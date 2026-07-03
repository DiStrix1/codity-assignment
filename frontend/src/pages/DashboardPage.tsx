import { useState, useEffect, useRef } from 'react';
import { metricsApi, queueApi } from '../api/client';
import { Metrics, QueueStats } from '../types';
import { Activity, XCircle, Clock, AlertTriangle, Users, TrendingUp, AlertCircle, Zap, Timer } from 'lucide-react';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

function AnimatedNumber({ value, duration = 800 }: { value: number; duration?: number }) {
  const [displayValue, setDisplayValue] = useState(value);
  const prevValueRef = useRef(value);

  useEffect(() => {
    const startValue = prevValueRef.current;
    const endValue = value;
    if (startValue === endValue) {
      setDisplayValue(endValue);
      return;
    }

    const startTime = performance.now();
    let animationFrameId: number;

    const step = (now: number) => {
      const elapsed = now - startTime;
      const progress = Math.min(elapsed / duration, 1);
      const easedProgress = progress * (2 - progress); // Ease out quad
      const current = Math.round(startValue + (endValue - startValue) * easedProgress);
      setDisplayValue(current);

      if (progress < 1) {
        animationFrameId = requestAnimationFrame(step);
      } else {
        prevValueRef.current = endValue;
      }
    };

    animationFrameId = requestAnimationFrame(step);
    return () => cancelAnimationFrame(animationFrameId);
  }, [value, duration]);

  return <>{displayValue.toLocaleString()}</>;
}

function SuccessRateGauge({ successRate }: { successRate: number }) {
  const [displayRate, setDisplayRate] = useState(successRate);
  const prevRateRef = useRef(successRate);

  useEffect(() => {
    const startVal = prevRateRef.current;
    const endVal = successRate;
    if (startVal === endVal) {
      setDisplayRate(endVal);
      return;
    }

    const startTime = performance.now();
    let frameId: number;

    const animate = (now: number) => {
      const elapsed = now - startTime;
      const progress = Math.min(elapsed / 800, 1);
      const eased = progress * (2 - progress);
      const val = Math.round(startVal + (endVal - startVal) * eased);
      setDisplayRate(val);

      if (progress < 1) {
        frameId = requestAnimationFrame(animate);
      } else {
        prevRateRef.current = endVal;
      }
    };

    frameId = requestAnimationFrame(animate);
    return () => cancelAnimationFrame(frameId);
  }, [successRate]);

  return (
    <div className="radial-gauge-container">
      <span className="metric-label" style={{ marginBottom: 12, justifyContent: 'center', fontSize: 13 }}>
        <TrendingUp size={14} /> Success Rate
      </span>
      <div
        className="radial-gauge"
        style={{
          background: `conic-gradient(var(--success) ${displayRate * 3.6}deg, rgba(34, 197, 94, 0.08) ${displayRate * 3.6}deg)`
        }}
      >
        <div className="radial-gauge-track">
          <span className="radial-gauge-value">{displayRate}%</span>
          <span className="radial-gauge-label">PASS RATE</span>
        </div>
      </div>
      <span className="metric-sub" style={{ textAlign: 'center', fontSize: 11 }}>
        Job execution efficiency over the last hour
      </span>
    </div>
  );
}

import { useApp } from '../context/AppContext';

export default function DashboardPage() {
  const { theme } = useApp();
  const isLight = theme === 'light';

  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [queueStats, setQueueStats] = useState<QueueStats[]>([]);
  const [chartData, setChartData] = useState<any[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = async (showLoading = false) => {
    if (showLoading) setLoading(true);
    try {
      setError(null);
      const [metricsRes, queuesRes] = await Promise.all([
        metricsApi.global(),
        queueApi.list(undefined, 0, 100),
      ]);
      setMetrics(metricsRes.data);

      const queueList = queuesRes.data.content || [];
      const statsPromises = queueList.map((q: any) =>
        queueApi.stats(q.id).then(r => r.data).catch(() => null)
      );
      const stats = await Promise.all(statsPromises);
      setQueueStats(stats.filter(Boolean));
    } catch (err: any) {
      console.error('Failed to fetch dashboard data:', err);
      setError('Failed to fetch metrics from server. Verify that the backend service is up and running.');
    } finally {
      if (showLoading) setLoading(false);
    }
  };

  useEffect(() => {
    fetchData(true);
    const interval = setInterval(() => fetchData(false), 5000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    if (metrics) {
      setChartData(prev => {
        const point = {
          time: new Date().toLocaleTimeString(),
          completed: metrics.completedLastHour,
          failed: metrics.failedLastHour,
          running: metrics.runningJobs,
        };
        const next = [...prev, point];
        return next.length > 30 ? next.slice(-30) : next;
      });
    }
  }, [metrics]);

  if (loading) return <div className="loading-container"><div className="spinner" /></div>;

  if (error && !metrics) {
    return (
      <div className="error-state card animate-in">
        <AlertCircle size={48} style={{ color: 'var(--error)', marginBottom: 12 }} />
        <h3>Failed to Load Dashboard</h3>
        <p>{error}</p>
        <button className="btn btn-primary" onClick={() => fetchData(true)}>Retry Connection</button>
      </div>
    );
  }

  const greeting = (() => {
    const h = new Date().getHours();
    if (h < 12) return 'Good Morning';
    if (h < 17) return 'Good Afternoon';
    return 'Good Evening';
  })();

  return (
    <div className="animate-in">
      <div className="page-header">
        <div>
          <h1 className="page-title">{greeting} 👋</h1>
          <p className="page-subtitle" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            Real-time system overview
            <span style={{
              width: 6, height: 6, borderRadius: '50%', background: 'var(--success)',
              display: 'inline-block', animation: 'pulse-dot-active 2s infinite',
              boxShadow: '0 0 6px var(--success-glow)'
            }} />
            <span style={{ fontSize: 11, color: 'var(--success)' }}>Live</span>
          </p>
        </div>
      </div>

      {metrics && (
        <div className="dashboard-split-layout stagger-in">
          {/* Left Column (2/3 width) */}
          <div style={{ display: 'flex', flexDirection: 'column' }} className="stagger-in">
            {/* Throughput Chart (Visual Anchor) */}
            <div className="card" style={{ marginBottom: 'var(--space-md)' }}>
              <div className="card-header">
                <span className="card-title">
                  <Zap size={16} className="gradient-text" style={{ color: 'var(--accent-primary)' }} />
                  Throughput — Live
                </span>
                <span className="tag">{chartData.length} data points</span>
              </div>
              <div className="chart-container">
                <ResponsiveContainer>
                  <AreaChart data={chartData}>
                    <defs>
                      <linearGradient id="colorCompleted" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#22c55e" stopOpacity={isLight ? 0.15 : 0.3} />
                        <stop offset="95%" stopColor="#22c55e" stopOpacity={0} />
                      </linearGradient>
                      <linearGradient id="colorFailed" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#ef4444" stopOpacity={isLight ? 0.15 : 0.3} />
                        <stop offset="95%" stopColor="#ef4444" stopOpacity={0} />
                      </linearGradient>
                      <linearGradient id="colorRunning" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#3b82f6" stopOpacity={isLight ? 0.1 : 0.2} />
                        <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke={isLight ? 'rgba(99, 102, 241, 0.08)' : 'rgba(99, 102, 241, 0.07)'} />
                    <XAxis dataKey="time" stroke={isLight ? '#64748b' : '#94a3b8'} fontSize={10} tickLine={false} />
                    <YAxis stroke={isLight ? '#64748b' : '#94a3b8'} fontSize={10} tickLine={false} axisLine={false} />
                    <Tooltip
                      contentStyle={{
                        background: isLight ? 'rgba(255, 255, 255, 0.95)' : 'rgba(14, 18, 35, 0.95)',
                        border: isLight ? '1px solid rgba(99,102,241,0.2)' : '1px solid rgba(99,102,241,0.2)',
                        borderRadius: 12,
                        fontSize: 12,
                        backdropFilter: 'blur(12px)',
                        boxShadow: isLight ? '0 8px 32px rgba(0,0,0,0.08)' : '0 8px 32px rgba(0,0,0,0.4)',
                        color: isLight ? '#0f172a' : '#f1f5f9'
                      }}
                      labelStyle={{ color: isLight ? '#475569' : '#94a3b8', marginBottom: 4 }}
                      itemStyle={{ color: isLight ? '#0f172a' : '#f1f5f9' }}
                    />
                    <Area type="monotone" dataKey="completed" stroke="#22c55e" strokeWidth={2} fillOpacity={1} fill="url(#colorCompleted)" name="Completed" />
                    <Area type="monotone" dataKey="failed" stroke="#ef4444" strokeWidth={2} fillOpacity={1} fill="url(#colorFailed)" name="Failed" />
                    <Area type="monotone" dataKey="running" stroke="#3b82f6" strokeWidth={1.5} fillOpacity={1} fill="url(#colorRunning)" name="Running" strokeDasharray="4 4" />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </div>

            {/* Asymmetric Metrics: Running & Failed (Operators focus) */}
            <div className="dashboard-metrics-hierarchy stagger-in">
              <div className="metric-card large info">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span className="metric-label" style={{ margin: 0 }}><Activity size={16} /> Running</span>
                  <span className="tag" style={{ border: '1px solid rgba(59, 130, 246, 0.2)' }}>Active</span>
                </div>
                <div>
                  <div className="metric-value" style={{ color: 'var(--info)' }}>
                    <AnimatedNumber value={metrics.runningJobs} />
                  </div>
                  <div className="metric-sub" style={{ fontSize: 11, marginTop: 4 }}>Workloads currently executing concurrently</div>
                </div>
              </div>

              <div className="metric-card large error">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span className="metric-label" style={{ margin: 0 }}><XCircle size={16} /> Failed (1h)</span>
                  <span className="tag" style={{ background: 'var(--error-bg)', color: 'var(--error)', border: '1px solid rgba(239, 68, 68, 0.2)' }}>Alerts</span>
                </div>
                <div>
                  <div className="metric-value" style={{ color: 'var(--error)' }}>
                    <AnimatedNumber value={metrics.failedLastHour} />
                  </div>
                  <div className="metric-sub" style={{ fontSize: 11, marginTop: 4 }}>Unsuccessful executions in last 60m</div>
                </div>
              </div>
            </div>

            {/* Low-signal secondary statistics inline info-strip */}
            <div className="metric-info-strip stagger-in">
              <div className="metric-strip-item">
                <span className="metric-strip-label">Pending</span>
                <span className="metric-strip-value">
                  <AnimatedNumber value={metrics.pendingJobs} />
                </span>
              </div>
              <div className="metric-strip-item">
                <span className="metric-strip-label">Completed</span>
                <span className="metric-strip-value" style={{ color: 'var(--success)' }}>
                  <AnimatedNumber value={metrics.completedLastHour} />
                </span>
              </div>
              <div className="metric-strip-item">
                <span className="metric-strip-label">DLQ Letters</span>
                <span className="metric-strip-value" style={{ color: metrics.deadLetterJobs > 0 ? 'var(--error)' : undefined }}>
                  <AnimatedNumber value={metrics.deadLetterJobs} />
                </span>
              </div>
              <div className="metric-strip-item">
                <span className="metric-strip-label">Workers</span>
                <span className="metric-strip-value">
                  <AnimatedNumber value={metrics.activeWorkers} />
                </span>
              </div>
              <div className="metric-strip-item">
                <span className="metric-strip-label">Avg Exec</span>
                <span className="metric-strip-value" style={{ fontSize: 13, alignSelf: 'flex-start', marginTop: 6 }}>
                  {metrics.avgExecutionTimeMs ? <><AnimatedNumber value={Math.round(metrics.avgExecutionTimeMs)} />ms</> : '—'}
                </span>
              </div>
            </div>
          </div>

          {/* Right Column / Sidebar (1/3 width) */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-md)' }} className="stagger-in">
            {/* Circular Success Rate Gauge Widget */}
            <SuccessRateGauge successRate={metrics.successRate} />

            {/* Queue Health Status Side List */}
            <div className="card" style={{ padding: '20px 24px' }}>
              <div className="card-header" style={{ marginBottom: '16px' }}>
                <span className="card-title" style={{ fontSize: '13px' }}>Queue Health</span>
                <span className="tag">{queueStats.length} active</span>
              </div>
              {queueStats.length === 0 ? (
                <div className="empty-state" style={{ padding: '20px 0' }}><p>No registered queues</p></div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                  {queueStats.map(q => {
                    const total = q.pending + q.running + q.completed + q.failed + q.deadLetter;
                    const successPct = total > 0 ? Math.round((q.completed / total) * 100) : 100;
                    return (
                      <div key={q.queueId} style={{ display: 'flex', flexDirection: 'column', gap: '4px', borderBottom: '1px solid rgba(99,102,241,0.04)', paddingBottom: '10px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <span style={{ fontWeight: 600, fontSize: 13, color: 'var(--text-primary)' }}>{q.queueName}</span>
                          <span className={`status-badge ${q.paused ? 'PAUSED' : 'ACTIVE'}`} style={{ fontSize: '9px', padding: '2px 8px' }}>
                            {q.paused ? 'Paused' : 'Active'}
                          </span>
                        </div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: 'var(--text-secondary)', marginTop: '2px' }}>
                          <span>Pending: {q.pending} | Running: {q.running}</span>
                          <span style={{ color: successPct > 80 ? 'var(--success)' : successPct > 50 ? 'var(--warning)' : 'var(--error)' }}>
                            {successPct}% success
                          </span>
                        </div>
                        {/* Mini progress bar */}
                        <div style={{ height: '3px', background: 'rgba(255,255,255,0.05)', borderRadius: '10px', overflow: 'hidden', marginTop: '4px' }}>
                          <div style={{ height: '100%', background: 'var(--success)', width: `${successPct}%`, borderRadius: '10px' }} />
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
