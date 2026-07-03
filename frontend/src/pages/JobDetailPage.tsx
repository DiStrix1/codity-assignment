import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { jobApi } from '../api/client';
import { JobDetail } from '../types';
import { ArrowLeft, RefreshCw, XCircle, AlertTriangle, Terminal, Clock, Cpu, Hash, Calendar, Tag, Layers } from 'lucide-react';
import { format } from 'date-fns';

export default function JobDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [detail, setDetail] = useState<JobDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();
  const [countdown, setCountdown] = useState<number | null>(null);

  const fetchDetail = async (showLoading = false) => {
    if (showLoading) setLoading(true);
    try {
      setError(null);
      const res = await jobApi.get(id!);
      setDetail(res.data);
    } catch (err: any) {
      console.error(err);
      setError(err.response?.data?.message || 'Failed to fetch job details. The job may have been deleted.');
    } finally {
      if (showLoading) setLoading(false);
    }
  };

  useEffect(() => {
    fetchDetail(true);
    const intervalId = setInterval(() => fetchDetail(false), 3000);
    return () => clearInterval(intervalId);
  }, [id]);

  useEffect(() => {
    if (detail?.job?.status === 'RETRY_PENDING' && detail.job.scheduledAt) {
      const calculateTimeLeft = () => {
        const diff = new Date(detail.job.scheduledAt!).getTime() - Date.now();
        return Math.max(0, Math.floor(diff / 1000));
      };

      setCountdown(calculateTimeLeft());

      const timer = setInterval(() => {
        const timeLeft = calculateTimeLeft();
        setCountdown(timeLeft);
        if (timeLeft <= 0) {
          clearInterval(timer);
          fetchDetail(false);
        }
      }, 1000);

      return () => clearInterval(timer);
    } else {
      setCountdown(null);
    }
  }, [detail?.job]);

  const handleRetry = async () => {
    try {
      await jobApi.retry(id!);
      fetchDetail(false);
    } catch (err: any) {
      console.error(err);
      alert('Failed to retry job: ' + (err.response?.data?.message || err.message));
    }
  };

  const handleCancel = async () => {
    if (!window.confirm('Are you sure you want to cancel this job?')) return;
    try {
      await jobApi.cancel(id!);
      fetchDetail(false);
    } catch (err: any) {
      console.error(err);
      alert('Failed to cancel job: ' + (err.response?.data?.message || err.message));
    }
  };

  const handleDelete = async () => {
    if (!window.confirm('Are you sure you want to permanently delete this job and its execution records?')) return;
    try {
      await jobApi.delete(id!);
      navigate('/jobs');
    } catch (err: any) {
      console.error(err);
      alert('Failed to delete job: ' + (err.response?.data?.message || err.message));
    }
  };

  if (loading) return <div className="loading-container"><div className="spinner" /></div>;

  if (error || !detail) {
    return (
      <div className="animate-in">
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-md)', marginBottom: '24px' }}>
          <button className="btn btn-ghost btn-icon" onClick={() => navigate('/jobs')}><ArrowLeft size={18} /></button>
          <span style={{ fontWeight: 600 }}>Back to Jobs</span>
        </div>
        <div className="error-state card">
          <AlertTriangle size={36} style={{ color: 'var(--error)' }} />
          <p>{error || 'Job not found'}</p>
          <button className="btn btn-secondary" onClick={() => fetchDetail(true)}>Retry</button>
        </div>
      </div>
    );
  }

  const { job, executions, recentLogs } = detail;

  return (
    <div className="animate-in">
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-md)' }}>
          <button className="btn btn-ghost btn-icon" onClick={() => navigate('/jobs')}><ArrowLeft size={18} /></button>
          <div>
            <h1 className="page-title">Job Details</h1>
            <p className="page-subtitle" style={{ fontFamily: 'var(--font-mono)', fontSize: 12, letterSpacing: '0.5px' }}>{job.id}</p>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 'var(--space-sm)' }}>
          {(job.status === 'QUEUED' || job.status === 'SCHEDULED') && (
            <button className="btn btn-secondary btn-sm" onClick={handleCancel} style={{ color: 'var(--error)' }}>
              <XCircle size={14} /> Cancel
            </button>
          )}
          {(job.status === 'FAILED' || job.status === 'DEAD_LETTER') && (
            <button className="btn btn-primary btn-sm" onClick={handleRetry}>
              <RefreshCw size={14} /> Retry Now
            </button>
          )}
          <button className="btn btn-ghost btn-sm" onClick={handleDelete} style={{ color: 'var(--error)' }}>
            Delete
          </button>
        </div>
      </div>

      {/* Lifecycle Stepper */}
      <div className="card" style={{ marginBottom: 'var(--space-md)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', position: 'relative', padding: '10px 20px' }}>
          {/* Step 1: Created */}
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', zIndex: 2, flex: 1 }}>
            <div style={{
              width: 32, height: 32, borderRadius: '50%',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              backgroundColor: 'var(--success)', color: '#fff',
              fontSize: 12, fontWeight: 700
            }}>✓</div>
            <span style={{ fontSize: 12, marginTop: 6, fontWeight: 600 }}>Created</span>
          </div>

          <div style={{ height: 2, backgroundColor: 'var(--success)', flex: 1, alignSelf: 'center', marginBottom: 26, zIndex: 1 }} />

          {/* Step 2: Queued/Scheduled */}
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', zIndex: 2, flex: 1 }}>
            <div style={{
              width: 32, height: 32, borderRadius: '50%',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              backgroundColor: (job.status === 'QUEUED' || job.status === 'SCHEDULED') ? 'var(--accent-primary)' : ['CLAIMED', 'RUNNING', 'COMPLETED', 'FAILED', 'DEAD_LETTER', 'RETRY_PENDING'].includes(job.status) ? 'var(--success)' : 'var(--border-color)',
              color: '#fff',
              fontSize: 12, fontWeight: 700
            }}>
              {['CLAIMED', 'RUNNING', 'COMPLETED', 'FAILED', 'DEAD_LETTER', 'RETRY_PENDING'].includes(job.status) ? '✓' : '2'}
            </div>
            <span style={{ fontSize: 12, marginTop: 6, fontWeight: 600 }}>{job.status === 'SCHEDULED' ? 'Scheduled' : 'Queued'}</span>
          </div>

          <div style={{
            height: 2,
            backgroundColor: ['CLAIMED', 'RUNNING', 'COMPLETED', 'FAILED', 'DEAD_LETTER', 'RETRY_PENDING'].includes(job.status) ? 'var(--success)' : 'var(--border-color)',
            flex: 1, alignSelf: 'center', zIndex: 1, marginBottom: 26
          }} />

          {/* Step 3: Running */}
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', zIndex: 2, flex: 1 }}>
            <div style={{
              width: 32, height: 32, borderRadius: '50%',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              backgroundColor: (job.status === 'RUNNING' || job.status === 'CLAIMED') ? 'var(--accent-primary)' : ['COMPLETED', 'FAILED', 'DEAD_LETTER', 'RETRY_PENDING'].includes(job.status) ? 'var(--success)' : 'var(--border-color)',
              color: '#fff',
              fontSize: 12, fontWeight: 700
            }}>
              {['COMPLETED', 'FAILED', 'DEAD_LETTER', 'RETRY_PENDING'].includes(job.status) ? '✓' : '3'}
            </div>
            <span style={{ fontSize: 12, marginTop: 6, fontWeight: 600 }}>Running</span>
          </div>

          <div style={{
            height: 2,
            backgroundColor: ['COMPLETED', 'FAILED', 'DEAD_LETTER', 'RETRY_PENDING'].includes(job.status) ? (job.status === 'RETRY_PENDING' ? '#f59e0b' : 'var(--success)') : 'var(--border-color)',
            flex: 1, alignSelf: 'center', zIndex: 1, marginBottom: 26
          }} />

          {/* Step 4: Finished/Retrying */}
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', zIndex: 2, flex: 1 }}>
            <div style={{
              width: 32, height: 32, borderRadius: '50%',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              backgroundColor: job.status === 'COMPLETED' ? 'var(--success)' :
                job.status === 'DEAD_LETTER' ? 'var(--error)' :
                  job.status === 'RETRY_PENDING' ? '#f59e0b' : 'var(--border-color)',
              color: '#fff',
              fontSize: 12, fontWeight: 700
            }}>
              {job.status === 'COMPLETED' ? '✓' : job.status === 'DEAD_LETTER' ? '✗' : job.status === 'RETRY_PENDING' ? '⏳' : '4'}
            </div>
            <span style={{
              fontSize: 12, marginTop: 6, fontWeight: 600,
              color: job.status === 'COMPLETED' ? 'var(--success)' :
                job.status === 'DEAD_LETTER' ? 'var(--error)' :
                  job.status === 'RETRY_PENDING' ? '#f59e0b' : 'var(--text-secondary)'
            }}>
              {job.status === 'COMPLETED' ? 'Completed' :
                job.status === 'DEAD_LETTER' ? 'Dead Letter' :
                  job.status === 'RETRY_PENDING' ? 'Retrying' : 'Finished'}
            </span>
          </div>
        </div>
      </div>

      {/* Status & Config */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 'var(--space-md)', marginBottom: 'var(--space-xl)' }}>
        <div className="card">
          <div className="card-title" style={{ marginBottom: 'var(--space-md)', fontSize: 14 }}>
            <Hash size={14} style={{ color: 'var(--accent-primary)' }} /> Status & Attempts
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-md)', marginBottom: 'var(--space-md)' }}>
            <span className={`status-badge ${job.status}`}>{job.status}</span>
            <span style={{ fontSize: 13, color: 'var(--text-secondary)', fontVariantNumeric: 'tabular-nums' }}>
              Attempt {job.attemptCount} of {job.maxAttempts}
            </span>
          </div>
          {job.status === 'RETRY_PENDING' && countdown !== null && (
            <div style={{
              margin: '12px 0',
              padding: '12px',
              background: 'rgba(245, 158, 11, 0.1)',
              borderLeft: '3px solid #f59e0b',
              borderRadius: 'var(--radius-sm)',
              fontSize: '13px',
              color: '#f59e0b',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              fontWeight: 500
            }}>
              <Clock size={15} style={{ animation: 'spin 3s linear infinite' }} />
              <span>Retry pending. Next attempt in <strong>{countdown}s</strong>...</span>
            </div>
          )}
          {job.errorMessage && (
            <div style={{
              padding: '12px',
              background: 'var(--error-bg)',
              border: '1px solid rgba(239, 68, 68, 0.15)',
              borderLeft: '3px solid var(--error)',
              borderRadius: 'var(--radius-sm)',
              fontSize: 12,
              color: 'var(--error)',
              fontFamily: 'var(--font-mono)',
              wordBreak: 'break-all'
            }}>
              {job.errorMessage}
            </div>
          )}
        </div>
        <div className="card">
          <div className="card-title" style={{ marginBottom: 'var(--space-md)', fontSize: 14 }}>
            <Tag size={14} style={{ color: 'var(--accent-primary)' }} /> Configuration
          </div>
          <div style={{ fontSize: 13, color: 'var(--text-secondary)', display: 'grid', gap: '10px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Layers size={13} style={{ color: 'var(--text-muted)' }} />
              <strong style={{ color: 'var(--text-primary)' }}>Queue:</strong> {job.queueName}
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Tag size={13} style={{ color: 'var(--text-muted)' }} />
              <strong style={{ color: 'var(--text-primary)' }}>Type:</strong> <span className="tag">{job.type}</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Hash size={13} style={{ color: 'var(--text-muted)' }} />
              <strong style={{ color: 'var(--text-primary)' }}>Priority:</strong> {job.priority}
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Calendar size={13} style={{ color: 'var(--text-muted)' }} />
              <strong style={{ color: 'var(--text-primary)' }}>Created:</strong> {format(new Date(job.createdAt), 'PPpp')}
            </div>
            {job.scheduledAt && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Clock size={13} style={{ color: 'var(--text-muted)' }} />
                <strong style={{ color: 'var(--text-primary)' }}>Scheduled:</strong> {format(new Date(job.scheduledAt), 'PPpp')}
              </div>
            )}
            {job.claimedAt && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Cpu size={13} style={{ color: 'var(--text-muted)' }} />
                <strong style={{ color: 'var(--text-primary)' }}>Claimed:</strong> {format(new Date(job.claimedAt), 'PPpp')}
              </div>
            )}
            {job.idempotencyKey && <div><strong style={{ color: 'var(--text-primary)' }}>Idempotency:</strong> <code style={{ fontFamily: 'var(--font-mono)', fontSize: 11 }}>{job.idempotencyKey}</code></div>}
            {job.batchId && <div><strong style={{ color: 'var(--text-primary)' }}>Batch:</strong> <code style={{ fontFamily: 'var(--font-mono)', fontSize: 11 }}>{job.batchId}</code></div>}
          </div>
        </div>
      </div>

      {/* Payload */}
      <div className="card" style={{ marginBottom: 'var(--space-xl)' }}>
        <div className="card-title" style={{ marginBottom: 'var(--space-md)', fontSize: 14 }}>Payload</div>
        <div className="code-block">
          <div className="code-block-header">
            <span>JSON</span>
          </div>
          <pre className="code-block-body">
            {JSON.stringify(job.payload, null, 2)}
          </pre>
        </div>
      </div>

      {/* Execution History */}
      <div className="card" style={{ marginBottom: 'var(--space-xl)' }}>
        <div className="card-header">
          <span className="card-title">Execution History</span>
          <span className="tag">{executions.length} run{executions.length !== 1 ? 's' : ''}</span>
        </div>
        {executions.length === 0 ? (
          <div className="empty-state"><p>No executions recorded yet.</p></div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {executions.map(exec => (
              <div key={exec.id} style={{
                borderLeft: `3px solid var(--${exec.status === 'COMPLETED' ? 'success' : exec.status === 'FAILED' ? 'error' : 'info'})`,
                paddingLeft: '16px',
                paddingTop: '12px',
                paddingBottom: '12px',
                background: `var(--${exec.status === 'COMPLETED' ? 'success' : exec.status === 'FAILED' ? 'error' : 'info'}-bg)`,
                borderRadius: '0 var(--radius-sm) var(--radius-sm) 0',
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontWeight: 700, fontSize: 14 }}>Attempt #{exec.attemptNumber}</span>
                  <span className={`status-badge ${exec.status}`}>{exec.status}</span>
                </div>
                <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 6, display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
                  {exec.startedAt && <span>{format(new Date(exec.startedAt), 'PPpp')}</span>}
                  {exec.durationMs != null && <span>⏱ {exec.durationMs}ms</span>}
                  {exec.workerId && <span>🖥 {exec.workerId.substring(0, 8)}…</span>}
                </div>
                {exec.errorMessage && (
                  <div style={{
                    marginTop: 8,
                    padding: '8px 12px',
                    background: 'rgba(0,0,0,0.2)',
                    borderRadius: 'var(--radius-sm)',
                    fontSize: 12,
                    color: 'var(--error)',
                    fontFamily: 'var(--font-mono)'
                  }}>
                    {exec.errorMessage}
                  </div>
                )}
                {exec.stackTrace && (
                  <pre style={{
                    background: '#080b14',
                    padding: '12px',
                    borderRadius: 'var(--radius-sm)',
                    fontSize: '11px',
                    overflow: 'auto',
                    border: '1px solid var(--border-color)',
                    marginTop: 8,
                    color: '#ff7b72',
                    maxHeight: '150px'
                  }}>
                    {exec.stackTrace}
                  </pre>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Logs */}
      <div className="card">
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: 'var(--space-md)' }}>
          <Terminal size={16} style={{ color: 'var(--accent-primary)' }} />
          <div className="card-title" style={{ margin: 0, fontSize: 14 }}>Live Execution Logs</div>
        </div>
        {recentLogs.length === 0 ? (
          <div className="empty-state"><p>No log statements written by this job execution.</p></div>
        ) : (
          <div className="log-viewer">
            <div className="log-viewer-header">
              <div className="log-viewer-dot" style={{ background: '#ef4444' }} />
              <div className="log-viewer-dot" style={{ background: '#f59e0b' }} />
              <div className="log-viewer-dot" style={{ background: '#22c55e' }} />
              <span style={{ marginLeft: 8, fontSize: 11, color: 'var(--text-muted)' }}>output — {recentLogs.length} entries</span>
            </div>
            <div className="log-viewer-body">
              {recentLogs.map(log => (
                <div key={log.id} className="log-entry">
                  <span className="log-time">{format(new Date(log.createdAt), 'HH:mm:ss.SSS')}</span>
                  <span className={`log-level ${log.level}`}>{log.level}</span>
                  <span className="log-message">{log.message}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
