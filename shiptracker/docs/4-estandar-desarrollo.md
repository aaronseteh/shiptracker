# Actividad 4 — Estándar de Desarrollo: Documento Técnico
**ShipTracker · Proyecto DAW 2025 · Aaron Del Toro Arias**

---

## 1. Arquitectura del sistema

ShipTracker sigue la arquitectura **MVC en capas** de Spring Boot, extendida con un componente de streaming en tiempo real:

```
┌─────────────────────────────────────────────────────────────────────┐
│                          CAPA PRESENTACIÓN                          │
│  Thymeleaf Templates (.html)  +  CSS3 custom  +  Leaflet.js         │
│  WebSocket JS (ws://…/ws/ships)                                     │
└──────────────────────────┬──────────────────────────────────────────┘
                           │ HTTP / WebSocket
┌──────────────────────────▼──────────────────────────────────────────┐
│                          CAPA CONTROLADORES                         │
│  HomeController  AuthController  ShipController                     │
│  MapController   FavoriteController                                 │
│  ShipWebSocketHandler (WebSocket sessions)                          │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────────┐
│                          CAPA SERVICIOS                             │
│  MarineApiService     AisStreamService     FavoriteService          │
│  UserService          EquasisService       ShipPhotoService         │
│  ShipAlertService     VesselInfoService                             │
└───────────────┬───────────────────────────┬────────────────────────┘
                │                           │
┌───────────────▼──────────┐  ┌─────────────▼──────────────────────┐
│      CAPA DATOS (JPA)    │  │         EXTERNOS                   │
│  UserRepository          │  │  aisstream.io  (WSS)               │
│  FavoriteRepository      │  │  Equasis.org   (HTTP scraping)     │
│  PortRepository          │  │  MarineTraffic (HTTP scraping)     │
│  H2 file database        │  │  Gmail SMTP    (Spring Mail)       │
└──────────────────────────┘  └────────────────────────────────────┘
```

---

## 2. Estructura de paquetes

```
com.shiptracker/
├── ShipTrackerApplication.java      ← Punto de entrada @SpringBootApplication
│
├── config/
│   ├── SecurityConfig.java          ← Configuración Spring Security (rutas, login, logout)
│   ├── WebSocketConfig.java         ← Registro del handler WebSocket + @EnableAsync
│   ├── PasswordEncoderConfig.java   ← Bean BCryptPasswordEncoder
│   └── GlobalModelAttributes.java  ← @ControllerAdvice: inyecta currentUri en todos los modelos
│
├── controller/
│   ├── HomeController.java          ← GET /  (página de inicio)
│   ├── AuthController.java          ← GET|POST /register
│   ├── ShipController.java          ← GET /ships, GET /ships/{mmsi}
│   ├── MapController.java           ← GET /map, REST /api/ships, /api/ais/status
│   └── FavoriteController.java      ← CRUD /favorites/**
│
├── websocket/
│   └── ShipWebSocketHandler.java    ← Gestiona sesiones ws:// de los navegadores
│
├── service/
│   ├── AisStreamService.java        ← Conexión WSS a aisstream.io, caché, broadcast
│   ├── MarineApiService.java        ← Fachada: AIS real o mock según configuración
│   ├── UserService.java             ← Registro, búsqueda de usuarios
│   ├── FavoriteService.java         ← CRUD de favoritos y alertas
│   ├── EquasisService.java          ← Scraping Equasis para datos técnicos
│   ├── VesselInfoService.java       ← Wrapper de EquasisService
│   ├── ShipPhotoService.java        ← Obtención de foto vía MarineTraffic
│   └── ShipAlertService.java        ← @EventListener: envía emails de alerta
│
├── event/
│   └── ShipStatusChangedEvent.java  ← POJO evento de cambio de estado AIS
│
├── model/
│   ├── User.java                    ← Entidad JPA (tabla users)
│   ├── Favorite.java                ← Entidad JPA (tabla favorites)
│   └── Port.java                    ← Entidad JPA (tabla ports)
│
├── repository/
│   ├── UserRepository.java          ← JpaRepository<User, Long>
│   ├── FavoriteRepository.java      ← JpaRepository + @Query personalizadas
│   └── PortRepository.java          ← JpaRepository<Port, Long>
│
└── dto/
    ├── ShipDTO.java                 ← Barco en memoria (no persistido)
    └── VesselInfoDTO.java           ← Datos técnicos Equasis (no persistido)
```

---

## 3. Convenciones de código

### 3.1 Nomenclatura

