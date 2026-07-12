import {
  lazy,
  Suspense,
  useCallback,
  useEffect,
  useMemo,
  useState,
} from "react";
import {
  Activity,
  ArrowDownRight,
  ArrowRight,
  ArrowUpRight,
  Camera,
  ChevronDown,
  CircleDollarSign,
  Command,
  Copy,
  Crosshair,
  Database,
  ExternalLink,
  Eye,
  Gauge,
  Hexagon,
  Layers3,
  Menu,
  Plus,
  RefreshCw,
  Satellite,
  Search,
  Settings2,
  ShieldCheck,
  Sparkles,
  WalletCards,
  X,
} from "lucide-react";
import {
  api,
  type AuthSession,
  type Balance,
  type Order,
  type OrderDraft,
  type ProductType,
} from "./api";
import { EmptyOrders, OrbitalMap, StatusBadge } from "./components";
import { AuthScreen, ProfileModal } from "./AuthViews";
import { NotificationCenter } from "./NotificationCenter";
import type { CapturedFrame } from "./SatelliteMissionMap";
import {
  frameFromBlob,
  hasCapturedFrame,
  loadCapturedFrame,
  saveCapturedFrame,
} from "./frameStorage";

const SatelliteMissionMap = lazy(() =>
  import("./SatelliteMissionMap").then((module) => ({
    default: module.SatelliteMissionMap,
  })),
);

const SESSION_KEY = "orbitamarket-session";

const products: Array<{
  type: ProductType;
  code: string;
  title: string;
  hint: string;
  price: number;
}> = [
  {
    type: "ARCHIVE",
    code: "01",
    title: "Архив",
    hint: "Готовый снимок из каталога",
    price: 120,
  },
  {
    type: "TASKING",
    code: "02",
    title: "Съёмка",
    hint: "Новая задача спутнику",
    price: 420,
  },
  {
    type: "MONITORING",
    code: "03",
    title: "Мониторинг",
    hint: "Регулярный контроль зоны",
    price: 890,
  },
];

const formatCredits = (value: number) =>
  new Intl.NumberFormat("ru-RU").format(value);
const shortId = (value: string) => `${value.slice(0, 6)}…${value.slice(-4)}`;

const requestFromWkt = (value: unknown) => {
  if (typeof value !== "string") return null;
  const match = value.match(/^POLYGON\(\((.+)\)\)$/i);
  if (!match) return null;
  const ring = match[1]
    .split(",")
    .map((pair) => pair.trim().split(/\s+/).map(Number));
  if (
    ring.length < 4 ||
    ring.some(
      ([longitude, latitude]) =>
        !Number.isFinite(longitude) || !Number.isFinite(latitude),
    )
  )
    return null;
  const longitudes = ring.map(([longitude]) => longitude);
  const latitudes = ring.map(([, latitude]) => latitude);
  return {
    bbox: [
      Math.min(...longitudes),
      Math.min(...latitudes),
      Math.max(...longitudes),
      Math.max(...latitudes),
    ] as [number, number, number, number],
    ring,
  };
};

const clipSnapshotToAoi = async (
  source: Blob,
  bbox: [number, number, number, number],
  ring: number[][],
) => {
  const bitmap = await createImageBitmap(source);
  const canvas = document.createElement("canvas");
  canvas.width = bitmap.width;
  canvas.height = bitmap.height;
  const context = canvas.getContext("2d");
  if (!context) return source;
  const [west, south, east, north] = bbox;
  const mercatorY = (latitude: number) => {
    const radians =
      (Math.max(-85.05112878, Math.min(85.05112878, latitude)) * Math.PI) / 180;
    return Math.log(Math.tan(Math.PI / 4 + radians / 2));
  };
  const northY = mercatorY(north);
  const southY = mercatorY(south);
  context.beginPath();
  ring.forEach(([lng, lat], index) => {
    const x = ((lng - west) / (east - west)) * canvas.width;
    const y = ((northY - mercatorY(lat)) / (northY - southY)) * canvas.height;
    if (index === 0) context.moveTo(x, y);
    else context.lineTo(x, y);
  });
  context.closePath();
  context.clip();
  context.drawImage(bitmap, 0, 0);
  bitmap.close();
  return new Promise<Blob>((resolve) =>
    canvas.toBlob((result) => resolve(result ?? source), "image/png"),
  );
};

