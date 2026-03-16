---
description: Midori Browser v2 Roadmap - Análisis completo y plan de features
---

# Midori Browser - Análisis y Roadmap v2

## ✅ Features Ya Implementadas

### Core Browser
- [x] GeckoView engine con Mozilla Android Components v150
- [x] Navegación básica (back, forward, reload, stop)
- [x] Tabs (normal + privado) con Tab Tray grid moderno
- [x] Custom Tabs para apps externas
- [x] Find in Page
- [x] Reader View
- [x] Picture-in-Picture
- [x] Swipe to Refresh
- [x] Full Screen mode
- [x] Desktop Mode toggle
- [x] Download manager con UI Compose
- [x] WebAuthn support
- [x] QR Scanner

### Privacidad (Actual)
- [x] Tracking Protection (normal + private mode toggles)
- [x] Global Privacy Control (GPC)
- [x] Modo Privado (pestañas)
- [x] Telemetría desactivable

### UI/UX
- [x] **Splash Screen** - AndroidX SplashScreen API (Midori green + logo)
- [x] **Onboarding** - 4 pantallas (Welcome, Toolbar Position, Widget, Default Browser)
- [x] **Home Screen** - Compose UI con speed dials, búsqueda, shortcuts, trackers counter
- [x] **Home Customization** - Wallpapers (8 gradientes), colores, toggles, tamaño speed dials
- [x] **Menu Bottom Sheet** - Material 3 con iconos (reemplaza TextMenuCandidate)
- [x] **Tab Tray** - Grid moderno con Normal/Private toggle, Save to Collection
- [x] **Toolbar configurable** - Top/Bottom position
- [x] **Navigation Bar** - Back, Forward, New Tab, Tabs (badge), Menu
- [x] **Tema** - Material 3 DayNight con paleta Midori Green
- [x] **Theme toggle** - System/Light/Dark
- [x] **MidoriTypography** - Material 3 scale con Roboto

### Data & Sync
- [x] Bookmarks (PlacesBookmarksStorage) con Compose UI
- [x] Collections (SharedPreferences + JSON) con Compose UI
- [x] History (PlacesHistoryStorage) con Compose UI
- [x] Firefox Account (sign in, pair, sync)
- [x] Synced Tabs
- [x] Password Sync (SyncableLoginsStorage)
- [x] Autofill service

### Performance
- [x] GeckoView optimizations (30+ about:config prefs)
- [x] R8/ProGuard minification
- [x] Servicios diferidos en 3 fases (search → addons → push)
- [x] Engine warmUp en main thread, file cleanup en IO

### Addons & Extensions
- [x] WebExtension support
- [x] Addon Manager UI
- [x] Custom AMO collection override
- [x] Web Push integration
- [x] AstianGo como buscador por defecto

### Other
- [x] Search Widget
- [x] Crash reporting (Sentry)
- [x] About page
- [x] Settings activity completa

---

## 🚀 Features Por Implementar - Roadmap Competitivo

### 🔒 FASE 1: Privacidad (Competir con Brave/Firefox Focus)

#### 1.1 DNS Seguro (DoH / DoT)
- **DoH (DNS over HTTPS)**: Configurar en GeckoRuntime via `network.trr.mode`
  - Opciones: Off, Automático (Cloudflare), Custom URL
  - Proveedores: Cloudflare, NextDNS, AdGuard, Quad9, AstianGo DNS
  - UI: ListPreference en Privacy Settings
- **DoT (DNS over TLS)**: Via Android Private DNS setting (redirect)
- **Priority**: 🔴 Alta — diferenciador clave

#### 1.2 Niveles de Privacidad (Básico / Medio / Avanzado)
- **Básico** (por defecto):
  - Tracking Protection estándar
  - GPC activado
  - Cookies terceros bloqueadas
  - Fingerprinting protection OFF
- **Medio**:
  - Todo lo de Básico +
  - Strict Tracking Protection
  - Cookies terceros bloqueadas completamente
  - Resistir fingerprinting (privacy.resistFingerprinting)
  - WebRTC leak prevention (media.peerconnection.ice.default_address_only)
  - Referrer trimming (network.http.referer.XOriginTrimmingPolicy=2)
- **Avanzado**:
  - Todo lo de Medio +
  - First-party isolation (privacy.firstparty.isolate)
  - Canvas fingerprint protection
  - WebGL disabled
  - Font fingerprinting protection
  - Battery API disabled
  - Geolocation API requiere permiso siempre
- **UI**: Selector visual con 3 cards en Privacy Settings
- **Priority**: 🔴 Alta

#### 1.3 HTTPS-Only Mode
- Forzar HTTPS en todas las conexiones
- Fallback con confirmación del usuario
- `dom.security.https_only_mode = true`
- **Priority**: 🔴 Alta

#### 1.4 Cookie Banner Blocker
- `cookiebanners.service.mode = 1` (reject all)
- Toggle en Privacy Settings
- **Priority**: 🟡 Media

#### 1.5 Content Blocker Avanzado
- Integrar lista de filtros (EasyList, EasyPrivacy, uBlock filters)
- Contador de trackers/ads bloqueados (ya tenemos UI)
- Estadísticas por sitio
- **Priority**: 🟡 Media

#### 1.6 Built-in VPN Proxy (midorivpn-extension)
- Integrar extensión midorivpn como built-in
- Toggle rápido en menú
- **Priority**: 🟡 Media

---

### 🎨 FASE 2: UI/UX Premium

