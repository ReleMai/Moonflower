import { useEffect, useState } from 'react';
import { getApiBase, getOperatorToken } from './api';
import type { OperatorEvent } from './types';

export function useOperatorSocket(enabled: boolean) {
  const [event, setEvent] = useState<OperatorEvent | null>(null);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    if (!enabled) {
      setConnected(false);
      return;
    }

    const token = getOperatorToken();
    if (!token) {
      return;
    }

    const base = new URL(getApiBase());
    const protocol = base.protocol === 'https:' ? 'wss:' : 'ws:';
    const ws = new WebSocket(`${protocol}//${base.host}/ws/operator?token=${token}`);

    ws.onopen = () => setConnected(true);
    ws.onclose = () => setConnected(false);
    ws.onerror = () => setConnected(false);
    ws.onmessage = (message) => {
      try {
        setEvent(JSON.parse(message.data) as OperatorEvent);
      } catch {
        setEvent({ type: 'parse-error', payload: message.data });
      }
    };

    return () => ws.close();
  }, [enabled]);

  return { event, connected };
}

