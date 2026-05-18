# Actividad 3 — Diseño del Modelo de Datos
**ShipTracker · Proyecto DAW 2025 · Aaron Del Toro Arias**

---

## 1. Entidades persistentes (JPA)

El sistema tiene **3 entidades** mapeadas en la base de datos H2:

| Entidad | Tabla | Descripción |
|---------|-------|-------------|
| `User` | `users` | Usuarios registrados en la aplicación |
| `Favorite` | `favorites` | Barcos favoritos de cada usuario |
| `Port` | `ports` | Puertos (reservado para futuras funcionalidades) |

> Los datos de barcos (`ShipDTO`) **no se persisten**. Viven en memoria en un `ConcurrentHashMap` dentro de `AisStreamService` y se reconstruyen en cada arranque desde el stream AIS en tiempo real.

---

## 2. Diagrama Entidad-Relación

```
┌──────────────────────────────┐         ┌──────────────────────────────────────┐
│           USERS              │         │              FAVORITES               │
├──────────────────────────────┤         ├──────────────────────────────────────┤
│ PK  id           BIGINT      │         │ PK  id              BIGINT           │
│     username     VARCHAR(30) │         │ FK  user_id         BIGINT  ──────►  │
│     email        VARCHAR     │         │     mmsi            VARCHAR          │
│     password     VARCHAR     │         │     ship_name       VARCHAR          │
│     role         VARCHAR(10) │         │     ship_type       VARCHAR          │
│     created_at   TIMESTAMP   │         │     ship_flag       VARCHAR          │
└──────────────────────────────┘         │     notes           VARCHAR(500)     │
              │                          │     added_at        TIMESTAMP        │
              │  1                       │     alert_on_departure  BOOLEAN      │
              └──────────────────────────│     alert_on_arrival    BOOLEAN      │
                       N                 ├──────────────────────────────────────┤
                                         │ UQ (user_id, mmsi)                   │
                                         └──────────────────────────────────────┘

┌──────────────────────────────┐
│            PORTS             │
├──────────────────────────────┤
│ PK  id          BIGINT       │
│     name        VARCHAR      │
│     country     VARCHAR      │
│     locode      VARCHAR      │
│     latitude    DOUBLE       │
│     longitude   DOUBLE       │
│     description VARCHAR      │
└──────────────────────────────┘
```

**Cardinalidad**: Un `User` puede tener muchos `Favorite` (1:N). La combinación `(user_id, mmsi)` tiene una restricción `UNIQUE` para evitar duplicados.

---

## 3. Diagrama Relacional (SQL DDL)

```sql
-- Tabla de usuarios
CREATE TABLE users (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    username   VARCHAR(255) NOT NULL UNIQUE,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(10)  NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- Tabla de favoritos
CREATE TABLE favorites (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    user_id              BIGINT       NOT NULL,
    mmsi                 VARCHAR(255) NOT NULL,
    ship_name            VARCHAR(255),
    ship_type            VARCHAR(255),
    ship_flag            VARCHAR(10),
    notes                VARCHAR(500),
    added_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    alert_on_departure   BOOLEAN      DEFAULT FALSE,
    alert_on_arrival     BOOLEAN      DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_mmsi UNIQUE (user_id, mmsi),
    CONSTRAINT fk_favorites_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Tabla de puertos (referencia futura)
CREATE TABLE ports (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255) NOT NULL,
    country     VARCHAR(255),
    locode      VARCHAR(20),
    latitude    DOUBLE,
    longitude   DOUBLE,
    description VARCHAR(255),
    PRIMARY KEY (id)
);
```

> En el proyecto se usa `spring.jpa.hibernate.ddl-auto=update`, por lo que Hibernate genera y actualiza el esquema automáticamente.

---

## 4. Descripción de atributos

### Entidad `User`

| Atributo | Tipo Java | Columna | Restricciones | Descripción |
|----------|-----------|---------|---------------|-------------|
| `id` | `Long` | `id` | PK, auto-increment | Identificador único |
| `username` | `String` | `username` | NOT NULL, UNIQUE | Nombre de usuario (3-30 chars) |
| `email` | `String` | `email` | NOT NULL, UNIQUE, @Email | Dirección de correo electrónico |
| `password` | `String` | `password` | NOT NULL | Hash BCrypt de la contraseña |
| `role` | `Role` (enum) | `role` | NOT NULL, default=USER | Rol del usuario: USER o ADMIN |
| `createdAt` | `LocalDateTime` | `created_at` | NOT NULL | Fecha y hora de registro |
| `favorites` | `List<Favorite>` | — | OneToMany lazy | Relación con la tabla de favoritos |

### Entidad `Favorite`

| Atributo | Tipo Java | Columna | Restricciones | Descripción |
|----------|-----------|---------|---------------|-------------|
| `id` | `Long` | `id` | PK, auto-increment | Identificador único |
| `user` | `User` | `user_id` | FK NOT NULL | Usuario propietario del favorito |
| `mmsi` | `String` | `mmsi` | NOT NULL | Identificador MMSI del buque |
| `shipName` | `String` | `ship_name` | — | Nombre del buque al añadirlo |
| `shipType` | `String` | `ship_type` | — | Tipo del buque (Cargo, Tanque…) |
| `shipFlag` | `String` | `ship_flag` | — | Código de país/bandera |
| `notes` | `String` | `notes` | length=500 | Notas libres del usuario |
| `addedAt` | `LocalDateTime` | `added_at` | NOT NULL | Fecha de adición a favoritos |
| `alertOnDeparture` | `Boolean` | `alert_on_departure` | default=false | Alerta cuando el barco zarpa |
| `alertOnArrival` | `Boolean` | `alert_on_arrival` | default=false | Alerta cuando el barco atraca |

