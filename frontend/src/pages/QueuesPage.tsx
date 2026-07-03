import { useState, useEffect } from 'react';
import { queueApi, retryPolicyApi, projectApi } from '../api/client';
import { Queue, QueueStats, RetryPolicy, Project } from '../types';
import { Play, Pause, Trash2, Edit2, Plus, Layers, X, AlertTriangle, Activity } from 'lucide-react';

export default function QueuesPage() {
  const [queues, setQueues] = useState<Queue[]>([]);
  const [stats, setStats] = useState<Record<string, QueueStats>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [policies, setPolicies] = useState<RetryPolicy[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingQueue, setEditingQueue] = useState<Queue | null>(null);
  const [formData, setFormData] = useState({
    name: '',
    projectId: '',
    priority: 0,
    maxConcurrency: 5,
    retryPolicyId: '',
    paused: false,
  });

  const fetchQueues = async (showLoading = false) => {
    if (showLoading) setLoading(true);
    try {
      setError(null);
      const [queuesRes, policiesRes, projectsRes] = await Promise.all([
        queueApi.list(undefined, 0, 100),
        retryPolicyApi.list(),
        projectApi.list(),
      ]);

      const queueList = queuesRes.data.content || [];
      setQueues(queueList);
      setPolicies(policiesRes.data.content || []);
      setProjects(projectsRes.data.content || []);

      const statsMap: Record<string, QueueStats> = {};
      await Promise.all(
        queueList.map(async (q: Queue) => {
          try {
            const s = await queueApi.stats(q.id);
            statsMap[q.id] = s.data;
          } catch (e) {
            console.error('Failed to load stats for queue ' + q.id, e);
          }
        })
      );
      setStats(statsMap);
    } catch (err: any) {
      console.error(err);
      setError('Failed to fetch queues. Please make sure the backend is active.');
    } finally {
      if (showLoading) setLoading(false);
    }
  };

  useEffect(() => {
    fetchQueues(true);
    const interval = setInterval(() => fetchQueues(false), 5000);
    return () => clearInterval(interval);
  }, []);

  const openNewModal = () => {
    setEditingQueue(null);
    setFormData({
      name: '',
      projectId: projects[0]?.id || '',
      priority: 0,
      maxConcurrency: 5,
      retryPolicyId: '',
      paused: false,
    });
    setIsModalOpen(true);
  };

  const openEditModal = (q: Queue, e: React.MouseEvent) => {
    e.stopPropagation();
    setEditingQueue(q);
    setFormData({
      name: q.name,
      projectId: q.projectId,
      priority: q.priority,
      maxConcurrency: q.maxConcurrency,
      retryPolicyId: q.retryPolicyId || '',
      paused: q.paused,
    });
    setIsModalOpen(true);
  };

  const handlePauseResume = async (q: Queue, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      q.paused ? await queueApi.resume(q.id) : await queueApi.pause(q.id);
      fetchQueues(false);
    } catch (err) {
      console.error(err);
    }
  };

  const handleDelete = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!window.confirm('Are you sure you want to delete this queue? All linked jobs will be lost!')) return;
    try {
      await queueApi.delete(id);
      fetchQueues(false);
    } catch (err: any) {
      console.error(err);
      alert('Failed to delete queue: ' + (err.response?.data?.message || err.message));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.projectId) {
      alert('Please select a project first.');
      return;
    }
    const payload = {
      ...formData,
      retryPolicyId: formData.retryPolicyId || null,
    };

    try {
      if (editingQueue) {
        await queueApi.update(editingQueue.id, payload);
      } else {
        await queueApi.create(payload);
      }
      setIsModalOpen(false);
      fetchQueues(false);
    } catch (err: any) {
      console.error(err);
      alert('Failed to save queue: ' + (err.response?.data?.message || err.message));
    }
  };

  if (loading) return <div className="loading-container"><div className="spinner" /></div>;

  return (
    <div className="animate-in">
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1 className="page-title">Queues</h1>
          <p className="page-subtitle">{queues.length} queue{queues.length !== 1 ? 's' : ''} configured</p>
        </div>
        <button className="btn btn-primary" onClick={openNewModal}>
          <Plus size={16} /> New Queue
        </button>
      </div>

      {error && (
        <div className="error-state card">
          <AlertTriangle size={36} style={{ color: 'var(--error)' }} />
          <p>{error}</p>
          <button className="btn btn-secondary" onClick={() => fetchQueues(true)}>Retry</button>
        </div>
      )}

      {!error && queues.length === 0 ? (
        <div className="card empty-state">
          <Layers size={48} />
          <p>No queues yet. Click "New Queue" to create one.</p>
        </div>
      ) : (
        <div className="stagger-in" style={{ display: 'grid', gap: 'var(--space-md)' }}>
          {queues.map(q => {
            const s = stats[q.id];
            const total = s ? s.completed + s.failed + s.pending + s.running : 0;
            const healthPct = s && total > 0 ? Math.round((s.completed / total) * 100) : 100;
            const healthColor = healthPct >= 80 ? 'var(--success)' : healthPct >= 50 ? 'var(--warning)' : 'var(--error)';

            return (
              <div key={q.id} className="card" style={{ padding: '0', overflow: 'hidden' }}>
                {/* Gradient top bar */}
                <div style={{
                  height: 3,
                  background: q.paused
                    ? 'linear-gradient(90deg, var(--text-muted), transparent)'
                    : `linear-gradient(90deg, ${healthColor}, transparent)`,
                }} />

                <div style={{ padding: '20px 24px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 16 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-md)', minWidth: 0 }}>
                      <div style={{
                        width: 40, height: 40, borderRadius: 'var(--radius-md)',
                        background: q.paused ? 'rgba(100,116,139,0.1)' : 'rgba(99,102,241,0.1)',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        flexShrink: 0
                      }}>
                        <Layers size={18} style={{ color: q.paused ? 'var(--text-muted)' : 'var(--accent-primary)' }} />
                      </div>
                      <div style={{ minWidth: 0 }}>
                        <div style={{ fontWeight: 700, fontSize: 16, color: 'var(--text-primary)' }}>{q.name}</div>
                        <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                          <span>{q.projectName}</span>
                          <span>·</span>
                          <span>Priority {q.priority}</span>
                          <span>·</span>
                          <span>Max {q.maxConcurrency}</span>
                          {q.retryPolicyName && (<><span>·</span><span>{q.retryPolicyName}</span></>)}
                        </div>
                      </div>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                      {s && (
                        <div style={{ display: 'flex', gap: '6px', fontSize: 12 }}>
                          <span className="tag" style={{ background: 'rgba(99,102,241,0.08)' }}>{s.pending} queued</span>
                          <span className="tag" style={{ background: 'var(--info-bg)', color: 'var(--info)' }}>{s.running} active</span>
                          <span className="tag" style={{ background: 'var(--success-bg)', color: 'var(--success)' }}>{s.completed} done</span>
                          {s.failed > 0 && <span className="tag" style={{ background: 'var(--error-bg)', color: 'var(--error)' }}>{s.failed} failed</span>}
                        </div>
                      )}
                      <span className={`status-badge ${q.paused ? 'PAUSED' : 'ACTIVE'}`}>
                        {q.paused ? 'Paused' : 'Active'}
                      </span>
                      <div style={{ display: 'flex', gap: 4 }}>
                        <button className="btn btn-ghost btn-icon" title={q.paused ? 'Resume' : 'Pause'}
                          onClick={(e) => handlePauseResume(q, e)}>
                          {q.paused ? <Play size={14} /> : <Pause size={14} />}
                        </button>
                        <button className="btn btn-ghost btn-icon" title="Edit" onClick={(e) => openEditModal(q, e)}>
                          <Edit2 size={14} />
                        </button>
                        <button className="btn btn-ghost btn-icon" title="Delete" onClick={(e) => handleDelete(q.id, e)} style={{ color: 'var(--error)' }}>
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </div>
                  </div>

                  {/* Health bar */}
                  {s && total > 0 && (
                    <div style={{ marginTop: 16 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: 'var(--text-muted)', marginBottom: 6 }}>
                        <span>Health Score</span>
                        <span style={{ color: healthColor, fontWeight: 600 }}>{healthPct}%</span>
                      </div>
                      <div className="progress-bar-track">
                        <div className="progress-bar-fill" style={{
                          width: `${healthPct}%`,
                          background: `linear-gradient(90deg, ${healthColor}, ${healthColor}88)`,
                        }} />
                      </div>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Modal Dialog */}
      {isModalOpen && (
        <div className="modal-overlay">
          <div className="modal-content card" style={{ maxWidth: 480, width: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 18, fontWeight: 700 }}>{editingQueue ? 'Edit Queue' : 'Create New Queue'}</h3>
                <p style={{ color: 'var(--text-muted)', fontSize: 12, marginTop: 4 }}>Configure queue processing behavior</p>
              </div>
              <button className="btn btn-ghost btn-icon" onClick={() => setIsModalOpen(false)}>
                <X size={16} />
              </button>
            </div>

            <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div>
                <label className="form-label">Queue Name</label>
                <input
                  type="text"
                  className="form-input"
                  required
                  placeholder="e.g. Image Resizing"
                  value={formData.name}
                  onChange={e => setFormData({ ...formData, name: e.target.value })}
                />
              </div>

              {!editingQueue && (
                <div>
                  <label className="form-label">Project</label>
                  <select
                    className="form-input form-select"
                    required
                    value={formData.projectId}
                    onChange={e => setFormData({ ...formData, projectId: e.target.value })}
                  >
                    <option value="" disabled>Select a project</option>
                    {projects.map(p => (
                      <option key={p.id} value={p.id}>{p.name}</option>
                    ))}
                  </select>
                </div>
              )}

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                <div>
                  <label className="form-label">Priority</label>
                  <input
                    type="number"
                    className="form-input"
                    value={formData.priority}
                    onChange={e => setFormData({ ...formData, priority: parseInt(e.target.value) || 0 })}
                  />
                </div>
                <div>
                  <label className="form-label">Max Concurrency</label>
                  <input
                    type="number"
                    className="form-input"
                    min="1"
                    required
                    value={formData.maxConcurrency}
                    onChange={e => setFormData({ ...formData, maxConcurrency: parseInt(e.target.value) || 1 })}
                  />
                </div>
              </div>

              <div>
                <label className="form-label">Retry Policy (Optional)</label>
                <select
                  className="form-input form-select"
                  value={formData.retryPolicyId}
                  onChange={e => setFormData({ ...formData, retryPolicyId: e.target.value })}
                >
                  <option value="">No retry policy (runs once)</option>
                  {policies.map(p => (
                    <option key={p.id} value={p.id}>{p.name} ({p.strategy})</option>
                  ))}
                </select>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <input
                  type="checkbox"
                  id="paused"
                  checked={formData.paused}
                  onChange={e => setFormData({ ...formData, paused: e.target.checked })}
                  style={{ accentColor: 'var(--accent-primary)' }}
                />
                <label htmlFor="paused" className="form-label" style={{ marginBottom: 0, cursor: 'pointer' }}>
                  Create queue in Paused state
                </label>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '8px', borderTop: '1px solid var(--border-color)', paddingTop: '16px' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setIsModalOpen(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary">
                  {editingQueue ? 'Save Changes' : 'Create Queue'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
