# Actividad 6 — Defensa Individual
**ShipTracker · Código fuente y demostración de ejecución**
**Aaron Del Toro Arias · DAW 2025**

---

## 1. Descripción del proyecto

ShipTracker es una aplicación web de rastreo de barcos en tiempo real desarrollada con **Java 17 y Spring Boot 3.5.14**. Consume el stream AIS de aisstream.io mediante WebSocket y muestra las posiciones de los buques sobre un mapa interactivo Leaflet. Los usuarios registrados pueden guardar favoritos y configurar alertas por email cuando un barco cambia de estado.

---

## 2. Estructura del código fuente

```
shiptracker/
├── pom.xml                                    ← Dependencias Maven
├── src/main/
│   ├── java/com/shiptracker/
│   │   ├── ShipTrackerApplication.java         ← @SpringBootApplication
│   │   ├── config/
│   │   │   ├── SecurityConfig.java             ← Spring Security 6
│   │   │   ├── WebSocketConfig.java            ← WebSocket + @EnableAsync
│   │   │   ├── PasswordEncoderConfig.java      ← BCrypt bean
│   │   │   └── GlobalModelAttributes.java      ← currentUri en todos los modelos
│   │   ├── controller/
│   │   │   ├── HomeController.java             ← GET /
│   │   │   ├── AuthController.java             ← /register
│   │   │   ├── ShipController.java             ← /ships, /ships/{mmsi}
│   │   │   ├── MapController.java              ← /map, /api/**
│   │   │   └── FavoriteController.java         ← /favorites/**
│   │   ├── websocket/
│   │   │   └── ShipWebSocketHandler.java       ← Sesiones WS browser
│   │   ├── service/
│   │   │   ├── AisStreamService.java           ← Núcleo: WSS + caché + broadcast
│   │   │   ├── MarineApiService.java           ← Fachada AIS/mock
│   │   │   ├── UserService.java                ← Registro usuarios
│   │   │   ├── FavoriteService.java            ← CRUD favoritos + alertas
│   │   │   ├── EquasisService.java             ← Scraping datos técnicos
│   │   │   ├── ShipPhotoService.java           ← Scraping foto buque
│   │   │   └── ShipAlertService.java           ← @EventListener email alerts
│   │   ├── event/
│   │   │   └── ShipStatusChangedEvent.java     ← POJO evento cambio estado
│   │   ├── model/
│   │   │   ├── User.java                       ← Entidad JPA
│   │   │   ├── Favorite.java                   ← Entidad JPA
│   │   │   └── Port.java                       ← Entidad JPA
│   │   ├── repository/
│   │   │   ├── UserRepository.java
│   │   │   ├── FavoriteRepository.java         ← Con @Query de alertas
│   │   │   └── PortRepository.java
│   │   └── dto/
│   │       ├── ShipDTO.java                    ← Barco en memoria
│   │       └── VesselInfoDTO.java              ← Datos Equasis
│   └── resources/
│       ├── application.properties              ← Configuración completa
│       ├── templates/                          ← Thymeleaf HTML
│       │   ├── index.html, map.html, ships.html
│       │   ├── ship-detail.html, favorites.html
│       │   ├── login.html, register.html
│       │   └── fragments/ (navbar, footer)
│       ├── static/
│       │   ├── css/styles.css                  ← CSS3 propio responsive
│       │   ├── js/main.js                      ← WebSocket + Leaflet
│       │   └── images/                         ← SVGs de tipos de barco
│       └── META-INF/
│           └── spring-configuration-metadata.json
```

---

## 3. Demostración de ejecución paso a paso

### Paso 1 — Arrancar la aplicación

```bash
cd shiptracker
mvn spring-boot:run
```

Output esperado en consola:
```
INFO  AisStreamService - Conectado a aisstream.io — enviando suscripción...
INFO  AisStreamService - Suscripción enviada OK — esperando mensajes AIS...
INFO  Started ShipTrackerApplication in X.XXX seconds
```

### Paso 2 — Verificar el estado AIS

Abrir en el navegador:
```
http://localhost:8080/api/ais/status
```
Respuesta JSON esperada:
```json
{
  "configured": true,
  "connected": true,
  "messagesReceived": 42,
  "cachedShips": 15
}
```

### Paso 3 — Ver el mapa en vivo

```
http://localhost:8080/map
```
- Se abre la conexión WebSocket `ws://localhost:8080/ws/ships`
- Los marcadores aparecen en el mapa (zona Canarias si hay datos reales, o 18 barcos mock)
- Los marcadores se actualizan en tiempo real sin recargar la página

### Paso 4 — Explorar la lista de barcos

