# Actividad 1 — Documento de Alcance
**ShipTracker · Proyecto DAW 2026 · Aaron Del Toro Arias**

---

## 1. Idea del proyecto

**ShipTracker** es una aplicación web de rastreo de barcos en tiempo real. Consume datos AIS (*Automatic Identification System*) emitidos por los propios buques y los muestra sobre un mapa interactivo. El usuario puede explorar barcos activos, ver su ficha de detalle, guardar favoritos y recibir alertas por email cuando un barco favorito zarpa o atraca.

---

## 2. Motivación

El tráfico marítimo mueve más del 80 % del comercio mundial. Sin embargo, las plataformas profesionales de seguimiento (MarineTraffic, VesselFinder) son de pago. ShipTracker demuestra que es posible construir un sistema funcional y gratuito usando la API pública de **aisstream.io** y tecnologías open-source.

---

## 3. Alcance funcional

### 3.1 Funcionalidades incluidas

| ID | Funcionalidad | Descripción |
|----|---------------|-------------|
| F-01 | Mapa en vivo | Mapa Leaflet con marcadores en tiempo real de todos los barcos activos en la zona visible |
| F-02 | WebSocket bidireccional | El servidor hace push de posiciones al browser; el browser envía el viewport cuando el usuario mueve el mapa |
| F-03 | Listado de barcos | Tabla filtrable por nombre y tipo de buque |
| F-04 | Ficha de detalle | Datos completos: MMSI, IMO, indicativo, tipo, bandera, coordenadas, velocidad, rumbo, destino, dimensiones |
| F-05 | Registro y login | Autenticación con usuario/contraseña. Contraseñas cifradas con BCrypt |
| F-06 | Favoritos | Añadir/quitar barcos de lista personal con notas libres |
| F-07 | Alertas por email | Notificación cuando un barco favorito zarpa (Atracado→En navegación) o atraca (En navegación→Atracado) |
| F-08 | API REST | Endpoints JSON para barcos, foto del buque y estado del sistema AIS |
| F-09 | Foto del barco | Obtención de imagen vía scraping de MarineTraffic |

### 3.2 Funcionalidades excluidas

- Histórico de rutas almacenado en base de datos
- Predicción de llegada (ETA)
- Notificaciones push en navegador (Web Push API)
- Panel de administración de usuarios
- Soporte multi-idioma

---

## 4. Tecnologías utilizadas

### Back-end

| Tecnología | Versión | Uso |
|-----------|---------|-----|
| Java | 17 | Lenguaje principal |
| Spring Boot | 3.5.14 | Framework MVC, seguridad, persistencia, WebSocket, Mail |
| Spring Security | 6.x | Autenticación, protección CSRF, roles USER/ADMIN |
| Spring Data JPA | 3.5.x | ORM sobre H2 |
| H2 Database | — | BD embebida, fichero persistente en `./data/shiptracker` |
| Lombok | — | Reducción de boilerplate (`@Data`, `@RequiredArgsConstructor`) |
| Jackson | — | Serialización/deserialización JSON |
| Jsoup | 1.17.2 | Scraping HTML para foto del barco |
| Spring Mail | 3.5.x | Envío de emails HTML vía Gmail SMTP |

### Front-end

| Tecnología | Uso |
|-----------|-----|
| Thymeleaf 3.1 | Motor de plantillas HTML server-side |
| Thymeleaf Security extras | Directivas `sec:authorize` en plantillas |
| Leaflet.js 1.9 | Mapa interactivo |
| OpenStreetMap | Tiles del mapa (gratuitos) |
| CSS3 custom | Diseño responsive propio sin frameworks externos |
| WebSocket JS nativo | Conexión `ws://` para actualizaciones en tiempo real |

### APIs externas

| API | Uso | Coste |
|-----|-----|-------|
| aisstream.io | Stream AIS en tiempo real vía WebSocket WSS | Gratuito |
| Equasis.org | Datos técnicos (GT, DWT, año construcción, operador) | Gratuito (requiere cuenta) |
| MarineTraffic | Foto del buque (scraping `og:image`) | Gratuito |

---

## 5. Arquitectura general

```
Browser
  │
  ├── HTTP/HTTPS (Thymeleaf MVC)
  │       └─► Controllers ──► Services ──► H2 Database
  │
  └── WebSocket  ws://localhost:8080/ws/ships
                      │
               ShipWebSocketHandler
                      │
               AisStreamService ────► wss://stream.aisstream.io/v0/stream
                      │
               ShipStatusChangedEvent
                      │
               ShipAlertService ────► Gmail SMTP
```

- `AisStreamService` mantiene una conexión WebSocket permanente con aisstream.io, actualiza el caché en memoria y publica eventos de cambio de estado.
- `ShipWebSocketHandler` gestiona las sesiones abiertas con los navegadores: hace broadcast de actualizaciones AIS y recibe mensajes `viewport` del browser para re-suscribirse a la zona visible.
- Cuando el usuario mueve el mapa, el browser envía las coordenadas del viewport al servidor, que limpia el caché y abre una nueva suscripción AIS con el bounding box actualizado. El usuario siempre ve los barcos de la zona que está mirando.
- `ShipAlertService` escucha los eventos con `@EventListener @Async` y envía emails HTML a los usuarios que tengan activa la alerta para ese barco.

---

## 6. Zona de cobertura por defecto

El sistema monitoriza las **Islas Canarias** (configurable en `application.properties`):

```
aisstream.bbox.minLat=25.0  /  maxLat=32.0
aisstream.bbox.minLng=-20.5 /  maxLng=-11.5
```

---

## 7. Usuarios objetivo

- **Usuario anónimo**: navega el mapa y el listado de barcos.
- **Usuario registrado**: además puede guardar favoritos, añadir notas y activar alertas por email.

---

## 8. Resultado esperado

Una aplicación web completamente funcional, desplegable en cualquier máquina con Java 17, que integra: streaming de datos en tiempo real, persistencia relacional, autenticación segura, comunicación asíncrona por WebSocket y notificaciones por email — todo dentro del ecosistema Spring Boot sin dependencias de pago.