| Elemento | Convención | Ejemplo |
|----------|-----------|---------|
| Clases | PascalCase | `AisStreamService`, `FavoriteController` |
| Métodos | camelCase | `getUserFavorites()`, `broadcastUpdate()` |
| Variables | camelCase | `shipCache`, `lastStatus` |
| Constantes | UPPER_SNAKE_CASE | `FMT`, `log` |
| Tablas BD | snake_case | `users`, `favorites`, `alert_on_departure` |
| URLs | kebab-case | `/ships/{mmsi}`, `/favorites/{id}/alerts` |
| Propiedades | kebab-case | `marine.api.mock`, `aisstream.bbox.minLat` |

### 3.2 Inyección de dependencias

Se usa **inyección por constructor** en todos los servicios y controladores. Lombok `@RequiredArgsConstructor` genera el constructor automáticamente para los campos `final`.

```java
@Service
@RequiredArgsConstructor
public class FavoriteService {
    private final FavoriteRepository favoriteRepository;
    // ...
}
```

### 3.3 Anotaciones Spring

| Anotación | Uso en el proyecto |
|-----------|-------------------|
| `@Service` | Todos los servicios de negocio |
| `@Controller` | Controladores MVC (devuelven vistas Thymeleaf) |
| `@ResponseBody` | Endpoints REST dentro de `@Controller` |
| `@RestController` | No se usa (se prefiere `@Controller` + `@ResponseBody`) |
| `@RequiredArgsConstructor` | Inyección por constructor via Lombok |
| `@Transactional` | `removeFavorite()` y los listeners async de alertas |
| `@EventListener` + `@Async` | `ShipAlertService.onStatusChanged()` |
| `@PostConstruct` / `@PreDestroy` | Ciclo de vida de `AisStreamService` |
| `@Scheduled` | Reconexión automática cada 30 s |

### 3.4 Gestión de estado

- Los datos de barcos son **volátiles** (en memoria). Se reconstruyen al arrancar desde el stream AIS.
- Los datos de usuarios y favoritos son **persistentes** en H2 fichero (`./data/shiptracker`).
- El caché de barcos usa `ConcurrentHashMap` para ser **thread-safe** (el listener AIS y los controllers pueden acceder concurrentemente).

---

## 4. API REST

| Método | URL | Autenticación | Descripción |
|--------|-----|--------------|-------------|
| GET | `/` | No | Página de inicio |
| GET | `/map` | No | Mapa en vivo (Thymeleaf) |
| GET | `/ships` | No | Listado de barcos |
| GET | `/ships?search=nombre` | No | Búsqueda por nombre |
| GET | `/ships?type=Cargo` | No | Filtro por tipo |
| GET | `/ships/{mmsi}` | No | Ficha detalle del barco |
| GET | `/register` | No | Formulario de registro |
| POST | `/register` | No | Crear cuenta nueva |
| GET | `/login` | No | Formulario de login |
| POST | `/login` | No | Autenticar (gestionado por Spring Security) |
| POST | `/logout` | Sí | Cerrar sesión |
| GET | `/favorites` | Sí | Lista de favoritos del usuario |
| POST | `/favorites/add` | Sí | Añadir barco a favoritos |
| POST | `/favorites/remove` | Sí | Quitar barco de favoritos |
| POST | `/favorites/{id}/notes` | Sí | Actualizar notas de un favorito |
| POST | `/favorites/{id}/alerts` | Sí | Activar/desactivar alertas de un favorito |
| GET | `/api/ships` | No | JSON: lista de todos los barcos |
| GET | `/api/ships?name=x&type=y` | No | JSON: barcos filtrados |
| GET | `/api/ships/{mmsi}` | No | JSON: un barco por MMSI |
| GET | `/api/ships/{mmsi}/photo` | No | JSON: URL de la foto del barco |
| GET | `/api/ais/status` | No | JSON: estado de la conexión AIS |
| WS | `/ws/ships` | No | WebSocket bidireccional: push de barcos al browser + recepción de viewport del browser |

---

## 5. Protocolo WebSocket

### Conexión

