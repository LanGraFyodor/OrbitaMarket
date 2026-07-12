import { useCallback, useEffect, useRef, useState } from "react";
import {
  Bell,
  CheckCheck,
  CircleAlert,
  CreditCard,
  Radio,
  X,
} from "lucide-react";
import { api, notificationStream, type NotificationItem } from "./api";

export function NotificationCenter({
  userId,
  token,
}: {
  userId: string;
  token: string;
}) {
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [open, setOpen] = useState(false);
  const [live, setLive] = useState(false);
  const reconnect = useRef<number | null>(null);
  const unread = items.filter((item) => !item.is_read).length;

  const load = useCallback(async () => {
    try {
      setItems(await api.notifications(userId));
    } catch {
      setLive(false);
    }
  }, [userId]);

  useEffect(() => {
    void load();
  }, [load]);
  useEffect(() => {
    const controller = new AbortController();
    const connect = async () => {
      try {
        const response = await notificationStream(
          userId,
          token,
          controller.signal,
        );
        if (!response.ok || !response.body) throw new Error("SSE unavailable");
        setLive(true);
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";
        while (!controller.signal.aborted) {
          const { value, done } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          const events = buffer.split("\n\n");
          buffer = events.pop() || "";
          for (const event of events) {
            if (!event.includes("event:notification")) continue;
            const data = event
              .split("\n")
              .find((line) => line.startsWith("data:"));
            if (data) {
              const item = JSON.parse(data.slice(5)) as NotificationItem;
              setItems((current) => [
                item,
                ...current.filter((value) => value.id !== item.id),
              ]);
            }
          }
        }
      } catch {
        if (!controller.signal.aborted) {
          setLive(false);
          reconnect.current = window.setTimeout(connect, 3000);
        }
      }
    };
    void connect();
    return () => {
      controller.abort();
      if (reconnect.current) window.clearTimeout(reconnect.current);
    };
  }, [userId, token]);

  const markRead = async (item: NotificationItem) => {
    if (!item.is_read) await api.readNotification(userId, item.id);
    setItems((current) =>
      current.map((value) =>
        value.id === item.id ? { ...value, is_read: true } : value,
      ),
    );
  };
  const markAll = async () => {
    await api.readAllNotifications(userId);
    setItems((current) => current.map((item) => ({ ...item, is_read: true })));
  };

  return (
    <div className="notification-center">
      <button
        className="icon-button notification-trigger"
        onClick={() => setOpen(!open)}
      >
        <Bell size={18} />
        {unread > 0 && <b>{unread > 9 ? "9+" : unread}</b>}
      </button>
      {open && (
        <div className="notification-popover">
          <div className="notification-head">
            <div>
              <span>
                <i className={live ? "live" : ""} />{" "}
                {live ? "LIVE CHANNEL" : "RECONNECTING"}
              </span>
              <h3>Уведомления</h3>
            </div>
            <button onClick={() => setOpen(false)}>
              <X />
            </button>
          </div>
          <div className="notification-actions">
            <span>{unread} непрочитанных</span>
            <button onClick={markAll}>
              <CheckCheck /> Прочитать все
            </button>
          </div>
          <div className="notification-list">
            {items.length === 0 ? (
              <div className="notification-empty">
                <Radio />
                <strong>Канал подключён</strong>
                <span>События оплаты появятся здесь мгновенно.</span>
              </div>
            ) : (
              items.map((item) => (
                <button
                  className={`notification-item ${item.is_read ? "read" : ""}`}
                  key={item.id}
                  onClick={() => void markRead(item)}
                >
                  <i>
                    {item.type === "PAYMENT_COMPLETED" ? (
                      <CreditCard />
                    ) : (
                      <CircleAlert />
                    )}
                  </i>
                  <span>
                    <strong>{item.title}</strong>
                    <small>{item.message}</small>
                    <time>
                      {new Date(item.created_at).toLocaleString("ru-RU")}
                    </time>
                  </span>
                  {!item.is_read && <b />}
                </button>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
