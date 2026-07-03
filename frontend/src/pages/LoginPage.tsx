import { useState, FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { authApi } from '../api/client';
import { Zap, ArrowRight, Loader2 } from 'lucide-react';

export default function LoginPage() {
  const [isRegister, setIsRegister] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = isRegister
        ? await authApi.register({ email, password, fullName })
        : await authApi.login({ email, password });
      login(res.data);
      navigate('/');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Authentication failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-container">
      <div className="login-card animate-in">
        <div className="login-header">
          <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '20px' }}>
            <div className="sidebar-brand-icon" style={{ width: 56, height: 56, fontSize: 20 }}>
              <Zap size={28} />
            </div>
          </div>
          <h1>{isRegister ? 'Create Account' : 'Welcome Back'}</h1>
          <p>{isRegister ? 'Set up your job scheduler account' : 'Sign in to your distributed job scheduler'}</p>
        </div>

        <form onSubmit={handleSubmit}>
          {isRegister && (
            <div className="form-group">
              <label className="form-label">Full Name</label>
              <input className="form-input" type="text" value={fullName}
                onChange={(e) => setFullName(e.target.value)} placeholder="John Doe" required />
            </div>
          )}
          <div className="form-group">
            <label className="form-label">Email</label>
            <input className="form-input" type="email" value={email}
              onChange={(e) => setEmail(e.target.value)} placeholder="you@company.com" required />
          </div>
          <div className="form-group">
            <label className="form-label">Password</label>
            <input className="form-input" type="password" value={password}
              onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" required minLength={8} />
          </div>

          {error && (
            <div style={{
              color: 'var(--error)',
              fontSize: '13px',
              marginBottom: '16px',
              textAlign: 'center',
              padding: '8px 12px',
              background: 'var(--error-bg)',
              borderRadius: 'var(--radius-sm)',
              border: '1px solid rgba(239, 68, 68, 0.15)'
            }}>
              {error}
            </div>
          )}

          <button className="btn btn-primary login-btn" type="submit" disabled={loading}>
            {loading ? (
              <Loader2 size={18} style={{ animation: 'spin 0.7s linear infinite' }} />
            ) : (
              <>
                {isRegister ? 'Create Account' : 'Sign In'}
                <ArrowRight size={16} />
              </>
            )}
          </button>
        </form>

        <div className="login-toggle">
          {isRegister ? 'Already have an account? ' : "Don't have an account? "}
          <a href="#" onClick={(e) => { e.preventDefault(); setIsRegister(!isRegister); setError(''); }}>
            {isRegister ? 'Sign In' : 'Create Account'}
          </a>
        </div>
      </div>
    </div>
  );
}
