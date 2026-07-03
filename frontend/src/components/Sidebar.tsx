import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard, Layers, Briefcase, Radio, Skull, LogOut, RotateCcw, Zap, Sun, Moon, ChevronLeft, ChevronRight
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useApp } from '../context/AppContext';

export default function Sidebar() {
  const { user, logout } = useAuth();
  const { theme, toggleTheme, isSidebarCollapsed, setSidebarCollapsed } = useApp();

  const getInitials = (email: string) => {
    const name = email.split('@')[0];
    return name.substring(0, 2).toUpperCase();
  };

  return (
    <aside className={`sidebar ${isSidebarCollapsed ? 'collapsed' : ''}`}>
      <div className="sidebar-brand">
        <div className="sidebar-brand-icon">
          <Zap size={18} />
        </div>
        {!isSidebarCollapsed && (
          <div className="brand-text">
            <h1>JobScheduler</h1>
            <span>Distributed Engine</span>
          </div>
        )}
        <button
          className="sidebar-collapse-btn"
          onClick={() => setSidebarCollapsed(!isSidebarCollapsed)}
          title={isSidebarCollapsed ? "Expand Sidebar" : "Collapse Sidebar"}
        >
          {isSidebarCollapsed ? <ChevronRight size={14} /> : <ChevronLeft size={14} />}
        </button>
      </div>

      <nav className="sidebar-nav">
        {!isSidebarCollapsed && <span className="sidebar-section-label">Overview</span>}
        <NavLink to="/" end className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
          <LayoutDashboard size={18} />
          {!isSidebarCollapsed && <span>Dashboard</span>}
        </NavLink>

        {!isSidebarCollapsed && <span className="sidebar-section-label">Management</span>}
        <NavLink to="/queues" className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
          <Layers size={18} />
          {!isSidebarCollapsed && <span>Queues</span>}
        </NavLink>
        <NavLink to="/jobs" className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
          <Briefcase size={18} />
          {!isSidebarCollapsed && <span>Jobs</span>}
        </NavLink>
        <NavLink to="/workers" className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
          <Radio size={18} />
          {!isSidebarCollapsed && <span>Workers</span>}
        </NavLink>
        <NavLink to="/dlq" className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
          <Skull size={18} />
          {!isSidebarCollapsed && <span>Dead Letter Queue</span>}
        </NavLink>

        {!isSidebarCollapsed && <span className="sidebar-section-label">Configuration</span>}
        <NavLink to="/policies" className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
          <RotateCcw size={18} />
          {!isSidebarCollapsed && <span>Retry Policies</span>}
        </NavLink>
      </nav>

      {/* Footer Area */}
      <div className="sidebar-footer">
        {/* Theme Toggle */}
        {isSidebarCollapsed ? (
          <button
            className="theme-toggle-compact"
            onClick={toggleTheme}
            title={`Switch to ${theme === 'light' ? 'dark' : 'light'} mode`}
          >
            {theme === 'light' ? <Moon size={16} /> : <Sun size={16} />}
          </button>
        ) : (
          <div className="theme-toggle-container">
            <button
              className={`theme-btn ${theme === 'light' ? 'active' : ''}`}
              onClick={() => theme !== 'light' && toggleTheme()}
            >
              <Sun size={13} />
              <span>Light</span>
            </button>
            <button
              className={`theme-btn ${theme === 'dark' ? 'active' : ''}`}
              onClick={() => theme !== 'dark' && toggleTheme()}
            >
              <Moon size={13} />
              <span>Dark</span>
            </button>
          </div>
        )}

        {/* User profile */}
        {user && (
          <div className="sidebar-user">
            <div className="sidebar-user-info">
              <div className="sidebar-avatar">
                {getInitials(user.email)}
              </div>
              {!isSidebarCollapsed && (
                <div style={{ minWidth: 0, flex: 1 }}>
                  <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-primary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {user.email}
                  </div>
                  <div style={{ fontSize: '10px', color: 'var(--text-muted)' }}>
                    {user.organizationName || 'Default Org'}
                  </div>
                </div>
              )}
            </div>
            {isSidebarCollapsed ? (
              <button className="btn-icon-logout" onClick={logout} title="Sign Out">
                <LogOut size={16} />
              </button>
            ) : (
              <button className="btn btn-ghost btn-sm" onClick={logout} style={{ width: '100%', justifyContent: 'center', marginTop: '8px', gap: '6px' }}>
                <LogOut size={14} /> Sign Out
              </button>
            )}
          </div>
        )}
      </div>
    </aside>
  );
}
