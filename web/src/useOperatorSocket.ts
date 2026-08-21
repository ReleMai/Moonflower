import { useEffect, useRef, useState } from 'react';
import { getApiBase, getOperatorToken } from './api';
import type { OperatorEvent } from './types';

export function useOperatorSocket(enabled: boolean, onEvent?: (event: OperatorEvent) => void) {
  const [event, setEvent] = useState<OperatorEvent | null>(null);
  const [connected, setConnected] = useState(false);
  const onEventRef = useRef(onEvent);

  useEffect(() => {
    onEventRef.current = onEvent;
  }, [onEvent]);

  useEffect(() => {
    if (!enabled) {
      return;
    }

    const token = getOperatorToken();
    if (!token) {
      return;
    }

    const base = new URL(getApiBase());
    const protocol = base.protocol === 'https:' ? 'wss:' : 'ws:';
    const ws = new WebSocket(`${protocol}//${base.host}/ws/operator?token=${token}`);
    let active = true;

    ws.onopen = () => {
      if (active) setConnected(true);
    };
    ws.onclose = () => {
      if (active) setConnected(false);
    };
    ws.onerror = () => {
      if (active) setConnected(false);
    };
    ws.onmessage = (message) => {
      if (!active) return;
      let nextEvent: OperatorEvent;
      try {
        nextEvent = JSON.parse(message.data) as OperatorEvent;
      } catch {
        nextEvent = { type: 'parse-error', payload: message.data };
      }
      setEvent(nextEvent);
      onEventRef.current?.(nextEvent);
    };

    return () => {
      active = false;
      ws.close();
    };
  }, [enabled]);

  return { event, connected: enabled && connected };
}
