import { useState, type FormEvent } from "react";
import {
  ArrowRight,
  Eye,
  EyeOff,
  LockKeyhole,
  Satellite,
  ShieldCheck,
  UserRound,
  X,
} from "lucide-react";
import { api, type AuthSession, type Profile, type ProfileDraft } from "./api";

export function AuthScreen({
  onAuthenticated,
}: {
  onAuthenticated: (session: AuthSession) => void;
}) {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [visible, setVisible] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setLoading(true);
    setError("");
    try {
      const session =
        mode === "login"
          ? await api.login(email, password)
          : await api.register(email, password, name);
      onAuthenticated(session);
    } catch (reason) {
      setError(
        reason instanceof Error ? reason.message : "Не удалось выполнить вход",
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="auth-screen">
      <section className="auth-visual">
        <div className="auth-grid" />
        <div className="auth-planet">
          <i />
          <i />
          <i />
        </div>
        <a className="brand auth-brand" href="#">
          <span className="brand-mark">
            <i />
            <i />
            <i />
          </span>
          <span>
            ORBITA<strong>MARKET</strong>
          </span>
        </a>
        <div className="auth-visual-copy">
          <span>БЮРО 1440 · INDUSTRIAL SOFTWARE</span>
          <h1>
            Личный контур
            <br />
            <em>космических данных.</em>
          </h1>
          <p>
            Управляйте миссиями, геокредитами и спутниковыми продуктами из
            единого защищённого пространства.
          </p>
        </div>
        <div className="auth-telemetry">
          <div>
            <Satellite />
            <span>ОРБИТАЛЬНЫЙ КОНТУР</span>
            <strong>ONLINE</strong>
          </div>
          <div>
            <ShieldCheck />
            <span>ЗАЩИТА СЕССИИ</span>
            <strong>JWT / HS256</strong>
          </div>
        </div>
      </section>
      <section className="auth-panel">
        <div className="auth-form-wrap">
          <div className="auth-index">ACCESS / 01</div>
          <h2>{mode === "login" ? "Войти в систему" : "Создать аккаунт"}</h2>
          <p>
            {mode === "login"
              ? "Продолжите работу в операционном центре."
              : "Откройте личный кабинет OrbitaMarket."}
          </p>
          <div className="auth-tabs">
            <button
              className={mode === "login" ? "active" : ""}
              onClick={() => {
                setMode("login");
                setError("");
              }}
            >
              Вход
            </button>
            <button
              className={mode === "register" ? "active" : ""}
              onClick={() => {
                setMode("register");
                setError("");
              }}
            >
              Регистрация
            </button>
          </div>
          <form onSubmit={submit}>
            {mode === "register" && (
              <label>
                Имя и фамилия
                <div className="auth-input">
                  <UserRound />
                  <input
                    value={name}
                    onChange={(event) => setName(event.target.value)}
                    placeholder="Алексей Крылов"
                    required
                    maxLength={100}
                  />
                </div>
              </label>
            )}
            <label>
              Рабочая почта
              <div className="auth-input">
                <span>@</span>
                <input
                  type="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="name@company.ru"
                  required
                />
              </div>
            </label>
            <label>
              Пароль
              <div className="auth-input">
                <LockKeyhole />
                <input
                  type={visible ? "text" : "password"}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="Не менее 8 символов"
                  required
                  minLength={8}
                />
                <button type="button" onClick={() => setVisible(!visible)}>
                  {visible ? <EyeOff /> : <Eye />}
                </button>
              </div>
            </label>
            {error && <div className="auth-error">{error}</div>}
            <button className="auth-submit" disabled={loading}>
              {loading
                ? "Проверка контура…"
                : mode === "login"
                  ? "Войти"
                  : "Создать аккаунт"}
              <ArrowRight />
            </button>
          </form>
          <div className="auth-security">
            <ShieldCheck /> Пароль защищён BCrypt · сессия ограничена по времени
          </div>
        </div>
      </section>
    </main>
  );
}

export function ProfileModal({
  token,
  profile,
  onClose,
  onSaved,
  onLogout,
}: {
  token: string;
  profile: Profile;
  onClose: () => void;
  onSaved: (profile: Profile) => void;
  onLogout: () => void;
}) {
  const [draft, setDraft] = useState<ProfileDraft>({
    display_name: profile.display_name,
    job_title: profile.job_title || "",
    company: profile.company || "",
    phone: profile.phone || "",
    bio: profile.bio || "",
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const change = (field: keyof ProfileDraft, value: string) =>
    setDraft((current) => ({ ...current, [field]: value }));
  const save = async (event: FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setError("");
    try {
      onSaved(await api.updateProfile(token, draft));
      onClose();
    } catch (reason) {
      setError(
        reason instanceof Error ? reason.message : "Профиль не сохранён",
      );
    } finally {
      setSaving(false);
    }
  };
  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div
        className="profile-modal"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <button className="modal-close" onClick={onClose}>
          <X />
        </button>
        <div className="profile-heading">
          <div className="profile-avatar">
            {profile.display_name
              .split(" ")
              .map((part) => part[0])
              .slice(0, 2)
              .join("")
              .toUpperCase()}
          </div>
          <div>
            <span>ЛИЧНЫЙ КАБИНЕТ</span>
            <h2>{profile.display_name}</h2>
            <p>{profile.email}</p>
          </div>
        </div>
        <form onSubmit={save}>
          <div className="profile-fields">
            <label>
              Имя
              <input
                value={draft.display_name}
                onChange={(event) => change("display_name", event.target.value)}
                required
              />
            </label>
            <label>
              Должность
              <input
                value={draft.job_title}
                onChange={(event) => change("job_title", event.target.value)}
                placeholder="Space operations"
              />
            </label>
            <label>
              Компания
              <input
                value={draft.company}
                onChange={(event) => change("company", event.target.value)}
                placeholder="Бюро 1440"
              />
            </label>
            <label>
              Телефон
              <input
                value={draft.phone}
                onChange={(event) => change("phone", event.target.value)}
                placeholder="+7 900 000-00-00"
              />
            </label>
            <label className="profile-bio">
              О себе
              <textarea
                value={draft.bio}
                onChange={(event) => change("bio", event.target.value)}
                maxLength={500}
                placeholder="Направление работы и интересы"
              />
            </label>
          </div>
          {error && <div className="auth-error">{error}</div>}
          <div className="profile-actions">
            <button type="button" className="logout-button" onClick={onLogout}>
              Выйти из аккаунта
            </button>
            <button className="auth-submit" disabled={saving}>
              {saving ? "Сохранение…" : "Сохранить профиль"}
              <ArrowRight />
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
