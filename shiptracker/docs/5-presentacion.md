# Actividad 5 — Exposición del Proyecto en el Aula
**ShipTracker · Proyecto DAW-N 2026 · Aaron Del Toro Arias**

---

## Guión de presentación (15 min)

---

### DIAPOSITIVA 1 — Portada

**ShipTracker**
*Rastreador de barcos en tiempo real*

- Alumno: Aaron Del Toro Arias
- Módulo: Desarrollo de Aplicaciones Web (DAW 2025)
- Tecnología: Java 17 + Spring Boot 3.5

---

### DIAPOSITIVA 2 — El problema que resuelve

**¿Dónde están los barcos ahora mismo?**

- El tráfico marítimo mueve el 80 % del comercio mundial
- Plataformas profesionales como MarineTraffic o VesselFinder son de **pago**
- Existe una API gratuita: **aisstream.io** — pero nadie ha construido una app completa con ella usando Spring Boot

**Objetivo:** demostrar que se puede construir un sistema de rastreo en tiempo real, gratuito y completamente funcional, con el stack que hemos aprendido en el ciclo.

---

### DIAPOSITIVA 3 — ¿Qué es AIS?

**Automatic Identification System (AIS)**

- Todos los barcos de más de 300 GT están obligados por ley a emitir señales AIS
- Contienen: posición GPS, velocidad, rumbo, estado, destino, nombre, tipo, dimensiones
- Se emiten cada 2-10 segundos
- aisstream.io recoge estas señales a nivel mundial y las ofrece gratis vía WebSocket

```
Barco ──AIS VHF──► Torre terrestre/satélite ──► aisstream.io ──WSS──► ShipTracker
```

---

### DIAPOSITIVA 4 — Arquitectura general

```
   BROWSER                          SERVIDOR                       EXTERNO
┌──────────┐   HTTP/Thymeleaf   ┌────────────────┐   WSS        ┌──────────────┐
│ Usuario  │ ◄────────────────► │  Controllers   │ ◄──────────► │ aisstream.io │
│          │                    │  Services      │              └──────────────┘
│  Leaflet │   WebSocket ws://  │  JPA / H2      │   SMTP       ┌──────────────┐
│  Map JS  │ ◄────────────────► │  Spring Mail   │ ──────────► │ Gmail SMTP   │
└──────────┘                    └────────────────┘              └──────────────┘
```

**Dos canales de comunicación:**
1. HTTP clásico para la interfaz de usuario (Thymeleaf MVC)
2. WebSocket permanente para actualizaciones en tiempo real (sin recargar)

---

### DIAPOSITIVA 5 — Funcionalidades principales

| # | Funcionalidad | Tecnología clave |
|---|---------------|-----------------|
| 1 | Mapa en vivo con marcadores | Leaflet.js + WebSocket |
| 2 | Listado y búsqueda de barcos | Spring MVC + Thymeleaf |
| 3 | Ficha detalle del barco | Spring MVC + Equasis |
| 4 | Registro y login seguro | Spring Security + BCrypt |
| 5 | Lista de favoritos con notas | Spring Data JPA + H2 |
| 6 | Alertas por email al zarpar/atracar | Spring Mail + @EventListener |
| 7 | API REST JSON | @ResponseBody endpoints |
| 8 | Modo mock para demo offline | 18 barcos simulados |

---

### DIAPOSITIVA 6 — El mapa en vivo (demo visual)

*(Mostrar la pantalla del mapa en vivo en el navegador)*

- Cada punto del mapa es un barco real, con su posición actual
- El color/icono indica el tipo de barco
- Al hacer clic se ve la ficha completa
- Los marcadores se mueven **sin recargar la página** gracias a WebSocket
- En la esquina superior se puede ver el estado de la conexión AIS

---

### DIAPOSITIVA 7 — Cómo funciona el tiempo real

**Flujo completo de un mensaje AIS:**

```
1. Barco emite señal AIS cada 2-10 segundos
2. aisstream.io la recibe y la envía por WSS a nuestro servidor
3. AisStreamService.processMessage() la parsea y actualiza el caché
4. broadcastUpdate() la envía a todos los navegadores conectados
5. JavaScript actualiza el marcador en Leaflet
```

**Tiempo total:** < 1 segundo desde que el barco emite hasta que se mueve en el mapa.

---

### DIAPOSITIVA 8 — Sistema de alertas por email

**Flujo de una alerta de zarpe:**

```
1. Usuario activa "Aviso al zarpar" para un barco favorito
2. Llega mensaje AIS: NavigationalStatus 5 (Atracado) → 0 (En navegación)
3. AisStreamService detecta el cambio y publica ShipStatusChangedEvent
4. ShipAlertService escucha el evento (@EventListener @Async)
5. Consulta BD: ¿quién tiene alertas activas para este MMSI?
6. Envía email HTML personalizado vía Gmail SMTP
```

El email incluye: nombre del barco, descripción del evento, destino (si se conoce), y enlace directo a la ficha del barco.

