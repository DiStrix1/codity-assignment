import { useState, useEffect } from 'react';
import { workerApi } from '../api/client';
import { Worker } from '../types';
import { Radio, Cpu, AlertTriangle, Server, Heart, Trash2 } from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';

export default function WorkersPage() {
  const [workers, setWorkers] = useState<Worker[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchWorkers = async (showLoading = false) => {
    if (showLoading) setLoading(true);
    try {
      setError(null);
      const res = await workerApi.list();
      setWorkers(res.data || []);
    } catch (err: any) {
      console.error(err);
      setError('Failed to fetch worker status. Verify connection to backend.');
    } finally {
      if (showLoading) setLoading(false);
    }
  };

  const handleClearOffline = async () => {
    if (!window.confirm('Are you sure you want to clear all offline workers?')) return;
    try {
      await workerApi.clearOffline();
      fetchWorkers(false);
    } catch (err: any) {
      console.error(err);
      alert('Failed to clear offline workers: ' + (err.response?.data?.message || err.message));
    }
  };

  useEffect(() => {
    fetchWorkers(true);
    const i = setInterval(() => fetchWorkers(false), 5000);
    return () => clearInterval(i);
  }, []);

  if (loading) return <div className="loading-container"><div className="spinner" /></div>;

  const utilColor = (pct: number) =>
    pct > 80 ? 'var(--error)' : pct > 50 ? 'var(--warning)' : 'var(--success)';

  return (
    <div className="animate-in">
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1 className="page-title">Workers</h1>
          <p className="page-subtitle">
            {!error ? `${workers.filter(w => w.status === 'ACTIVE').length} active worker(s)` : 'Workers overview'}
          </p>
        </div>
        <button className="btn btn-secondary btn-sm" onClick={handleClearOffline}>
          <Trash2 size={14} /> Clear Offline
        </button>
      </div>

      {error && (
        <div className="error-state card">
          <AlertTriangle size={36} style={{ color: 'var(--error)' }} />
          <p>{error}</p>
          <button className="btn btn-secondary" onClick={() => fetchWorkers(true)}>Retry</button>
        </div>
      )}

      {!error && workers.length === 0 ? (
        <div className="card empty-state">
          <Radio size={48} />
          <p>No workers registered. Start your worker pool service to see active instances.</p>
        </div>
      ) : !error && (
        <div className="stagger-in" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(360px, 1fr))', gap: 'var(--space-md)' }}>
          {workers.map(w => {
            const utilPct = w.maxConcurrentJobs > 0 ? Math.round((w.currentJobCount / w.maxConcurrentJobs) * 100) : 0;
            const color = utilColor(utilPct);

            return (
              <div key={w.id} className="card" style={{ padding: 0, overflow: 'hidden' }}>
                {/* Gradient header strip */}
                <div style={{
                  height: 3,
                  background: w.status === 'ACTIVE'
                    ? 'linear-gradient(90deg, var(--success), var(--success)44)'
                    : w.status === 'DRAINING'
                      ? 'linear-gradient(90deg, var(--warning), var(--warning)44)'
                      : 'linear-gradient(90deg, var(--text-muted), transparent)',
                }} />

                <div style={{ padding: '20px 24px' }}>
                  {/* Header row */}
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                      <div style={{
                        width: 40, height: 40, borderRadius: 'var(--radius-md)',
                        background: w.status === 'ACTIVE' ? 'var(--success-bg)' : 'rgba(100,116,139,0.1)',
                        display: 'flex', alignItems: 'center', justifyContent: 'center'
                      }}>
                        <Server size={18} style={{ color: w.status === 'ACTIVE' ? 'var(--success)' : 'var(--text-muted)' }} />
                      </div>
                      <div>
                        <span style={{ fontWeight: 700, fontSize: 15 }}>{w.hostname}</span>
                        <div style={{ fontFamily: 'var(--font-mono)', color: 'var(--text-muted)', fontSize: 11, marginTop: 2 }}>{w.id.substring(0, 12)}…</div>
                      </div>
                    </div>
                    <span className={`status-badge ${w.status}`}>{w.status}</span>
                  </div>

                  {/* Body layout */}
                  <div style={{ display: 'flex', gap: 24, alignItems: 'center' }}>
                    {/* Utilization ring */}
                    <div className="util-ring" style={{
                      background: `conic-gradient(${color} ${utilPct * 3.6}deg, rgba(99,102,241,0.08) ${utilPct * 3.6}deg)`,
                    }}>
                      <div style={{
                        width: 60, height: 60, borderRadius: '50%', background: 'var(--bg-card)',
                        display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center'
                      }}>
                        <span className="util-ring-value" style={{ color }}>{utilPct}%</span>
                        <span className="util-ring-label">LOAD</span>
                      </div>
                    </div>

                    {/* Stats */}
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px 24px', flex: 1, fontSize: 12 }}>
                      <div>
                        <span style={{ color: 'var(--text-muted)' }}>Jobs Running</span>
                        <div style={{ color: 'var(--text-primary)', fontWeight: 700, fontSize: 16, marginTop: 2, fontVariantNumeric: 'tabular-nums' }}>
                          {w.currentJobCount} <span style={{ fontWeight: 400, fontSize: 12, color: 'var(--text-muted)' }}>/ {w.maxConcurrentJobs}</span>
                        </div>
                      </div>
                      <div>
                        <span style={{ color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: 4 }}>
                          <Heart size={10} /> Last Heartbeat
                        </span>
                        <div style={{ color: 'var(--text-secondary)', marginTop: 2 }}>
                          {formatDistanceToNow(new Date(w.lastHeartbeatAt), { addSuffix: true })}
                        </div>
                      </div>
                      <div style={{ gridColumn: '1 / -1' }}>
                        <span style={{ color: 'var(--text-muted)' }}>Registered</span>
                        <div style={{ color: 'var(--text-secondary)', marginTop: 2 }}>
                          {formatDistanceToNow(new Date(w.registeredAt), { addSuffix: true })}
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
