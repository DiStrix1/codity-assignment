import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { jobApi, queueApi } from '../api/client';
import { Job, Queue, Page } from '../types';
import { Briefcase, ChevronLeft, ChevronRight, RefreshCw, Plus, X, AlertTriangle, Search, Filter } from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';

const STATUSES = ['', 'QUEUED', 'SCHEDULED', 'CLAIMED', 'RUNNING', 'COMPLETED', 'FAILED', 'DEAD_LETTER'];

export default function JobsPage() {
  const [jobs, setJobs] = useState<Page<Job>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20, first: true, last: true });
  const [queues, setQueues] = useState<Queue[]>([]);
  const [statusFilter, setStatusFilter] = useState('');
  const [queueFilter, setQueueFilter] = useState('');
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  // Create Job Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [formData, setFormData] = useState({
    queueId: '',
    type: 'IMMEDIATE',
    payloadRaw: '{\n  "duration_ms": 1000\n}',
    priority: 0,
    scheduledAt: '',
    cronExpression: '*/5 * * * *',
    batchSize: 5,
  });
  const [jsonError, setJsonError] = useState<string | null>(null);

  const fetchJobs = async (showLoading = false) => {
    if (showLoading) setLoading(true);
    try {
      setError(null);
      const res = await jobApi.list({
        status: statusFilter || undefined,
        queueId: queueFilter || undefined,
        page,
        size: 20
      });
      setJobs(res.data);
    } catch (err: any) {
      console.error(err);
      setError('Failed to fetch jobs. Please verify backend connection.');
    } finally {
      if (showLoading) setLoading(false);
    }
  };

  useEffect(() => {
    queueApi.list(undefined, 0, 100)
      .then(r => {
        const qList = r.data.content || [];
        setQueues(qList);
        if (qList.length > 0) {
          setFormData(prev => ({ ...prev, queueId: qList[0].id }));
        }
      })
      .catch(console.error);
  }, []);

  useEffect(() => {
    fetchJobs(true);
    const interval = setInterval(() => fetchJobs(false), 5000);
    return () => clearInterval(interval);
  }, [statusFilter, queueFilter, page]);

  const handleRetry = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await jobApi.retry(id);
      fetchJobs(false);
    } catch (err) {
      console.error(err);
    }
  };

  const openNewModal = () => {
    setJsonError(null);
    setFormData({
      queueId: queues[0]?.id || '',
      type: 'IMMEDIATE',
      payloadRaw: '{\n  "duration_ms": 1000\n}',
      priority: 0,
      scheduledAt: '',
      cronExpression: '*/5 * * * *',
      batchSize: 5,
    });
    setIsModalOpen(true);
  };

  const handlePayloadChange = (val: string) => {
    setFormData(prev => ({ ...prev, payloadRaw: val }));
    try {
      JSON.parse(val);
      setJsonError(null);
    } catch (e: any) {
      setJsonError('Invalid JSON structure: ' + e.message);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.queueId) {
      alert('Please select a queue.');
      return;
    }

    let parsedPayload = {};
    try {
      parsedPayload = JSON.parse(formData.payloadRaw);
    } catch (err) {
      setJsonError('Invalid JSON structure. Please correct before submitting.');
      return;
    }

    try {
      if (formData.type === 'BATCH') {
        const jobsList = [];
        for (let i = 0; i < formData.batchSize; i++) {
          jobsList.push({
            payload: { ...parsedPayload, batch_index: i },
            idempotencyKey: `batch-item-${Date.now()}-${i}-${Math.random()}`
          });
        }
        await jobApi.createBatch({
          queueId: formData.queueId,
          priority: formData.priority,
          jobs: jobsList
        });
      } else {
        const payload: Record<string, any> = {
          queueId: formData.queueId,
          type: formData.type,
          payload: parsedPayload,
          priority: formData.priority,
        };

        if (formData.type === 'DELAYED' || formData.type === 'SCHEDULED') {
          if (!formData.scheduledAt) {
            alert('Please select a scheduled time.');
            return;
          }
          payload.scheduledAt = new Date(formData.scheduledAt).toISOString();
        }

        if (formData.type === 'RECURRING') {
          if (!formData.cronExpression) {
            alert('Please enter a cron expression.');
            return;
          }
          payload.cronExpression = formData.cronExpression;
        }

        await jobApi.create(payload);
      }

      setIsModalOpen(false);
      fetchJobs(false);
    } catch (err: any) {
      console.error(err);
      alert('Failed to submit job: ' + (err.response?.data?.message || err.message));
    }
  };

  return (
    <>
      <div className="animate-in">
        <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <h1 className="page-title">Jobs</h1>
            <p className="page-subtitle">{jobs.totalElements.toLocaleString()} total jobs across all queues</p>
          </div>
          <button className="btn btn-primary" onClick={openNewModal}>
            <Plus size={16} /> New Job
          </button>
        </div>

        <div className="filters-bar" style={{ display: 'flex', gap: '16px', alignItems: 'center', marginBottom: 'var(--space-md)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flex: 1 }}>
            <Filter size={14} style={{ color: 'var(--text-muted)', flexShrink: 0 }} />
            <select className="form-input form-select" value={queueFilter} onChange={e => { setQueueFilter(e.target.value); setPage(0); }} style={{ maxWidth: 240 }}>
              <option value="">All Queues</option>
              {queues.map(q => <option key={q.id} value={q.id}>{q.name}</option>)}
            </select>
          </div>
          <button className="btn btn-secondary btn-sm" onClick={() => fetchJobs(true)}>
            <RefreshCw size={14} /> Refresh
          </button>
        </div>

        {/* Quick Filter Tabs for statuses */}
        <div className="jobs-quick-filters">
          {[
            { label: 'All Jobs', value: '' },
            { label: 'Queued', value: 'QUEUED' },
            { label: 'Scheduled', value: 'SCHEDULED' },
            { label: 'Running', value: 'RUNNING' },
            { label: 'Completed', value: 'COMPLETED' },
            { label: 'Failed', value: 'FAILED' },
            { label: 'Dead Letter', value: 'DEAD_LETTER' }
          ].map(tab => (
            <button
              key={tab.value}
              className={`filter-tab ${statusFilter === tab.value ? 'active' : ''}`}
              onClick={() => { setStatusFilter(tab.value); setPage(0); }}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {error && (
          <div className="error-state card">
            <AlertTriangle size={36} style={{ color: 'var(--error)' }} />
            <p>{error}</p>
            <button className="btn btn-secondary" onClick={() => fetchJobs(true)}>Retry</button>
          </div>
        )}

        {!error && (
          <div className="card" style={{ padding: 'var(--space-md)' }}>
            {loading ? (
              <div className="loading-container"><div className="spinner" /></div>
            ) : jobs.content.length === 0 ? (
              <div className="empty-state"><Briefcase size={48} /><p>No jobs found matching filters</p></div>
            ) : (
              <>
                <table className="data-table dense-table">
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>Queue</th>
                      <th>Type</th>
                      <th>Status</th>
                      <th>Priority</th>
                      <th>Attempts</th>
                      <th>Created</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {jobs.content.map(job => (
                      <tr key={job.id} className="clickable-row" onClick={() => navigate(`/jobs/${job.id}`)}>
                        <td style={{ fontFamily: 'var(--font-mono)', fontSize: 11, letterSpacing: '0.5px' }}>{job.id.substring(0, 8)}…</td>
                        <td style={{ fontWeight: 500, color: 'var(--text-primary)' }}>{job.queueName}</td>
                        <td><span className="tag" style={{ border: 'none', background: 'rgba(99,102,241,0.06)' }}>{job.type}</span></td>
                        <td><span className={`status-badge ${job.status}`}>{job.status}</span></td>
                        <td style={{ fontVariantNumeric: 'tabular-nums' }}>{job.priority}</td>
                        <td style={{ fontVariantNumeric: 'tabular-nums' }}>{job.attemptCount}/{job.maxAttempts}</td>
                        <td style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                          {formatDistanceToNow(new Date(job.createdAt), { addSuffix: true })}
                        </td>
                        <td>
                          {(job.status === 'FAILED' || job.status === 'DEAD_LETTER') && (
                            <button className="btn btn-ghost btn-sm" onClick={(e) => handleRetry(job.id, e)} style={{ display: 'inline-flex', gap: 4, alignItems: 'center' }}>
                              <RefreshCw size={12} /> Retry
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                <div className="pagination" style={{ marginTop: 'var(--space-md)' }}>
                  <button className="btn btn-secondary btn-sm" disabled={jobs.first} onClick={() => setPage(p => p - 1)}>
                    <ChevronLeft size={14} />
                  </button>
                  <span className="pagination-info">
                    Page {jobs.number + 1} of {jobs.totalPages}
                  </span>
                  <button className="btn btn-secondary btn-sm" disabled={jobs.last} onClick={() => setPage(p => p + 1)}>
                    <ChevronRight size={14} />
                  </button>
                </div>
              </>
            )}
          </div>
        )}
      </div>

      {/* New Job Modal — rendered outside animate-in to avoid transform containing block */}
      {isModalOpen && (
        <div className="modal-overlay">
          <div className="modal-content card" style={{ maxWidth: 520, width: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 18, fontWeight: 700 }}>Create New Job</h3>
                <p style={{ color: 'var(--text-muted)', fontSize: 12, marginTop: 4 }}>Submit a job to the processing queue</p>
              </div>
              <button className="btn btn-ghost btn-icon" onClick={() => setIsModalOpen(false)}>
                <X size={16} />
              </button>
            </div>

            <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div>
                <label className="form-label">Target Queue</label>
                <select
                  className="form-input form-select"
                  required
                  value={formData.queueId}
                  onChange={e => setFormData({ ...formData, queueId: e.target.value })}
                >
                  <option value="" disabled>Select target queue</option>
                  {queues.map(q => (
                    <option key={q.id} value={q.id}>{q.name} ({q.projectName})</option>
                  ))}
                </select>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                <div>
                  <label className="form-label">Job Type</label>
                  <select
                    className="form-input form-select"
                    value={formData.type}
                    onChange={e => setFormData({ ...formData, type: e.target.value })}
                  >
                    <option value="IMMEDIATE">Immediate</option>
                    <option value="DELAYED">Delayed</option>
                    <option value="SCHEDULED">Scheduled</option>
                    <option value="RECURRING">Recurring (Cron)</option>
                    <option value="BATCH">Batch (Multiple)</option>
                  </select>
                </div>
                <div>
                  <label className="form-label">Priority</label>
                  <input
                    type="number"
                    className="form-input"
                    value={formData.priority}
                    onChange={e => setFormData({ ...formData, priority: parseInt(e.target.value) || 0 })}
                  />
                </div>
              </div>

              {(formData.type === 'DELAYED' || formData.type === 'SCHEDULED') && (
                <div>
                  <label className="form-label">Scheduled Time</label>
                  <input
                    type="datetime-local"
                    className="form-input"
                    required
                    value={formData.scheduledAt}
                    onChange={e => setFormData({ ...formData, scheduledAt: e.target.value })}
                  />
                </div>
              )}

              {formData.type === 'RECURRING' && (
                <div>
                  <label className="form-label">Cron Expression</label>
                  <input
                    type="text"
                    className="form-input"
                    required
                    placeholder="e.g. */5 * * * *"
                    value={formData.cronExpression}
                    onChange={e => setFormData({ ...formData, cronExpression: e.target.value })}
                    style={{ fontFamily: 'var(--font-mono)' }}
                  />
                </div>
              )}

              {formData.type === 'BATCH' && (
                <div>
                  <label className="form-label">Batch Size (Number of Jobs)</label>
                  <input
                    type="number"
                    className="form-input"
                    min="1"
                    max="100"
                    required
                    value={formData.batchSize}
                    onChange={e => setFormData({ ...formData, batchSize: parseInt(e.target.value) || 1 })}
                  />
                </div>
              )}

              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <label className="form-label" style={{ marginBottom: 0 }}>Payload (JSON)</label>
                  {jsonError && <span style={{ fontSize: 11, color: 'var(--error)', fontWeight: 500 }}>⚠ Invalid JSON</span>}
                </div>
                <textarea
                  className="form-input"
                  rows={5}
                  style={{ fontFamily: 'var(--font-mono)', fontSize: 13, resize: 'vertical', marginTop: 8 }}
                  required
                  value={formData.payloadRaw}
                  onChange={e => handlePayloadChange(e.target.value)}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '8px', borderTop: '1px solid var(--border-color)', paddingTop: '16px' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setIsModalOpen(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={!!jsonError}>
                  <Plus size={14} /> Enqueue Job
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
}