---

### DIAPOSITIVA 9 — Modelo de datos

**3 entidades persistidas en H2:**

```
users (id, username, email, password, role, created_at)
   │
   └──► favorites (id, user_id, mmsi, ship_name, ship_type, ship_flag,
                   notes, added_at, alert_on_departure, alert_on_arrival)

ports (id, name, country, locode, latitude, longitude)
```

**Datos en memoria (no persistidos):**
- `ConcurrentHashMap<String mmsi, ShipDTO>` → caché de posiciones AIS
- Se reconstruye automáticamente al arrancar desde el stream

---

### DIAPOSITIVA 10 — Spring Security

- Solo la ruta `/favorites/**` requiere autenticación
- El resto de la app es pública (mapa, listado, detalle)
- Contraseñas cifradas con **BCrypt**
- CSRF activo en todos los formularios
- Roles: USER (por defecto) y ADMIN (reservado)

---

### DIAPOSITIVA 11 — Tecnologías utilizadas

**Back-end:**
- Java 17, Spring Boot 3.5.14
- Spring Security 6, Spring Data JPA, Spring WebSocket, Spring Mail
- H2 Database (fichero persistente), Lombok, Jsoup

**Front-end:**
- Thymeleaf 3.1, CSS3 propio (sin Bootstrap), Leaflet.js, WebSocket nativo

**APIs externas gratuitas:**
- aisstream.io (datos AIS), Equasis.org (datos técnicos), MarineTraffic (fotos)

---

### DIAPOSITIVA 12 — Lo más difícil del proyecto

1. **WebSocket doble**: mantener la conexión Java→aisstream.io y simultáneamente las sesiones browser→servidor es arquitectónicamente complejo
2. **Thread-safety**: los datos AIS llegan en el hilo del WebSocket listener; los controladores los leen desde hilos HTTP → `ConcurrentHashMap` y `CopyOnWriteArrayList`
3. **Alertas asíncronas**: el `@EventListener` debe ser `@Async` para no bloquear el procesamiento AIS, pero eso crea un nuevo contexto sin sesión JPA → necesidad de `JOIN FETCH` en las queries
4. **Migración de esquema H2**: añadir columnas `boolean` a datos existentes → H2 las pone a null → excepción al mapear a tipo primitivo → solución: usar `Boolean` wrapper

---

### DIAPOSITIVA 13 — Demo en vivo

**Pasos de la demo:**

1. Mostrar el mapa en vivo con barcos reales (o mock)
2. Hacer clic en un barco → ficha detalle
3. Buscar un barco por nombre
4. Registrar un usuario nuevo
5. Añadir un barco a favoritos
6. Activar "Aviso al zarpar"
7. Mostrar el email que llegó (si hay cambio de estado)
8. Consultar `/api/ais/status` en el navegador
9. Abrir H2 Console → mostrar tablas `users` y `favorites`

---

### DIAPOSITIVA 14 — Posibles mejoras futuras

- Almacenar el historial de posiciones en BD (trayectorias)
- Predicción de hora de llegada (ETA) basada en velocidad y distancia
- Notificaciones push en el navegador (Web Push API)
- Panel de administración para gestión de usuarios
- Despliegue en la nube (Railway, Render, o contenedor Docker)
- Exportar trayectorias a KML/GPX para Google Earth

---

### DIAPOSITIVA 15 — Conclusión

**ShipTracker demuestra que con Spring Boot y APIs gratuitas se puede construir:**

- Un sistema de **tiempo real** (WebSocket bidireccional)
- Con **persistencia** (Spring Data JPA + H2)
- Con **seguridad** (Spring Security 6 + BCrypt)
- Con **notificaciones** (Spring Mail + eventos asíncronos)
- Con **interfaz responsive** (Thymeleaf + CSS3 propio)

Todo ello en un único proyecto Spring Boot sin servicios de pago.

---

*Gracias por vuestra atención. ¿Preguntas?*

---

### Repositorio del proyecto

**https://github.com/aaronseteh/shiptracker**

---

## Notas para el presentador

### Gestión del tiempo (15 min)

| Bloque | Tiempo | Diapositivas |
|--------|--------|--------------|
| Introducción y motivación | 2 min | 1-3 |
| Arquitectura y tecnologías | 3 min | 4, 11 |
| Demo en vivo | 6 min | 6, 13 |
| Puntos técnicos destacados | 2 min | 7, 8, 12 |
| Cierre y preguntas | 2 min | 14, 15 |

### Consejos

- **Arranca el servidor antes de entrar al aula** para que el WebSocket ya esté conectado a aisstream.io
- Si no hay conexión a internet, el modo mock (`marine.api.mock=true`) ya está activo por defecto con 18 barcos
- Tiene preparada la consola H2 en `/h2-console` para mostrar las tablas directamente
- La URL de status AIS `/api/ais/status` es útil para mostrar que la conexión en tiempo real está activa
