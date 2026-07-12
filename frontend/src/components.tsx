import {
  Check,
  CircleAlert,
  Clock3,
  LoaderCircle,
  Radio,
  X,
} from "lucide-react";
import type { OrderStatus } from "./api";

const statusMeta: Record<OrderStatus, { label: string; icon: typeof Check }> = {
  CREATED: { label: "Зарегистрирован", icon: Radio },
  PAYMENT_PENDING: { label: "Оплата в сети", icon: LoaderCircle },
  PAID: { label: "Оплачен", icon: Check },
  PAYMENT_FAILED: { label: "Ошибка оплаты", icon: CircleAlert },
  REJECTED: { label: "Отклонён", icon: X },
};

export function StatusBadge({ status }: { status: OrderStatus }) {
  const meta = statusMeta[status];
  const Icon = meta.icon;
  return (
    <span className={`status status--${status.toLowerCase()}`}>
      <Icon size={13} strokeWidth={2.4} />
      {meta.label}
    </span>
  );
}

export function OrbitalMap({ compact = false }: { compact?: boolean }) {
  return (
    <div
      className={`orbital-map ${compact ? "orbital-map--compact" : ""}`}
      aria-label="Зона спутникового мониторинга"
    >
      <div className="map-grid" />
      <div className="map-glow" />
      <svg viewBox="0 0 800 430" role="img" aria-label="Карта зоны интереса">
        <defs>
          <filter id="glow">
            <feGaussianBlur stdDeviation="4" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
          <linearGradient id="land" x1="0" y1="0" x2="1" y2="1">
            <stop stopColor="#283936" />
            <stop offset="1" stopColor="#101c1d" />
          </linearGradient>
        </defs>
        <path
          className="map-land"
          d="M0 88L64 68l60 11 39-35 77 18 36 53 64 5 48 56 82 5 68-26 85 23 53 61-12 48-62 18-47 75-91 12-55-52-74 4-66-31-60 23-75-12-41-53-91-9z"
        />
        <path
          className="map-contour"
          d="M13 112c86-9 103 38 177 19s124 26 207 24 121 46 184 22 148 13 206 43M34 281c85-44 174 6 234-16s109 4 168 35 121-16 209 6 109 32 145 15"
        />
        <path className="orbit orbit-a" d="M-40 334C172 85 504 14 854 90" />
        <path className="orbit orbit-b" d="M-50 395C204 141 544 59 842 142" />
        <polygon
          className="aoi"
          points="344,137 484,157 520,260 398,300 310,229"
        />
        <g className="satellite" transform="translate(562 105) rotate(-12)">
          <rect x="-18" y="-8" width="36" height="16" rx="3" />
          <path d="M-53-12h31V12h-31M22-12h31V12H22M0-8V-26" />
        </g>
        <circle className="signal" cx="562" cy="105" r="5" />
      </svg>
      <div className="map-coordinates">
        <span>55.7558° N</span>
        <span>37.6173° E</span>
      </div>
      <div className="map-live">
        <i /> LIVE ORBIT
      </div>
      {!compact && (
        <div className="map-caption">
          <span>AOI–06</span>
          <strong>Центральный ФО</strong>
          <small>184 620 км² · облачность 8%</small>
        </div>
      )}
    </div>
  );
}

export function EmptyOrders() {
  return (
    <div className="empty">
      <Clock3 size={24} />
      <strong>Пока нет заказов</strong>
      <span>Новая миссия появится здесь после отправки.</span>
    </div>
  );
}