```
http://localhost:8080/ships
```
- Búsqueda: `http://localhost:8080/ships?search=MSC`
- Filtro por tipo: `http://localhost:8080/ships?type=Cargo`

### Paso 5 — Ver ficha detalle

```
http://localhost:8080/ships/215269000
```
Muestra: nombre, MMSI, IMO, indicativo, tipo, bandera, posición, velocidad, rumbo, destino.

### Paso 6 — Registrar usuario

```
http://localhost:8080/register
```
Rellenar: usuario, email, contraseña (mín. 6 chars).

### Paso 7 — Login y favoritos

```
http://localhost:8080/login
```
Tras login → `/favorites`. Desde la ficha de cualquier barco se puede añadir a favoritos.

### Paso 8 — Activar alertas

En `/favorites`, activar el checkbox "Aviso al zarpar" o "Aviso al atracar" para un barco y guardar.

### Paso 9 — Consola H2

```
http://localhost:8080/h2-console
JDBC URL: jdbc:h2:file:./data/shiptracker
Usuario: sa  /  Contraseña: (vacía)
```
Ejecutar: `SELECT * FROM users;` y `SELECT * FROM favorites;`

---

## 4. Explicación técnica de cada componente

### 4.1 `AisStreamService` — El núcleo del sistema

```java
@PostConstruct
public void init() {
    if (isConfigured()) connect();  // conecta al arrancar
}

@Scheduled(fixedDelay = 30000, initialDelay = 30000)
public void reconnectIfNeeded() {
    if (!connected) connect();  // reconecta si se pierde
}
```

El inner class `AisListener` implementa `java.net.http.WebSocket.Listener`:
- `onOpen` → envía la suscripción JSON con API key y bounding box
- `onText` → acumula el mensaje en `msgBuffer` (puede llegar fragmentado) y llama a `processMessage()` cuando `last=true`
- `onClose` / `onError` → marca `connected=false` para que el scheduler reconecte

En `processMessage()`:
- **PositionReport**: actualiza lat/lng, velocidad, rumbo y estado. Si el estado cambia, publica `ShipStatusChangedEvent`. Hace broadcast a los browsers.
- **ShipStaticData**: actualiza nombre, indicativo, IMO, tipo y dimensiones.

### 4.2 `ShipWebSocketHandler` — Browser sessions

```java
@Override
public void afterConnectionEstablished(WebSocketSession session) {
    aisStreamService.addBrowserSession(session);  // añade al listado
}
```

`AisStreamService.broadcastUpdate(ship)` itera sobre las sesiones activas y envía el JSON `{type:"update", ship:{…}}`. Las sesiones cerradas se eliminan automáticamente.

El handler también recibe mensajes del browser. Cuando llega un mensaje `{type:"viewport", ...}`, llama a `AisStreamService.updateViewport()`:

```java
@Override
protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    JsonNode node = objectMapper.readTree(message.getPayload());
    if ("viewport".equals(node.path("type").asText())) {
        aisStreamService.updateViewport(
            node.path("minLat").asDouble(), node.path("maxLat").asDouble(),
            node.path("minLng").asDouble(), node.path("maxLng").asDouble()
        );
    }
}
```

`updateViewport()` en `AisStreamService`:
```java
public void updateViewport(double minLat, double maxLat, double minLng, double maxLng) {
    if (!isConfigured()) return;
    this.bboxMinLat = minLat;  this.bboxMaxLat = maxLat;
    this.bboxMinLng = minLng;  this.bboxMaxLng = maxLng;
    shipCache.clear();   // limpiar barcos de la zona anterior
    lastStatus.clear();
    if (aisSocket != null) aisSocket.abort();  // cerrar suscripción actual
    connect();  // nueva suscripción con el bounding box actualizado
}
```

En el browser, el evento `moveend` de Leaflet dispara el envío con un debounce de 800 ms:
```javascript
map.on('moveend', () => {
  clearTimeout(viewportTimer);
  viewportTimer = setTimeout(() => {
    const b = map.getBounds();
    ws.send(JSON.stringify({ type:'viewport',
      minLat: b.getSouth(), maxLat: b.getNorth(),
      minLng: b.getWest(),  maxLng: b.getEast() }));
  }, 800);
});
```

### 4.3 `ShipAlertService` — Alertas asíncronas

```java
@EventListener
@Async           // hilo separado → no bloquea el procesamiento AIS
@Transactional(readOnly = true)
public void onStatusChanged(ShipStatusChangedEvent event) {
    // Solo actúa en Atracado→En navegación y En navegación→Atracado
    boolean isDeparture = "Atracado".equals(event.getPreviousStatus())
                       && "En navegación".equals(event.getNewStatus());
    // ...
    List<Favorite> recipients = favoriteRepository.findDepartureAlertsByMmsi(mmsi);
    // JOIN FETCH para evitar LazyInitializationException en hilo async
    for (Favorite fav : recipients) {
        sendDepartureMail(fav.getUser().getEmail(), event);
    }
}
```

