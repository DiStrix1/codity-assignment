import { useState, useEffect } from 'react';
import { dlqApi } from '../api/client';
import { DeadLetterEntry, Page } from '../types';
import { Skull, RefreshCw, Trash2, ChevronLeft, ChevronRight, X, AlertTriangle, AlertCircle } from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';

export default function DLQPage() {
  const [entries, setEntries] = useState<Page<DeadLetterEntry>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20, first: true, last: true });
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [selectedEntry, setSelectedEntry] = useState<DeadLetterEntry | null>(null);

  const fetchDLQ = async (showLoading = false) => {
    if (showLoading) setLoading(true);
    try {
      setError(null);
      const res = await dlqApi.list(page);
      setEntries(res.data);
    } catch (err: any) {
      console.error(err);
      setError('Failed to load Dead Letter Queue entries.');
    } finally {
      if (showLoading) setLoading(false);
    }
  };

  useEffect(() => { fetchDLQ(true); }, [page]);
  useEffect(() => {
    const interval = setInterval(() => fetchDLQ(false), 5000);
    return () => clearInterval(interval);
  }, [page]);

  const handleRetry = async (entry: DeadLetterEntry, e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    try {
      await dlqApi.retry(entry.id);
      setSelectedEntry(null);
      fetchDLQ(false);
    } catch (err: any) {
      console.error(err);
      alert('Failed to requeue job: ' + (err.response?.data?.message || err.message));
    }
  };

  const handleDelete = async (entry: DeadLetterEntry, e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    if (!window.confirm('Are you sure you want to permanently purge this DLQ entry?')) return;
    try {
      await dlqApi.delete(entry.id);
      setSelectedEntry(null);
      fetchDLQ(false);
    } catch (err: any) {
      console.error(err);
      alert('Failed to purge entry: ' + (err.response?.data?.message || err.message));
    }
  };

  if (loading) return <div className="loading-container"><div className="spinner" /></div>;

  return (
    <div className="animate-in">
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1 className="page-title" style={{
            background: 'linear-gradient(135deg, #ef4444, #f87171)',
            WebkitBackgroundClip: 'text',
            WebkitTextFillColor: 'transparent',
            backgroundClip: 'text',
          }}>Dead Letter Queue</h1>
          <p className="page-subtitle">{entries.totalElements} entries waiting review</p>
        </div>
        <button className="btn btn-secondary btn-sm" onClick={() => fetchDLQ(true)}>
          <RefreshCw size={14} /> Refresh
        </button>
      </div>

      {error && (
        <div className="error-state card">
          <AlertTriangle size={36} style={{ color: 'var(--error)' }} />
          <p>{error}</p>
          <button className="btn btn-secondary" onClick={() => fetchDLQ(true)}>Retry</button>
        </div>
      )}

      {!error && (
        <div className="card" style={{ overflow: 'hidden', padding: 0 }}>
          {/* Danger stripe */}
          <div style={{ height: 3, background: 'linear-gradient(90deg, #ef4444, #dc2626, transparent)' }} />
          <div style={{ padding: 'var(--space-lg)' }}>
            {entries.content.length === 0 ? (
              <div className="empty-state">
                <Skull size={48} />
                <p>No dead letter entries — all jobs are healthy!</p>
              </div>
            ) : (
              <>
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Original Job</th>
                      <th>Queue</th>
                      <th>Attempts</th>
                      <th>Error</th>
                      <th>Failed</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {entries.content.map(e => (
                      <tr key={e.id} className="clickable-row" onClick={() => setSelectedEntry(e)}>
                        <td style={{ fontFamily: 'var(--font-mono)', fontSize: 11, letterSpacing: '0.5px' }}>{e.originalJobId.substring(0, 8)}…</td>
                        <td style={{ fontWeight: 500, color: 'var(--text-primary)' }}>{e.queueName}</td>
                        <td style={{ fontVariantNumeric: 'tabular-nums' }}>{e.totalAttempts}</td>
                        <td style={{ maxWidth: 300, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: 'var(--error)', fontSize: 12 }}>
                          {e.finalError || '—'}
                        </td>
                        <td style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                          {formatDistanceToNow(new Date(e.failedAt), { addSuffix: true })}
                        </td>
                        <td>
                          <div style={{ display: 'flex', gap: 4 }}>
                            <button className="btn btn-ghost btn-sm" onClick={(evt) => handleRetry(e, evt)} title="Requeue">
                              <RefreshCw size={12} /> Requeue
                            </button>
                            <button className="btn btn-ghost btn-sm" onClick={(evt) => handleDelete(e, evt)} title="Purge"
                              style={{ color: 'var(--error)' }}>
                              <Trash2 size={12} />
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <div className="pagination">
                  <button className="btn btn-secondary btn-sm" disabled={entries.first} onClick={() => setPage(p => p - 1)}>
                    <ChevronLeft size={14} />
                  </button>
                  <span className="pagination-info">Page {entries.number + 1} of {entries.totalPages}</span>
                  <button className="btn btn-secondary btn-sm" disabled={entries.last} onClick={() => setPage(p => p + 1)}>
                    <ChevronRight size={14} />
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {/* Details Modal */}
      {selectedEntry && (
        <div className="modal-overlay">
          <div className="modal-content card" style={{ maxWidth: 650, width: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <div style={{
                  width: 36, height: 36, borderRadius: 'var(--radius-md)',
                  background: 'var(--error-bg)', display: 'flex', alignItems: 'center', justifyContent: 'center'
                }}>
                  <AlertCircle size={18} style={{ color: 'var(--error)' }} />
                </div>
                <div>
                  <h3 style={{ margin: 0, fontSize: 18, fontWeight: 700 }}>Dead Letter Details</h3>
                  <p style={{ color: 'var(--text-muted)', fontSize: 12, marginTop: 2 }}>Review failure and take action</p>
                </div>
              </div>
              <button className="btn btn-ghost btn-icon" onClick={() => setSelectedEntry(null)}>
                <X size={16} />
              </button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', fontSize: 13 }}>
                <div>
                  <span style={{ color: 'var(--text-muted)', fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.5 }}>Original Job ID</span>
                  <div style={{ fontFamily: 'var(--font-mono)', color: 'var(--text-primary)', marginTop: 4, fontSize: 12 }}>{selectedEntry.originalJobId}</div>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted)', fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.5 }}>Queue</span>
                  <div style={{ color: 'var(--text-primary)', fontWeight: 600, marginTop: 4 }}>{selectedEntry.queueName}</div>
                </div>
              </div>

              <div style={{
                padding: '14px 16px', borderRadius: 'var(--radius-md)',
                background: 'var(--error-bg)', borderLeft: '3px solid var(--error)'
              }}>
                <span style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.5, color: 'var(--error)', opacity: 0.8 }}>Final Error</span>
                <div style={{ fontWeight: 500, marginTop: 6, color: 'var(--text-primary)' }}>{selectedEntry.finalError || 'No error message specified'}</div>
              </div>

              <div>
                <span style={{ color: 'var(--text-muted)', fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.5 }}>Payload</span>
                <div className="code-block" style={{ marginTop: 8 }}>
                  <div className="code-block-header"><span>JSON</span></div>
                  <pre className="code-block-body" style={{ maxHeight: 180 }}>
                    {JSON.stringify(selectedEntry.payload, null, 2)}
                  </pre>
                </div>
              </div>

              {selectedEntry.finalStackTrace && (
                <div>
                  <span style={{ color: 'var(--text-muted)', fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.5 }}>Stack Trace</span>
                  <pre style={{
                    background: '#080b14',
                    padding: '12px',
                    borderRadius: 'var(--radius-sm)',
                    fontSize: '11px',
                    overflow: 'auto',
                    border: '1px solid var(--border-color)',
                    marginTop: 8,
                    color: '#ff7b72',
                    maxHeight: '200px'
                  }}>
                    {selectedEntry.finalStackTrace}
                  </pre>
                </div>
              )}

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '8px', borderTop: '1px solid var(--border-color)', paddingTop: '16px' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setSelectedEntry(null)}>
                  Close
                </button>
                <button type="button" className="btn btn-secondary" onClick={() => handleDelete(selectedEntry)} style={{ color: 'var(--error)' }}>
                  <Trash2 size={12} /> Purge
                </button>
                <button type="button" className="btn btn-primary" onClick={() => handleRetry(selectedEntry)}>
                  <RefreshCw size={12} /> Requeue Job
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