```javascript
const ws = new WebSocket(`ws://${location.host}/ws/ships`);
```

### Mensajes servidor → browser

**Snapshot inicial** (enviado al conectar si hay datos en caché):
```json
{
  "type": "snapshot",
  "ships": [ { "mmsi": "...", "name": "...", "latitude": 28.4, ... }, ... ]
}
```

**Actualización individual** (enviado cada vez que llega un mensaje AIS):
```json
{
  "type": "update",
  "ship": { "mmsi": "...", "latitude": 28.5, "longitude": -16.2, "speed": 12.5, ... }
}
```

### Mensajes browser → servidor

**Viewport dinámico** (enviado al conectar y cada vez que el usuario mueve o hace zoom en el mapa):
```json
{
  "type":   "viewport",
  "minLat": 35.5,
  "maxLat": 44.0,
  "minLng": -10.0,
  "maxLng": 5.0
}
```

El servidor recibe este mensaje en `ShipWebSocketHandler.handleTextMessage()`, llama a `AisStreamService.updateViewport()`, que:
1. Actualiza las variables `bboxMinLat / maxLat / minLng / maxLng`
2. Limpia `shipCache` y `lastStatus` (los barcos anteriores ya no son relevantes)
3. Cierra la conexión WSS actual con `aisSocket.abort()`
4. Abre una nueva conexión a aisstream.io con el nuevo bounding box

El browser aplica un debounce de **800 ms** para no re-suscribirse con cada pixel de desplazamiento:

```javascript
map.on('moveend', () => {
  clearTimeout(viewportTimer);
  viewportTimer = setTimeout(sendViewport, 800);
});
```

### Procesamiento en el browser

```javascript
ws.onmessage = (event) => {
  const msg = JSON.parse(event.data);
  if (msg.type === 'snapshot') {
    msg.ships.forEach(ship => addOrUpdateMarker(ship));
  } else if (msg.type === 'update') {
    addOrUpdateMarker(msg.ship);
  }
};
```

---

## 6. Seguridad

### 6.1 Autenticación

- Spring Security 6 con autenticación basada en formulario.
- Contraseñas cifradas con **BCrypt** (factor de trabajo por defecto: 10).
- Gestión de sesión vía cookies de sesión HTTP (JSESSIONID).

### 6.2 Rutas protegidas

```java
// SecurityConfig.java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/favorites/**").authenticated()
    .anyRequest().permitAll()
)
```

Solo `/favorites/**` requiere autenticación. El resto de la aplicación es pública.

### 6.3 CSRF

Spring Security habilita CSRF por defecto. Todos los formularios POST incluyen el token CSRF mediante Thymeleaf (`th:action="@{…}"` lo añade automáticamente).

### 6.4 Validación de entrada

- Registro: `@NotBlank`, `@Email`, `minlength`/`maxlength` en HTML.
- Las rutas de favoritos comparan siempre el usuario autenticado con el propietario del recurso mediante `Authentication auth`.

---

## 7. Configuración (`application.properties`)

```properties
# Base de datos H2 (fichero persistente)
spring.datasource.url=jdbc:h2:file:./data/shiptracker;AUTO_SERVER=TRUE
spring.jpa.hibernate.ddl-auto=update

# AIS Stream (aisstream.io)
aisstream.api.key=<tu-api-key>
aisstream.bbox.minLat=25.0   # Zona: Islas Canarias por defecto
aisstream.bbox.maxLat=32.0
aisstream.bbox.minLng=-20.5
aisstream.bbox.maxLng=-11.5

# Modo mock (activo si AIS no tiene datos)
marine.api.mock=true

# Email (Gmail SMTP)
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=<cuenta-gmail>
spring.mail.password=<contraseña-de-aplicacion>
```

---

## 8. Gestión de errores

| Situación | Tratamiento |
|-----------|-------------|
| Barco no encontrado (`/ships/{mmsi}`) | `RuntimeException` → Spring Boot muestra página de error |
| Error al enviar email | `try/catch` en `ShipAlertService`, log de error, no propaga |
| Pérdida de conexión AIS | `@Scheduled(fixedDelay=30000)` reconecta automáticamente |
| MMSI con posición 0,0 | Filtrado en `processMessage()` (`if (lat==0 && lng==0) return`) |
| Usuario/email duplicado al registrar | `UserService` verifica existencia y devuelve mensaje de error al formulario |
| Null en campos boolean de BD | `Boolean` wrapper en `Favorite` permite valores null de H2 |

---

## 9. Dependencias principales (pom.xml)

```xml
spring-boot-starter-web          → MVC, REST
spring-boot-starter-thymeleaf    → Templates HTML
spring-boot-starter-websocket    → WebSocket server
spring-boot-starter-security     → Autenticación y autorización
spring-boot-starter-data-jpa     → ORM / repositorios
spring-boot-starter-validation   → Validación de beans
spring-boot-starter-mail         → Envío de emails
thymeleaf-extras-springsecurity6 → Directivas sec: en plantillas
com.h2database:h2                → Base de datos embebida
org.projectlombok:lombok         → Reducción de boilerplate
com.fasterxml.jackson.core       → JSON (incluido en spring-web)
org.jsoup:jsoup:1.17.2           → Scraping HTML
```