El email enviado es HTML con diseño oscuro marinero, incluye nombre del barco, descripción del evento, destino (si conocido) y botón con enlace a la ficha.

### 4.4 `SecurityConfig` — Seguridad

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/favorites/**").authenticated()
    .anyRequest().permitAll()
)
.formLogin(form -> form
    .loginPage("/login")
    .defaultSuccessUrl("/map", true)
)
```

- Solo favoritos requiere autenticación
- Login en formulario propio `/login`
- BCrypt con factor 10 para contraseñas

### 4.5 `MarineApiService` — Patrón Fachada

```java
public List<ShipDTO> getAllShips() {
    if (aisStreamService.isConfigured() && aisStreamService.hasData()) {
        return new ArrayList<>(aisStreamService.getCachedShips());  // AIS real
    }
    return buildMockShips();  // 18 barcos simulados si no hay AIS
}
```

Esto permite que la aplicación funcione correctamente en demos offline o sin API key.

### 4.6 Gestión thread-safety

```java
// Thread-safe para acceso concurrente desde hilos AIS y HTTP
private final ConcurrentHashMap<String, ShipDTO> shipCache = new ConcurrentHashMap<>();
private final CopyOnWriteArrayList<WebSocketSession> browserSessions = new CopyOnWriteArrayList<>();

// Sincronización al enviar mensajes WebSocket (las sesiones no son thread-safe)
synchronized (session) {
    if (session.isOpen()) {
        session.sendMessage(msg);
    }
}
```

---

## 5. Preguntas frecuentes de defensa

**P: ¿Por qué usas H2 y no MySQL o PostgreSQL?**
R: H2 en modo fichero es suficiente para este proyecto académico. No requiere instalación externa, la BD se crea sola al arrancar con `ddl-auto=update`, y los datos persisten entre reinicios. Para producción real sería trivial cambiar a PostgreSQL solo modificando el `pom.xml` y la URL de conexión.

**P: ¿Por qué no usas `@RestController` en los controladores?**
R: Los controladores MVC usan `@Controller` con `@ResponseBody` en los endpoints REST. `@RestController` es un shortcut pero la diferencia es solo anotacional. Preferí ser explícito sobre qué métodos devuelven JSON y cuáles vistas Thymeleaf.

**P: ¿Cómo sabes si el estado cambió para disparar la alerta?**
R: `AisStreamService` mantiene un `ConcurrentHashMap<String mmsi, String status>` llamado `lastStatus`. Cuando llega un `PositionReport`, hace `lastStatus.put(mmsi, newStatus)` y compara con el valor anterior. Si son distintos y no null, publica el evento.

**P: ¿Por qué `Boolean` wrapper en vez de `boolean` primitivo en `Favorite`?**
R: H2 con `ddl-auto=update` añadió las columnas `alert_on_departure` y `alert_on_arrival` a la tabla existente con valor `NULL` en las filas previas. JPA no puede asignar `NULL` a un tipo primitivo `boolean` → `NullPointerException`. El tipo wrapper `Boolean` acepta `null` y el operador `= true` en las queries JPQL excluye correctamente los `null`.

**P: ¿Qué pasa si se cae la conexión con aisstream.io?**
R: El listener `onClose`/`onError` marca `connected=false`. El método anotado con `@Scheduled(fixedDelay=30000)` lo detecta y llama a `connect()` de nuevo. El servidor sigue sirviendo los barcos del caché hasta que se restablezca la conexión.

**P: ¿Por qué solo ves barcos de la zona de Canarias y no de todo el mundo?**
R: Porque la suscripción a aisstream.io usa un bounding box: solo envía barcos dentro de ese rectángulo geográfico. El sistema ahora implementa viewport dinámico: cuando el usuario mueve el mapa, el browser envía las coordenadas del área visible al servidor por WebSocket, el servidor actualiza el bounding box y se re-suscribe a aisstream.io con la nueva zona. El usuario siempre ve los barcos de donde está mirando.

**P: ¿Por qué hay un debounce de 800 ms en el cliente antes de enviar el viewport?**
R: Porque al arrastrar el mapa se disparan decenas de eventos `moveend` por segundo. Sin debounce, el servidor cerraría y abriría la conexión AIS decenas de veces mientras el usuario arrastra, lo que saturaria la API. Con 800 ms de espera, solo se re-suscribe una vez cuando el usuario termina de mover el mapa.

**P: Al cambiar de zona, ¿se pierden los barcos que estaba viendo antes?**
R: Sí, intencionadamente. `updateViewport()` llama a `shipCache.clear()` y `lastStatus.clear()` antes de re-suscribirse. Si no se limpiara el caché, el mapa mostraría barcos de la zona anterior mezclados con los nuevos, lo que sería incorrecto geográficamente.

**P: ¿Cómo funciona el WebSocket del mapa sin Spring WebSocket Protocol?**
R: `ShipWebSocketHandler` implementa `WebSocketHandler` de Spring y se registra en `WebSocketConfig` en la ruta `/ws/ships`. Es un WebSocket puro (no STOMP). El browser lo abre con `new WebSocket('ws://...')` y recibe mensajes JSON que el JS de Leaflet procesa para mover los marcadores.

**P: ¿Por qué el email usa `localhost:8080` en el enlace?**
R: El proyecto es académico y se despliega localmente. En un entorno de producción, ese valor se sacaría a una propiedad configurable (`app.base-url`) inyectada con `@Value`. Es una mejora conocida y pendiente.

**P: ¿Qué valida Spring Security en el registro?**
R: Spring Security no valida el registro directamente. La validación la hace `UserService`: comprueba que `username` y `email` no estén ya en BD con `existsByUsername()` y `existsByEmail()`. Las restricciones de formato (mínimo 6 chars, formato email) las aplica HTML5 en el formulario y `@Email` + `@NotBlank` a nivel de entidad JPA.

**P: ¿Cómo mapeas el código de bandera desde el MMSI?**
R: Los primeros 3 dígitos del MMSI son el **MID** (*Maritime Identification Digits*), asignado por la ITU a cada país. El método `mmsiToFlag()` tiene un switch con los códigos MID más comunes. Para MMSI desconocidos devuelve `??`.

**P: ¿Por qué Jsoup para Equasis y no una API REST?**
R: Equasis.org no ofrece API pública. Sí ofrece una interfaz web gratuita con registro. `EquasisService` hace scraping del HTML con Jsoup, exactamente como haría un usuario en el navegador, pero de forma programática.

**P: ¿Qué ocurre si dos usuarios tienen el mismo barco como favorito y ambos tienen alerta activa?**
R: La query `findDepartureAlertsByMmsi(mmsi)` devuelve todos los `Favorite` con ese MMSI y `alertOnDeparture=true`. El bucle `for` envía un email a cada uno. Cada usuario recibe su propio email independientemente.

---

## 6. Código más relevante para mostrar en la defensa

### AIS → Browser en tiempo real (fragmento `AisStreamService`)

```java
// Al llegar un PositionReport de aisstream.io:
String newStatus  = mapStatus(pos.path("NavigationalStatus").asInt());
String prevStatus = lastStatus.put(mmsi, newStatus);
ship.setStatus(newStatus);

if (prevStatus != null && !prevStatus.equals(newStatus)) {
    eventPublisher.publishEvent(new ShipStatusChangedEvent(
        mmsi, ship.getName(), prevStatus, newStatus, ship.getDestination()
    ));
}
broadcastUpdate(ship);  // → todos los browsers conectados
```

### Query con JOIN FETCH (fragmento `FavoriteRepository`)

```java
@Query("SELECT f FROM Favorite f JOIN FETCH f.user " +
       "WHERE f.mmsi = :mmsi AND f.alertOnDeparture = true")
List<Favorite> findDepartureAlertsByMmsi(@Param("mmsi") String mmsi);
```
`JOIN FETCH` carga el usuario en la misma query para evitar `LazyInitializationException` en el hilo `@Async`.

### Email asíncrono (fragmento `ShipAlertService`)

```java
@EventListener
@Async
@Transactional(readOnly = true)
public void onStatusChanged(ShipStatusChangedEvent event) {
    if (!"Atracado".equals(event.getPreviousStatus())) return;
    if (!"En navegación".equals(event.getNewStatus())) return;
    favoriteRepository.findDepartureAlertsByMmsi(event.getMmsi())
        .forEach(fav -> sendDepartureMail(fav.getUser().getEmail(), event));
}
```

### Seguridad (fragmento `SecurityConfig`)

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/favorites/**").authenticated()
    .anyRequest().permitAll()
)
.formLogin(form -> form
    .loginPage("/login")
    .defaultSuccessUrl("/map", true)
    .failureUrl("/login?error=true")
)
.logout(logout -> logout
    .logoutUrl("/logout")
    .logoutSuccessUrl("/login?logout=true")
)
```