function Dashboard({
  session,
  onSessionChange,
  onLogout,
}: {
  session: AuthSession;
  onSessionChange: (session: AuthSession) => void;
  onLogout: () => void;
}) {
  const userId = session.profile.user_id;
  const [balance, setBalance] = useState<Balance | null>(null);
  const [orders, setOrders] = useState<Order[]>([]);
  const [product, setProduct] = useState<ProductType>("ARCHIVE");
  const [connected, setConnected] = useState(false);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [topUpOpen, setTopUpOpen] = useState(false);
  const [topUpAmount, setTopUpAmount] = useState(1000);
  const [toast, setToast] = useState<string | null>(null);
  const [mobileNav, setMobileNav] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [aoiWkt, setAoiWkt] = useState("");
  const [aoiGeoJson, setAoiGeoJson] = useState<object | null>(null);
  const [aoiValid, setAoiValid] = useState(false);
  const [resolutionM, setResolutionM] = useState(0.8);
  const [geoAnalysis, setGeoAnalysis] = useState<Awaited<
    ReturnType<typeof api.analyzeGeoJson>
  > | null>(null);
  const [previewFrame, setPreviewFrame] = useState<CapturedFrame | null>(null);

  const selected = products.find((item) => item.type === product)!;
  const paidCount = orders.filter((item) => item.status === "PAID").length;
  const calculatedPrice = geoAnalysis?.price_geocredits ?? null;

  useEffect(() => {
    if (!aoiGeoJson || !aoiValid) {
      setGeoAnalysis(null);
      return;
    }
    const timer = window.setTimeout(() => {
      api
        .analyzeGeoJson(userId, aoiGeoJson, product, resolutionM)
        .then(setGeoAnalysis)
        .catch(() => setGeoAnalysis(null));
    }, 250);
    return () => window.clearTimeout(timer);
  }, [aoiGeoJson, aoiValid, product, resolutionM, userId]);

  const notify = (message: string) => {
    setToast(message);
    window.setTimeout(() => setToast(null), 3200);
  };

  const openOrderProduct = async (order: Order) => {
    if (order.status === "PAID") {
      const stored = await loadCapturedFrame(order.order_id).catch(() => null);
      if (stored) {
        setPreviewFrame(stored);
        return;
      }
      const request = requestFromWkt(order.payload.aoi);
      if (!request) {
        notify("В заказе отсутствует корректный AOI");
        return;
      }
      notify("Rust формирует спутниковый снимок выбранной территории…");
      try {
        const blob = await api.createSnapshot(userId, request.bbox);
        const productBlob = await clipSnapshotToAoi(
          blob,
          request.bbox,
          request.ring,
        );
        const [west, south, east, north] = request.bbox;
        const metadata: Omit<CapturedFrame, "image"> = {
          captured_at: new Date().toISOString(),
          center: [(west + east) / 2, (south + north) / 2],
          zoom: 15,
          source: "Esri World Imagery / Rust Fulfillment",
        };
        const frame = await saveCapturedFrame(
          order.order_id,
          productBlob,
          metadata,
        ).catch(() => frameFromBlob(productBlob, metadata));
        setPreviewFrame(frame);
        return;
      } catch (error) {
        notify(
          error instanceof Error
            ? error.message
            : "Не удалось сформировать спутниковый снимок",
        );
        return;
      }
    }
    navigator.clipboard?.writeText(order.order_id);
    notify("ID миссии скопирован — продукт появится после оплаты");
  };

  const refresh = useCallback(
    async (silent = false) => {
      if (!silent) setLoading(true);
      try {
        await api.createAccount(userId);
        const [nextBalance, nextOrders] = await Promise.all([
          api.balance(userId),
          api.orders(userId),
        ]);
        setBalance(nextBalance);
        setOrders(nextOrders);
        setConnected(true);
      } catch {
        setConnected(false);
      } finally {
        setLoading(false);
      }
    },
    [userId],
  );

  useEffect(() => {
    void refresh();
  }, [refresh]);
  useEffect(() => {
    const pending = orders.some(
      (item) => item.status === "CREATED" || item.status === "PAYMENT_PENDING",
    );
    if (!pending) return;
    const timer = window.setInterval(() => void refresh(true), 1600);
    return () => window.clearInterval(timer);
  }, [orders, refresh]);

  useEffect(() => {
    const prepareProducts = async () => {
      for (const order of orders.filter((value) => value.status === "PAID")) {
        const requestKey = `orbitamarket-capture-v2-${order.order_id}`;
        if (await hasCapturedFrame(order.order_id).catch(() => false)) continue;
        const stored = localStorage.getItem(requestKey);
        try {
          const request = stored
            ? (JSON.parse(stored) as {
                bbox: [number, number, number, number];
                ring?: number[][];
              })
            : requestFromWkt(order.payload.aoi);
          if (!request) continue;
          const blob = await api.createSnapshot(userId, request.bbox);
          const productBlob = request.ring
            ? await clipSnapshotToAoi(blob, request.bbox, request.ring)
            : blob;
          const [west, south, east, north] = request.bbox;
          const metadata: Omit<CapturedFrame, "image"> = {
            captured_at: new Date().toISOString(),
            center: [(west + east) / 2, (south + north) / 2],
            zoom: 15,
            source: "Esri World Imagery / Rust Fulfillment",
          };
          await saveCapturedFrame(order.order_id, productBlob, metadata);
          localStorage.removeItem(requestKey);
          notify(`Спутниковый продукт ${shortId(order.order_id)} готов`);
        } catch {
          // Rust fulfillment will retry on the next status refresh.
        }
      }
    };
    void prepareProducts();
  }, [orders, userId]);

  const handleTopUp = async () => {
    try {
      const next = await api.topUp(userId, topUpAmount);
      setBalance(next);
      setTopUpOpen(false);
      notify(`Зачислено ${formatCredits(topUpAmount)} GC`);
    } catch (error) {
      notify(
        error instanceof Error ? error.message : "Не удалось пополнить счёт",
      );
    }
  };

  const payload = useMemo<Record<string, unknown>>(() => {
    const aoi = aoiWkt;
    if (product === "TASKING")
      return {
        aoi,
        time_window: {
          from: "2026-07-15T08:00:00Z",
          to: "2026-07-16T18:00:00Z",
        },
        sensor_type: "MSI",
      };
    if (product === "MONITORING")
      return { aoi, cadence: "WEEKLY", duration_days: 30 };
    return { aoi, capture_date: "2026-06-28", sensor_type: "MSI" };
  }, [product, aoiWkt, resolutionM]);

  const handleOrder = async () => {
    if (!geoAnalysis || !aoiValid || calculatedPrice === null) {
      notify("Сначала выделите и подтвердите территорию на карте");
      return;
    }
    setSubmitting(true);
    const draft: OrderDraft = {
      product_type: product,
      price: calculatedPrice,
      payload: {
        ...payload,
        quote_id: geoAnalysis?.quote_id,
        resolution_m: resolutionM,
        area_sq_km: geoAnalysis.area_sq_km,
        bbox: geoAnalysis.bbox,
      },
    };
    try {
      const created = await api.createOrder(userId, draft);
      setOrders((current) => [created, ...current]);
      if (geoAnalysis) {
        localStorage.setItem(
          `orbitamarket-capture-v2-${created.order_id}`,
          JSON.stringify({
            bbox: geoAnalysis.bbox,
            ring: geoAnalysis.normalized_geojson.coordinates[0],
          }),
        );
      }
      notify(`Миссия ${shortId(created.order_id)} отправлена в сеть`);
      window.setTimeout(() => void refresh(true), 1000);
    } catch (error) {
      notify(error instanceof Error ? error.message : "Заказ не создан");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="app-shell">
      <aside className={`sidebar ${mobileNav ? "sidebar--open" : ""}`}>
        <button className="mobile-close" onClick={() => setMobileNav(false)}>
          <X />
        </button>
        <a className="brand" href="#top">
          <span className="brand-mark">
            <i />
            <i />
            <i />
          </span>
          <span>
            ORBITA<strong>MARKET</strong>
          </span>
        </a>
        <div className="course-label">
          БЮРО 1440
          <br />
          <span>INDUSTRIAL LAB</span>
        </div>
        <nav>
          <span className="nav-kicker">Консоль</span>
          <a className="active" href="#overview">
            <Gauge /> Обзор <i />
          </a>
          <a href="#mission">
            <Crosshair /> Новая миссия
          </a>
          <a href="#orders">
            <Layers3 /> Заказы <b>{orders.length}</b>
          </a>
          <a href="#wallet">
            <WalletCards /> Геокредиты
          </a>
          <span className="nav-kicker nav-kicker--second">Система</span>
          <a href="#network">
            <Activity /> Сеть
          </a>
          <a href="#settings">
            <Settings2 /> Настройки
          </a>
        </nav>
        <div className="sidebar-orbit">
          <div className="mini-planet" />
          <span>Контур «Рассвет»</span>
          <strong>LEO / 800 KM</strong>
          <small>следующий пролёт · 04:18</small>
        </div>
        <button className="profile" onClick={() => setProfileOpen(true)}>
          <div className="avatar">
            {session.profile.display_name
              .split(" ")
              .map((part) => part[0])
              .slice(0, 2)
              .join("")
              .toUpperCase()}
          </div>
          <div>
            <strong>{session.profile.display_name}</strong>
            <span>{session.profile.job_title || "Space operations"}</span>
          </div>
          <ChevronDown size={15} />
        </button>
      </aside>

      <main id="top">
        <header className="topbar">
          <button className="menu-button" onClick={() => setMobileNav(true)}>
            <Menu />
          </button>
          <div className="breadcrumb">
            <span>Пространство</span>
            <b>/</b>
            <strong>Операционный центр</strong>
          </div>
          <div className="top-actions">
            <button className="search">
              <Search size={16} />
              <span>Быстрый поиск</span>
              <kbd>⌘ K</kbd>
            </button>
            <NotificationCenter userId={userId} token={session.access_token} />
            <div className={`connection ${connected ? "connection--ok" : ""}`}>
              <i />
              {connected ? "Системы в норме" : "Оффлайн-режим"}
            </div>
          </div>
        </header>

        <div className="content" id="overview">
          <section className="hero">
            <div className="hero-noise" />
            <div className="hero-copy">
              <div className="eyebrow">
                <span>БЮРО 1440 · INDUSTRIAL SOFTWARE</span>
                <i /> УЧЕБНЫЙ ПРОЕКТ
              </div>
              <h1>
                Земля —<br />
                <em>в зоне видимости.</em>
              </h1>
              <p>
                Заказывайте спутниковые данные, управляйте съёмкой и следите за
                территориями в едином контуре.
              </p>
              <div className="hero-actions">
                <a className="primary-button" href="#mission">
                  Создать миссию <ArrowRight size={17} />
                </a>
                <a className="ghost-button" href="#orders">
                  <Eye size={17} /> Открыть заказы
                </a>
              </div>
            </div>
            <div className="hero-orbit">
              <div className="earth" />
              <div className="orbit-line orbit-line--one">
                <i />
              </div>
              <div className="orbit-line orbit-line--two">
                <i />
              </div>
              <div className="hero-data hero-data--top">
                <span>OM–14</span>
                <b>547.8 km</b>
              </div>
              <div className="hero-data hero-data--bottom">
                <span>СЕАНС СВЯЗИ</span>
                <b>98.7%</b>
              </div>
            </div>
            <div className="hero-footer">
              <div>
                <span>Высота орбиты</span>
                <strong>
                  800 <small>км</small>
                </strong>
              </div>
              <div>
                <span>Зон под контролем</span>
                <strong>08</strong>
              </div>
              <div>
                <span>Данных сегодня</span>
                <strong>
                  1.84 <small>TB</small>
                </strong>
              </div>
            </div>
          </section>

          <section className="dashboard-grid">
            <article className="wallet-card" id="wallet">
              <div className="section-head">
                <div>
                  <span className="section-index">01</span>
                  <h2>Баланс геокредитов</h2>
                </div>
                <button className="icon-button" onClick={() => void refresh()}>
                  <RefreshCw size={16} className={loading ? "spin" : ""} />
                </button>
              </div>
              <div className="balance">
                <span>Доступно</span>
                <strong>
                  {balance ? formatCredits(balance.balance) : "—"}{" "}
                  <small>GC</small>
                </strong>
                <div className="balance-trend">
                  <ArrowUpRight size={14} /> +12.4% за период
                </div>
              </div>
              <div className="credit-chart">
                <svg viewBox="0 0 500 100" preserveAspectRatio="none">
                  <defs>
                    <linearGradient id="chartFill" x1="0" y1="0" x2="0" y2="1">
                      <stop stopColor="#d9ff43" stopOpacity=".28" />
                      <stop offset="1" stopColor="#d9ff43" stopOpacity="0" />
                    </linearGradient>
                  </defs>
                  <path
                    className="chart-area"
                    d="M0 83 C45 72,65 78,102 59 S164 70,203 48 S270 55,307 31 S363 41,408 19 S464 25,500 8 V100 H0Z"
                  />
                  <path
                    className="chart-line"
                    d="M0 83 C45 72,65 78,102 59 S164 70,203 48 S270 55,307 31 S363 41,408 19 S464 25,500 8"
                  />
                </svg>
              </div>
              <button
                className="topup-button"
                onClick={() => setTopUpOpen(true)}
              >
                <Plus size={17} /> Пополнить баланс
              </button>
            </article>

            <article className="coverage-card">
              <div className="section-head">
                <div>
                  <span className="section-index">02</span>
                  <h2>Орбитальное покрытие</h2>
                </div>
                <span className="live-pill">
                  <i /> LIVE
                </span>
              </div>
              <OrbitalMap compact />
              <div className="coverage-stats">
                <div>
                  <span>Следующий пролёт</span>
                  <strong>04:18</strong>
                </div>
                <div>
                  <span>Разрешение</span>
                  <strong>0.8 m</strong>
                </div>
                <div>
                  <span>Облачность</span>
                  <strong>8%</strong>
                </div>
              </div>
            </article>

            <article className="activity-card">
              <div className="section-head">
                <div>
                  <span className="section-index">03</span>
                  <h2>Контур данных</h2>
                </div>
                <ExternalLink size={16} />
              </div>
              <div className="pipeline">
                <div className="pipe-node">
                  <Satellite />
                  <span>Спутники</span>
                  <b>24</b>
                </div>
                <i className="pipe-line">
                  <span />
                </i>
                <div className="pipe-node">
                  <Database />
                  <span>Каталог</span>
                  <b>12.8 PB</b>
                </div>
                <i className="pipe-line">
                  <span />
                </i>
                <div className="pipe-node">
                  <ShieldCheck />
                  <span>Доставка</span>
                  <b>99.98%</b>
                </div>
              </div>
              <div className="activity-row">
                <span>
                  <i className="pulse" /> Поток стабилен
                </span>
                <b>842 Mb/s</b>
              </div>
            </article>
          </section>

          <section className="mission-section" id="mission">
            <div className="section-title">
              <span>04 / НОВАЯ МИССИЯ</span>
              <h2>
                Сформировать
                <br />
                заказ данных
              </h2>
              <p>
                Выберите продукт, укажите территорию и отправьте задачу в
                орбитальный контур.
              </p>
            </div>
            <div className="mission-builder">
              <div className="product-selector">
                {products.map((item) => (
                  <button
                    key={item.type}
                    className={product === item.type ? "selected" : ""}
                    onClick={() => setProduct(item.type)}
                  >
                    <span>{item.code}</span>
                    <div>
                      <strong>{item.title}</strong>
                      <small>{item.hint}</small>
                    </div>
                    <i />
                  </button>
                ))}
              </div>
              <div className="mission-map">
                <Suspense
                  fallback={
                    <div className="map-loading">
                      <Satellite />
                      <strong>Подключаем орбитальную карту</strong>
                      <span>WebGL / World Imagery</span>
                    </div>
                  }
                >
                  <SatelliteMissionMap
                    onAoiChange={(wkt, geojson) => {
                      setAoiWkt(wkt);
                      setAoiGeoJson(geojson.geometry);
                    }}
                    onValidityChange={(valid) => {
                      setAoiValid(valid);
                      if (!valid) {
                        setAoiGeoJson(null);
                        setAoiWkt("");
                        setGeoAnalysis(null);
                      }
                    }}
                  />
                </Suspense>
              </div>
              <div className="mission-summary">
                <div className="summary-kicker">
                  <Sparkles size={15} /> Параметры миссии
                </div>
                <h3>{selected.title}</h3>
                <p>{selected.hint}</p>
                <dl>
                  <div>
                    <dt>Сенсор</dt>
                    <dd>
                      {product === "MONITORING" ? "MULTI" : "MSI / Optical"}
                    </dd>
                  </div>
                  <div>
                    <dt>Разрешение</dt>
                    <dd>
                      <select
                        className="resolution-select"
                        value={resolutionM}
                        onChange={(event) =>
                          setResolutionM(Number(event.target.value))
                        }
                      >
                        <option value={0.3}>
                          0.3 м · максимальная детализация
                        </option>
                        <option value={0.5}>0.5 м · высокая детализация</option>
                        <option value={0.8}>0.8 м · стандарт</option>
                        <option value={1.5}>1.5 м · обзорная</option>
                        <option value={3}>3 м · экономичная</option>
                      </select>
                    </dd>
                  </div>
                  <div>
                    <dt>Территория</dt>
                    <dd>
                      {geoAnalysis
                        ? `${formatCredits(Math.round(geoAnalysis.area_sq_km))} км²`
                        : aoiValid
                          ? "Расчёт Rust…"
                          : "Выберите AOI"}
                    </dd>
                  </div>
                  <div>
                    <dt>{product === "MONITORING" ? "Период" : "Доставка"}</dt>
                    <dd>
                      {product === "MONITORING" ? "30 дней" : "до 24 часов"}
                    </dd>
                  </div>
                </dl>
                <div className="price-row">
                  <span>Стоимость</span>
                  <strong>
                    {calculatedPrice === null
                      ? "—"
                      : formatCredits(calculatedPrice)}{" "}
                    <small>GC</small>
                  </strong>
                </div>
                <div className="rust-quote">
                  <span>RUST GEO ENGINE</span>
                  <b>
                    {geoAnalysis
                      ? `QUOTE ${geoAnalysis.quote_id.slice(0, 8).toUpperCase()}`
                      : "VALIDATING AOI"}
                  </b>
                </div>
                <button
                  className="order-button"
                  disabled={
                    submitting || !connected || !aoiValid || !geoAnalysis
                  }
                  onClick={handleOrder}
                >
                  {submitting
                    ? "Передача в сеть…"
                    : !aoiValid
                      ? "Сначала выберите AOI"
                      : !geoAnalysis
                        ? "Rust рассчитывает цену…"
                        : "Отправить миссию"}
                  <ArrowUpRight size={18} />
                </button>
                {!connected && (
                  <small className="offline-hint">
                    Запустите backend, чтобы оформить заказ
                  </small>
                )}
              </div>
            </div>
          </section>

          <section className="orders-section" id="orders">
            <div className="orders-heading">
              <div>
                <span>05 / ЖУРНАЛ ОПЕРАЦИЙ</span>
                <h2>Последние заказы</h2>
              </div>
              <div className="order-metrics">
                <div>
                  <small>Всего</small>
                  <strong>{orders.length}</strong>
                </div>
                <div>
                  <small>Выполнено</small>
                  <strong>{paidCount}</strong>
                </div>
              </div>
            </div>
            <div className="orders-table">
              <div className="table-head">
                <span>Миссия</span>
                <span>Продукт</span>
                <span>Создана</span>
                <span>Стоимость</span>
                <span>Статус</span>
                <span />
              </div>
              {orders.length === 0 ? (
                <EmptyOrders />
              ) : (
                orders.slice(0, 8).map((order) => (
                  <div className="table-row" key={order.order_id}>
                    <span className="order-id">
                      <Hexagon size={18} />
                      <span>
                        <b>OM–{order.order_id.slice(0, 6).toUpperCase()}</b>
                        <small>{shortId(order.order_id)}</small>
                      </span>
                    </span>
                    <span>
                      {products.find((item) => item.type === order.product_type)
                        ?.title || order.product_type}
                    </span>
                    <span>
                      {new Date(order.created_at).toLocaleDateString("ru-RU", {
                        day: "2-digit",
                        month: "short",
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </span>
                    <span className="order-price">
                      {formatCredits(order.price)} GC
                    </span>
                    <span>
                      <StatusBadge status={order.status} />
                    </span>
                    <button
                      className="icon-button"
                      title={
                        order.status === "PAID"
                          ? "Открыть спутниковый продукт"
                          : "Скопировать ID"
                      }
                      onClick={() => openOrderProduct(order)}
                    >
                      {order.status === "PAID" ? (
                        <Camera size={15} />
                      ) : (
                        <Copy size={15} />
                      )}
                    </button>
                  </div>
                ))
              )}
            </div>
          </section>

          <footer>
            <div className="brand brand--footer">
              <span className="brand-mark">
                <i />
                <i />
                <i />
              </span>
              <span>
                ORBITA<strong>MARKET</strong>
              </span>
            </div>
            <p>Учебный проект программы БЮРО 1440 / 2026</p>
            <div>
              <span className="pulse" /> Все системы работают штатно
            </div>
          </footer>
        </div>
      </main>

      {topUpOpen && (
        <div className="modal-backdrop" onMouseDown={() => setTopUpOpen(false)}>
          <div
            className="modal"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <button className="modal-close" onClick={() => setTopUpOpen(false)}>
              <X />
            </button>
            <div className="modal-icon">
              <CircleDollarSign />
            </div>
            <span className="modal-kicker">ФИНАНСОВЫЙ КОНТУР</span>
            <h2>Пополнить баланс</h2>
            <p>Геокредиты будут доступны сразу после подтверждения операции.</p>
            <label>
              Количество GC
              <input
                type="number"
                min="1"
                value={topUpAmount}
                onChange={(event) => setTopUpAmount(Number(event.target.value))}
              />
            </label>
            <div className="quick-amounts">
              {[500, 1000, 5000].map((amount) => (
                <button key={amount} onClick={() => setTopUpAmount(amount)}>
                  +{formatCredits(amount)}
                </button>
              ))}
            </div>
            <button className="order-button" onClick={handleTopUp}>
              Зачислить {formatCredits(topUpAmount)} GC{" "}
              <ArrowDownRight size={18} />
            </button>
          </div>
        </div>
      )}
      {profileOpen && (
        <ProfileModal
          token={session.access_token}
          profile={session.profile}
          onClose={() => setProfileOpen(false)}
          onLogout={onLogout}
          onSaved={(profile) => onSessionChange({ ...session, profile })}
        />
      )}
      {previewFrame && (
        <div
          className="modal-backdrop"
          onMouseDown={() => setPreviewFrame(null)}
        >
          <div
            className="product-modal"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <button
              className="modal-close"
              onClick={() => setPreviewFrame(null)}
            >
              <X />
            </button>
            <div className="product-image">
              <img src={previewFrame.image} alt="Спутниковый продукт AOI" />
              <span>ORBITAMARKET / OPTICAL MSI</span>
            </div>
            <div className="product-info">
              <span className="modal-kicker">PRODUCT READY</span>
              <h2>Снимок территории готов</h2>
              <p>
                Демонстрационный продукт сформирован на основе World Imagery.
                Источник и атрибуция сохранены вместе с кадром.
              </p>
              <dl>
                <div>
                  <dt>Источник</dt>
                  <dd>{previewFrame.source}</dd>
                </div>
                <div>
                  <dt>Центр</dt>
                  <dd>
                    {previewFrame.center[1].toFixed(5)}° N /{" "}
                    {previewFrame.center[0].toFixed(5)}° E
                  </dd>
                </div>
                <div>
                  <dt>Получен</dt>
                  <dd>
                    {new Date(previewFrame.captured_at).toLocaleString("ru-RU")}
                  </dd>
                </div>
              </dl>
              <a
                className="auth-submit"
                href={previewFrame.image}
                download="orbitamarket-product.png"
              >
                Сохранить продукт <ArrowDownRight />
              </a>
            </div>
          </div>
        </div>
      )}
      {toast && (
        <div className="toast">
          <Command size={17} />
          {toast}
        </div>
      )}
    </div>
  );
}

function App() {
  const [session, setSession] = useState<AuthSession | null>(() => {
    try {
      return JSON.parse(
        localStorage.getItem(SESSION_KEY) || "null",
      ) as AuthSession | null;
    } catch {
      return null;
    }
  });

  const saveSession = useCallback((next: AuthSession) => {
    api.authorize(next.access_token);
    localStorage.setItem(SESSION_KEY, JSON.stringify(next));
    setSession(next);
  }, []);

  const logout = useCallback(() => {
    api.authorize(null);
    localStorage.removeItem(SESSION_KEY);
    setSession(null);
  }, []);

  useEffect(() => {
    if (!session) return;
    api
      .profile(session.access_token)
      .then((profile) => saveSession({ ...session, profile }))
      .catch(logout);
  }, []);

  api.authorize(session?.access_token || null);

  return session ? (
    <Dashboard
      session={session}
      onSessionChange={saveSession}
      onLogout={logout}
    />
  ) : (
    <AuthScreen onAuthenticated={saveSession} />
  );
}

export default App;
