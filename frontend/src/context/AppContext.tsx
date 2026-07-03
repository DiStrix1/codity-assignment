import React, { createContext, useContext, useState, ReactNode } from 'react';

type Theme = 'dark' | 'light';

interface AppContextType {
    theme: Theme;
    toggleTheme: () => void;
    isSidebarCollapsed: boolean;
    setSidebarCollapsed: (value: boolean) => void;
}

const AppContext = createContext<AppContextType | undefined>(undefined);

export function AppProvider({ children }: { children: ReactNode }) {
    const [theme, setTheme] = useState<Theme>('dark');
    const [isSidebarCollapsed, setSidebarCollapsed] = useState<boolean>(false);

    const toggleTheme = () => {
        setTheme(prev => (prev === 'dark' ? 'light' : 'dark'));
    };

    return (
        <AppContext.Provider
            value={{
                theme,
                toggleTheme,
                isSidebarCollapsed,
                setSidebarCollapsed,
            }}
        >
            {children}
        </AppContext.Provider>
    );
}

export function useApp() {
    const context = useContext(AppContext);
    if (!context) {
        throw new Error('useApp must be used within an AppProvider');
    }
    return context;
}
