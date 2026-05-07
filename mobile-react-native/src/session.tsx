import React, { createContext, useContext, useEffect, useState } from 'react';
import * as SecureStore from 'expo-secure-store';

export interface Session {
  userId: string;
  username: string;
  email: string;
}

interface SessionCtx {
  session: Session | null;
  loading: boolean;
  login: (s: Session) => Promise<void>;
  logout: () => Promise<void>;
}

const Ctx = createContext<SessionCtx>({
  session: null,
  loading: true,
  login: async () => {},
  logout: async () => {},
});

const KEY = 'session.v1';

export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    SecureStore.getItemAsync(KEY)
      .then((raw) => {
        if (raw) {
          try {
            setSession(JSON.parse(raw) as Session);
          } catch {}
        }
      })
      .finally(() => setLoading(false));
  }, []);

  const login = async (s: Session) => {
    setSession(s);
    await SecureStore.setItemAsync(KEY, JSON.stringify(s));
  };

  const logout = async () => {
    setSession(null);
    await SecureStore.deleteItemAsync(KEY);
  };

  return <Ctx.Provider value={{ session, loading, login, logout }}>{children}</Ctx.Provider>;
}

export const useSession = () => useContext(Ctx);