#### 2.1 Textura Única Midori
- **Midori Glass Texture**: Efecto glassmorphism sutil en:
  - Home screen background
  - Tab tray cards
  - Bottom sheets
  - Toolbar
- **Partículas flotantes**: Pequeñas partículas verdes/doradas animadas en home
- **Gradiente dinámico**: Gradiente de fondo que cambia sutilmente con hora del día
- **Priority**: 🔴 Alta — identidad visual

#### 2.2 Animaciones y Transiciones
- **Tab switching**: Animación slide/fade al cambiar tabs
- **Home → Browser**: Transición suave con shared element
- **Bottom sheet**: Spring animation al abrir/cerrar
- **Speed dials**: Animación staggered al aparecer
- **Toolbar**: Animación de scroll hide/show suave
- **Splash → Home**: Exit animation con fade + scale del logo
- **Priority**: 🔴 Alta

#### 2.3 Home Page Mejorado
- **Noticias/Feed**: Widget de noticias configurable (fuentes RSS)
- **Clima**: Widget pequeño de clima actual
- **Reloj**: Widget de reloj/fecha elegante
- **Atajos rápidos**: Más atajos configurables (VPN, borrar datos, modo lectura)
- **Barra de progreso**: Indicador visual de trackers bloqueados hoy
- **Priority**: 🟡 Media

#### 2.4 Gestos
- **Swipe left/right**: Cambiar entre tabs
- **Swipe down on toolbar**: Abrir tab tray
- **Long press back**: Historial de navegación
- **Double tap toolbar**: Scroll to top
- **Priority**: 🟡 Media

#### 2.5 Tab Groups / Spaces
- Agrupar tabs por tema/contexto
- Iconos y colores por grupo
- **Priority**: 🟡 Media

---

### ⚡ FASE 3: Productividad

#### 3.1 Built-in Ad Blocker
- Listas de filtros integradas (no extensión)
- Whitelist por sitio
- Estadísticas
- **Priority**: 🔴 Alta

#### 3.2 Traductor Integrado
- Detección automática de idioma
- Barra de traducción inline
- Usando GeckoView translation API
- **Priority**: 🟡 Media

#### 3.3 Screenshots
- Captura de página completa (full page)
- Anotaciones básicas
- Compartir directo
- **Priority**: 🟡 Media

#### 3.4 Reading List
- Guardar artículos para leer después
- Integración con Reader View
- Sync con Firefox Account
- **Priority**: 🟢 Baja

#### 3.5 Password Manager Mejorado
- UI propia para ver/editar contraseñas
- Generador de contraseñas
- Breach alerts
- **Priority**: 🟡 Media

---

### 🔧 FASE 4: Infraestructura

#### 4.1 Migrar a Jetpack Navigation
- Fragment navigation graph
- Deep links
- Safe Args
- **Priority**: 🟡 Media

#### 4.2 Notifications
- Download progress
- Sync complete
- Tab from other device
- **Priority**: 🟡 Media

#### 4.3 Widgets
- Widget de búsqueda mejorado (ya existe básico)
- Widget de favoritos
- Widget de tabs recientes
- **Priority**: 🟢 Baja

#### 4.4 Accessibility
- TalkBack support completo
- High contrast mode
- Font scaling
- **Priority**: 🟡 Media

---

## 📊 Matriz Competitiva

| Feature                    | Midori | Brave | Firefox | Chrome | DuckDuckGo |
|---------------------------|--------|-------|---------|--------|------------|
| DoH/DoT                  | ⏳     | ✅    | ✅      | ✅     | ❌         |
| Privacy Levels            | ⏳     | ✅    | ❌      | ❌     | ✅         |
| HTTPS-Only               | ⏳     | ✅    | ✅      | ✅     | ✅         |
| Cookie Banner Blocker    | ⏳     | ❌    | ✅      | ❌     | ✅         |
| Built-in Ad Blocker      | ⏳     | ✅    | ❌      | ❌     | ✅         |
| Custom Home              | ✅     | ✅    | ✅      | ✅     | ❌         |
| Wallpapers               | ✅     | ✅    | ✅      | ❌     | ❌         |
| Collections              | ✅     | ❌    | ✅      | ❌     | ❌         |
| Firefox Sync             | ✅     | ❌    | ✅      | ❌     | ❌         |
| Material 3               | ✅     | ❌    | ❌      | ✅     | ❌         |
| Built-in VPN             | ⏳     | ✅    | ❌      | ❌     | ❌         |
| Translator               | ⏳     | ❌    | ✅      | ✅     | ❌         |
| Glass Texture/Unique UI  | ⏳     | ❌    | ❌      | ❌     | ❌         |

---

## 🎯 Orden de Implementación Recomendado

### Sprint 1 (Inmediato) — Privacidad Core
1. DoH con selector de proveedores
2. HTTPS-Only Mode
3. Niveles de privacidad (Básico/Medio/Avanzado)

### Sprint 2 — Identidad Visual
4. Textura Midori Glass
5. Animaciones (splash exit, tab switch, home transitions)
6. Partículas decorativas en home

### Sprint 3 — Productividad
7. Ad Blocker integrado
8. Cookie Banner Blocker
9. Mejoras al home (widgets de fecha, atajos)

### Sprint 4 — UX Avanzado
10. Gestos de navegación
11. Tab Groups
12. Screenshots de página completa

### Sprint 5 — Infraestructura
13. VPN integration
14. Traductor
15. Password Manager UI