### Entidad `Port`

| Atributo | Tipo Java | Columna | Descripción |
|----------|-----------|---------|-------------|
| `id` | `Long` | `id` | PK, auto-increment |
| `name` | `String` | `name` | Nombre del puerto |
| `country` | `String` | `country` | País |
| `locode` | `String` | `locode` | Código UN/LOCODE (p.ej. ES LPA) |
| `latitude` | `Double` | `latitude` | Latitud geográfica |
| `longitude` | `Double` | `longitude` | Longitud geográfica |
| `description` | `String` | `description` | Descripción adicional |

---

## 5. DTOs (no persistidos)

### `ShipDTO`

Objeto de transferencia que representa un barco en memoria. Se actualiza en tiempo real con datos AIS y se serializa a JSON para el front-end.

| Campo | Tipo | Fuente | Descripción |
|-------|------|--------|-------------|
| `mmsi` | `String` | AIS PositionReport | Identificador único del buque (9 dígitos) |
| `imo` | `String` | AIS ShipStaticData | Número IMO (si se ha recibido) |
| `name` | `String` | AIS ShipStaticData / MetaData | Nombre del buque |
| `type` | `String` | AIS ShipStaticData (Type code) | Tipo: Cargo, Tanque, Pasajeros… |
| `flag` | `String` | MMSI prefix (MID) | Código de país derivado del MMSI |
| `callSign` | `String` | AIS ShipStaticData | Indicativo de llamada |
| `latitude` | `Double` | AIS PositionReport | Latitud actual |
| `longitude` | `Double` | AIS PositionReport | Longitud actual |
| `speed` | `Double` | AIS SOG | Velocidad sobre el fondo (nudos) |
| `course` | `Double` | AIS COG | Rumbo sobre el fondo (grados) |
| `status` | `String` | AIS NavigationalStatus | Estado: En navegación, Atracado… |
| `destination` | `String` | AIS ShipStaticData | Puerto de destino declarado |
| `length` | `Double` | AIS Dimension A+B | Eslora calculada (metros) |
| `width` | `Double` | AIS Dimension C+D | Manga calculada (metros) |
| `draught` | `Double` | AIS MaximumStaticDraught | Calado máximo (metros) |
| `lastUpdate` | `String` | Sistema (now) | Última actualización recibida |
| `favorite` | `boolean` | Calculado | Indica si es favorito del usuario activo |

### `VesselInfoDTO`

Datos técnicos obtenidos de **Equasis.org** mediante scraping. Solo disponible cuando el barco tiene número IMO.

| Campo | Fuente Equasis | Descripción |
|-------|----------------|-------------|
| `yearBuilt` | Año de construcción | — |
| `grossTonnage` | Arqueo bruto (GT) | Medida de volumen total del buque |
| `deadweight` | Peso muerto (DWT) | Capacidad de carga en toneladas |
| `draught` | Calado | Profundidad de inmersión |
| `portOfRegistry` | Puerto de matrícula | — |
| `operator` | Operador / naviera | — |
| `vesselTypeDetailed` | Tipo detallado | Subtipo del buque |
| `flagCountry` | País de bandera | — |
| `lengthOverall` | Eslora total | — |
| `breadthMoulded` | Manga de trazado | — |

---

## 6. Caché en memoria

Además de la BD relacional, el sistema mantiene un caché en memoria:

```
ConcurrentHashMap<String mmsi, ShipDTO>   → shipCache      (en AisStreamService)
ConcurrentHashMap<String mmsi, String>    → lastStatus     (para detectar cambios de estado)
```

Estos mapas son thread-safe y se actualizan en tiempo real. Al reiniciar la aplicación se vacían y se reconstruyen a medida que llegan nuevos mensajes AIS.

---

## 7. Repositorios JPA

### `UserRepository`
```java
Optional<User> findByUsername(String username);
boolean existsByUsername(String username);
boolean existsByEmail(String email);
```

### `FavoriteRepository`
```java
List<Favorite> findByUserOrderByAddedAtDesc(User user);
Optional<Favorite> findByUserAndMmsi(User user, String mmsi);
boolean existsByUserAndMmsi(User user, String mmsi);
void deleteByUserAndMmsi(User user, String mmsi);

// Para alertas (JOIN FETCH para evitar LazyInitializationException en hilo async)
@Query("SELECT f FROM Favorite f JOIN FETCH f.user WHERE f.mmsi = :mmsi AND f.alertOnDeparture = true")
List<Favorite> findDepartureAlertsByMmsi(@Param("mmsi") String mmsi);

@Query("SELECT f FROM Favorite f JOIN FETCH f.user WHERE f.mmsi = :mmsi AND f.alertOnArrival = true")
List<Favorite> findArrivalAlertsByMmsi(@Param("mmsi") String mmsi);
```

### `PortRepository`
```java
// Hereda de JpaRepository: findAll(), findById(), save(), delete()…
```
