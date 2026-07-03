import { useState, useEffect } from 'react';
import { retryPolicyApi } from '../api/client';
import { RetryPolicy } from '../types';
import { RotateCcw, Plus, Trash2, Edit2, X, Repeat, TrendingUp, Timer } from 'lucide-react';

const strategyConfig: Record<string, { icon: any; color: string; bg: string; label: string }> = {
  EXPONENTIAL: { icon: TrendingUp, color: 'var(--accent-primary)', bg: 'rgba(99,102,241,0.12)', label: 'Exponential Backoff' },
  LINEAR: { icon: TrendingUp, color: 'var(--info)', bg: 'var(--info-bg)', label: 'Linear Backoff' },
  FIXED: { icon: Timer, color: 'var(--success)', bg: 'var(--success-bg)', label: 'Fixed Interval' },
};

export default function PoliciesPage() {
  const [policies, setPolicies] = useState<RetryPolicy[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingPolicy, setEditingPolicy] = useState<RetryPolicy | null>(null);
  const [formData, setFormData] = useState({
    name: '',
    strategy: 'EXPONENTIAL',
    maxAttempts: 3,
    initialDelayMs: 1000,
    multiplier: 2.0,
    maxDelayMs: 300000,
  });

  const fetchPolicies = async () => {
    try {
      setError(null);
      const res = await retryPolicyApi.list();
      setPolicies(res.data.content || []);
    } catch (err: any) {
      console.error(err);
      setError('Failed to fetch retry policies. Please check backend connection.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchPolicies(); }, []);

  const openNewModal = () => {
    setEditingPolicy(null);
    setFormData({ name: '', strategy: 'EXPONENTIAL', maxAttempts: 3, initialDelayMs: 1000, multiplier: 2.0, maxDelayMs: 300000 });
    setIsModalOpen(true);
  };

  const openEditModal = (p: RetryPolicy) => {
    setEditingPolicy(p);
    setFormData({
      name: p.name, strategy: p.strategy, maxAttempts: p.maxAttempts,
      initialDelayMs: p.initialDelayMs, multiplier: p.multiplier, maxDelayMs: p.maxDelayMs,
    });
    setIsModalOpen(true);
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm('Are you sure you want to delete this policy?')) return;
    try {
      await retryPolicyApi.delete(id);
      fetchPolicies();
    } catch (err: any) {
      console.error(err);
      alert('Failed to delete policy: ' + (err.response?.data?.message || err.message));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editingPolicy) {
        await retryPolicyApi.update(editingPolicy.id, formData);
      } else {
        await retryPolicyApi.create(formData);
      }
      setIsModalOpen(false);
      fetchPolicies();
    } catch (err: any) {
      console.error(err);
      alert('Failed to save policy: ' + (err.response?.data?.message || err.message));
    }
  };

  if (loading) return <div className="loading-container"><div className="spinner" /></div>;

  return (
    <div className="animate-in">
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1 className="page-title">Retry Policies</h1>
          <p className="page-subtitle">Configure transient error recovery strategies</p>
        </div>
        <button className="btn btn-primary" onClick={openNewModal}>
          <Plus size={16} /> New Policy
        </button>
      </div>

      {error && (
        <div className="error-state card">
          <p>{error}</p>
          <button className="btn btn-secondary" onClick={fetchPolicies}>Retry</button>
        </div>
      )}

      {!error && policies.length === 0 && (
        <div className="card empty-state">
          <RotateCcw size={48} />
          <p>No retry policies defined. Create one to get started.</p>
        </div>
      )}

      {!error && policies.length > 0 && (
        <div className="stagger-in" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 'var(--space-md)' }}>
          {policies.map(p => {
            const cfg = strategyConfig[p.strategy] || strategyConfig.EXPONENTIAL;
            const Icon = cfg.icon;

            return (
              <div key={p.id} className="card" style={{ padding: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
                {/* Strategy gradient strip */}
                <div style={{ height: 3, background: `linear-gradient(90deg, ${cfg.color}, transparent)` }} />

                <div style={{ padding: '20px 24px', flex: 1, display: 'flex', flexDirection: 'column' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                      <div style={{
                        width: 40, height: 40, borderRadius: 'var(--radius-md)',
                        background: cfg.bg, display: 'flex', alignItems: 'center', justifyContent: 'center'
                      }}>
                        <Icon size={18} style={{ color: cfg.color }} />
                      </div>
                      <div>
                        <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700 }}>{p.name}</h3>
                        <span style={{ fontSize: 11, color: cfg.color, fontWeight: 500 }}>{cfg.label}</span>
                      </div>
                    </div>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px', fontSize: 12, flex: 1 }}>
                    <div>
                      <span style={{ color: 'var(--text-muted)', fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.5 }}>Max Attempts</span>
                      <div style={{ color: 'var(--text-primary)', fontWeight: 700, fontSize: 20, marginTop: 4, fontVariantNumeric: 'tabular-nums' }}>{p.maxAttempts}</div>
                    </div>
                    <div>
                      <span style={{ color: 'var(--text-muted)', fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.5 }}>Initial Delay</span>
                      <div style={{ color: 'var(--text-primary)', fontWeight: 700, fontSize: 20, marginTop: 4, fontVariantNumeric: 'tabular-nums' }}>
                        {p.initialDelayMs >= 1000 ? `${(p.initialDelayMs / 1000).toFixed(p.initialDelayMs % 1000 === 0 ? 0 : 1)}s` : `${p.initialDelayMs}ms`}
                      </div>
                    </div>
                    {p.strategy === 'EXPONENTIAL' && (
                      <div>
                        <span style={{ color: 'var(--text-muted)', fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.5 }}>Multiplier</span>
                        <div style={{ color: 'var(--text-primary)', fontWeight: 700, fontSize: 20, marginTop: 4 }}>{p.multiplier}×</div>
                      </div>
                    )}
                    {p.strategy !== 'FIXED' && (
                      <div>
                        <span style={{ color: 'var(--text-muted)', fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.5 }}>Max Delay</span>
                        <div style={{ color: 'var(--text-primary)', fontWeight: 700, fontSize: 20, marginTop: 4, fontVariantNumeric: 'tabular-nums' }}>
                          {p.maxDelayMs >= 60000 ? `${Math.round(p.maxDelayMs / 60000)}m` : p.maxDelayMs >= 1000 ? `${Math.round(p.maxDelayMs / 1000)}s` : `${p.maxDelayMs}ms`}
                        </div>
                      </div>
                    )}
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '6px', marginTop: '20px', borderTop: '1px solid var(--border-color)', paddingTop: '14px' }}>
                    <button className="btn btn-ghost btn-sm" onClick={() => openEditModal(p)}>
                      <Edit2 size={12} /> Edit
                    </button>
                    <button className="btn btn-ghost btn-sm" onClick={() => handleDelete(p.id)} style={{ color: 'var(--error)' }}>
                      <Trash2 size={12} /> Delete
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Modal */}
      {isModalOpen && (
        <div className="modal-overlay">
          <div className="modal-content card" style={{ maxWidth: 480, width: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 18, fontWeight: 700 }}>{editingPolicy ? 'Edit Retry Policy' : 'Create Retry Policy'}</h3>
                <p style={{ color: 'var(--text-muted)', fontSize: 12, marginTop: 4 }}>Configure failure recovery behavior</p>
              </div>
              <button className="btn btn-ghost btn-icon" onClick={() => setIsModalOpen(false)}>
                <X size={16} />
              </button>
            </div>

            <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div>
                <label className="form-label">Policy Name</label>
                <input type="text" className="form-input" required placeholder="e.g. Exponential Backoff"
                  value={formData.name} onChange={e => setFormData({ ...formData, name: e.target.value })} />
              </div>

              <div>
                <label className="form-label">Strategy</label>
                <select className="form-input form-select" value={formData.strategy}
                  onChange={e => setFormData({ ...formData, strategy: e.target.value })}>
                  <option value="FIXED">Fixed Interval</option>
                  <option value="LINEAR">Linear Backoff</option>
                  <option value="EXPONENTIAL">Exponential Backoff</option>
                </select>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                <div>
                  <label className="form-label">Max Attempts</label>
                  <input type="number" className="form-input" min="1" max="100" required
                    value={formData.maxAttempts} onChange={e => setFormData({ ...formData, maxAttempts: parseInt(e.target.value) || 3 })} />
                </div>
                <div>
                  <label className="form-label">Initial Delay (ms)</label>
                  <input type="number" className="form-input" min="0" required
                    value={formData.initialDelayMs} onChange={e => setFormData({ ...formData, initialDelayMs: parseInt(e.target.value) || 1000 })} />
                </div>
              </div>

              {formData.strategy === 'EXPONENTIAL' && (
                <div>
                  <label className="form-label">Backoff Multiplier</label>
                  <input type="number" step="0.1" className="form-input" min="1" required
                    value={formData.multiplier} onChange={e => setFormData({ ...formData, multiplier: parseFloat(e.target.value) || 2.0 })} />
                </div>
              )}

              {formData.strategy !== 'FIXED' && (
                <div>
                  <label className="form-label">Max Delay (ms)</label>
                  <input type="number" className="form-input" min="0" required
                    value={formData.maxDelayMs} onChange={e => setFormData({ ...formData, maxDelayMs: parseInt(e.target.value) || 300000 })} />
                </div>
              )}

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '8px', borderTop: '1px solid var(--border-color)', paddingTop: '16px' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setIsModalOpen(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary">{editingPolicy ? 'Save Changes' : 'Create Policy'}</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
