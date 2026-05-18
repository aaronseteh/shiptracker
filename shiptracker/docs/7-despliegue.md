# Actividad 7 — Documento de Despliegue: Memoria de Implantación
**ShipTracker · Proyecto DAW 2025 · Aaron Del Toro Arias**

---

## 1. Requisitos del sistema

### 1.1 Requisitos de hardware (mínimos)

| Recurso | Mínimo | Recomendado |
|---------|--------|-------------|
| CPU | 1 núcleo | 2 núcleos |
| RAM | 512 MB libres | 1 GB libres |
| Disco | 200 MB | 500 MB |
| Red | Conexión a internet | Conexión estable (para AIS stream) |

### 1.2 Requisitos de software

| Software | Versión mínima | Notas |
|----------|---------------|-------|
| Java JDK | 17 | OpenJDK o Oracle JDK |
| Apache Maven | 3.8+ | Solo para compilar desde fuente |
| Navegador web | Chrome 90+ / Firefox 88+ / Edge 90+ | Para usar la aplicación |

### 1.3 Puertos necesarios

| Puerto | Uso |
|--------|-----|
| 8080 | Servidor HTTP ShipTracker (configurable) |
| 587 | Salida SMTP Gmail (alertas email) |
| 443 | Salida HTTPS/WSS a aisstream.io |

---

## 2. Cuentas externas necesarias

### 2.1 aisstream.io (datos AIS en tiempo real)

1. Ir a [https://aisstream.io](https://aisstream.io)
2. Crear cuenta gratuita
3. Ir al dashboard → **API Keys** → crear una nueva
4. Copiar la clave (formato hexadecimal de 40 caracteres)
5. Pegarla en `application.properties`:
   ```properties
   aisstream.api.key=tu-clave-aqui
   ```

> Sin esta clave, la aplicación funciona en modo mock con 18 barcos simulados.

### 2.2 Gmail — Contraseña de aplicación (alertas por email)

1. Usar una cuenta Gmail dedicada (recomendado: `shiptrackeralertas@gmail.com` o similar)
2. Activar verificación en dos pasos en [myaccount.google.com](https://myaccount.google.com)
3. Ir a **Seguridad → Contraseñas de aplicación**
4. Crear una contraseña para "Otra aplicación" → nombre: "ShipTracker"
5. Copiar la contraseña de 16 caracteres (con espacios: `xxxx xxxx xxxx xxxx`)
6. Configurar en `application.properties`:
   ```properties
   spring.mail.username=tu-cuenta@gmail.com
   spring.mail.password=xxxx xxxx xxxx xxxx
   ```

> Sin configurar esto, los emails de alerta no se envían (el resto de la app funciona igual).

### 2.3 Equasis.org (datos técnicos de buques — opcional)

1. Ir a [https://www.equasis.org](https://www.equasis.org)
2. Registrarse con email institucional o personal
3. Configurar las credenciales:
   ```properties
   equasis.username=tu@email.com
   equasis.password=tu-contraseña
   ```

> Sin Equasis, la ficha de detalle muestra solo los datos que vienen del AIS.

---

## 3. Instalación y configuración

### 3.1 Obtener el código fuente

```bash
# Opción A: desde un repositorio git
git clone <url-del-repositorio>
cd shiptracker

# Opción B: descomprimir el ZIP entregado
unzip shiptracker.zip
cd shiptracker
```

### 3.2 Configurar `application.properties`

Editar el fichero `src/main/resources/application.properties`:

```properties
# ===== BASE DE DATOS H2 =====
spring.datasource.url=jdbc:h2:file:./data/shiptracker;AUTO_SERVER=TRUE
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=update
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

# ===== AIS STREAM =====
aisstream.api.key=TU-API-KEY-AQUI

# ===== ZONA DE SEGUIMIENTO (Islas Canarias por defecto) =====
aisstream.bbox.minLat=25.0
aisstream.bbox.maxLat=32.0
aisstream.bbox.minLng=-20.5
aisstream.bbox.maxLng=-11.5

# ===== MODO MOCK (true = usar barcos simulados si no hay AIS) =====
marine.api.mock=true

# ===== EMAIL — Alertas =====
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=TU-EMAIL@gmail.com
spring.mail.password=XXXX XXXX XXXX XXXX
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true

# ===== EQUASIS (opcional) =====
equasis.username=TU-EMAIL@ejemplo.com
equasis.password=TU-CONTRASEÑA

# ===== SERVIDOR =====
server.port=8080

# ===== LOGGING =====
logging.level.com.shiptracker=INFO
```

### 3.3 Compilar y ejecutar

```bash
# Compilar y ejecutar directamente con Maven
mvn spring-boot:run

# O bien, compilar el JAR primero
mvn clean package -DskipTests
java -jar target/shiptracker-1.0.0.jar
```

### 3.4 Verificar el arranque

En la consola debe aparecer:

```
INFO  com.shiptracker.service.AisStreamService - AisStreamService init — configurado: true
INFO  com.shiptracker.service.AisStreamService - Conectado a aisstream.io
INFO  com.shiptracker.service.AisStreamService - Suscripción enviada OK — esperando mensajes AIS...
INFO  o.s.b.w.embedded.tomcat.TomcatWebServer  - Tomcat started on port 8080
INFO  com.shiptracker.ShipTrackerApplication   - Started ShipTrackerApplication in X.XXX seconds
```

---

## 4. Acceso a la aplicación

| URL | Descripción |
|-----|-------------|
| `http://localhost:8080/` | Página de inicio |
| `http://localhost:8080/map` | Mapa en vivo |
| `http://localhost:8080/ships` | Listado de barcos |
| `http://localhost:8080/register` | Crear cuenta |
| `http://localhost:8080/login` | Iniciar sesión |
| `http://localhost:8080/favorites` | Mis favoritos (requiere login) |
| `http://localhost:8080/h2-console` | Consola base de datos H2 |
| `http://localhost:8080/api/ais/status` | Estado del sistema AIS (JSON) |

### Acceso a la consola H2

```
URL JDBC: jdbc:h2:file:./data/shiptracker
Usuario:  sa
Contraseña: (dejar en blanco)
```

---

## 5. Estructura de datos generada en disco

Al arrancar por primera vez se crea automáticamente:

```
shiptracker/
└── data/
    ├── shiptracker.mv.db    ← Base de datos H2 (usuarios, favoritos, puertos)
    └── shiptracker.trace.db ← Trazas de H2 (diagnóstico)
```

> Estos ficheros se crean automáticamente gracias a `ddl-auto=update`. No es necesario ejecutar ningún script SQL.

---

## 6. Cambiar la zona de seguimiento AIS

Para monitorizar una zona diferente de las Islas Canarias, modificar el bounding box en `application.properties`:

```properties
# Ejemplo: Estrecho de Gibraltar
aisstream.bbox.minLat=35.5
aisstream.bbox.maxLat=36.5
aisstream.bbox.minLng=-6.5
aisstream.bbox.maxLng=-4.5

# Ejemplo: Puerto de Barcelona
aisstream.bbox.minLat=41.2
aisstream.bbox.maxLat=41.5
aisstream.bbox.minLng=2.1
aisstream.bbox.maxLng=2.4

# Ejemplo: Canal de la Mancha
aisstream.bbox.minLat=50.0
aisstream.bbox.maxLat=52.0
aisstream.bbox.minLng=-2.0
aisstream.bbox.maxLng=2.0
```

También hay que ajustar el centro del mapa en `templates/map.html` (línea que inicializa Leaflet).

---

## 7. Cambiar el puerto del servidor

```properties
server.port=9090
```

Reiniciar la aplicación. El WebSocket del browser también cambia de puerto automáticamente (usa `location.host`).

---

## 8. Despliegue en producción (opcional)

### 8.1 Generar JAR ejecutable

```bash
mvn clean package -DskipTests
```

Se genera: `target/shiptracker-1.0.0.jar` (JAR auto-contenido con Tomcat embebido).

### 8.2 Ejecutar como servicio

```bash
# Linux / Mac
java -jar shiptracker-1.0.0.jar &

# Con perfil de producción y más memoria
java -Xmx512m -jar shiptracker-1.0.0.jar
```

### 8.3 Opción Docker (básica)

Crear `Dockerfile` en la raíz del proyecto:

```dockerfile
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/shiptracker-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
# Construir imagen
docker build -t shiptracker:1.0 .

# Ejecutar contenedor
docker run -p 8080:8080 \
  -e AISSTREAM_API_KEY=tu-clave \
  -v $(pwd)/data:/app/data \
  shiptracker:1.0
```

### 8.4 Variables de entorno para producción

En lugar de poner credenciales en `application.properties`, se pueden usar variables de entorno:

```bash
export AISSTREAM_API_KEY=tu-clave
export SPRING_MAIL_PASSWORD="xxxx xxxx xxxx xxxx"
export SPRING_MAIL_USERNAME=tu@gmail.com
```

Spring Boot lee automáticamente las variables de entorno con el naming convencional (puntos → guiones bajos mayúsculas).

---

## 9. Resolución de problemas frecuentes

| Problema | Causa probable | Solución |
|----------|---------------|---------|
| No aparecen barcos en el mapa | AIS no conectado o sin datos aún | Esperar 30-60 s. Verificar `/api/ais/status`. Si `connected=false`, revisar la API key. El modo mock se activará automáticamente |
| Error 500 al hacer login | Columnas booleanas null en BD existente | Borrar `./data/shiptracker.mv.db` y reiniciar (se pierden los datos) |
| No llegan emails de alerta | Credenciales SMTP incorrectas | Verificar que se usa contraseña de aplicación (no la contraseña de la cuenta Gmail) |
| `Connection refused` al conectar AIS | Sin conexión a internet o aisstream.io caído | La app sigue funcionando con el caché anterior o con mock |
| Puerto 8080 ocupado | Otro proceso usa ese puerto | Cambiar `server.port=8090` en `application.properties` |
| H2 Console muestra tabla vacía | Primera ejecución | Normal — las tablas se crean vacías. Registrar un usuario para poblar `users` |

---

## 10. Pruebas de verificación post-despliegue

Tras el despliegue, ejecutar estos pasos para verificar que todo funciona:

- [ ] `GET http://localhost:8080/` devuelve la página de inicio (HTTP 200)
- [ ] `GET http://localhost:8080/map` muestra el mapa con barcos
- [ ] `GET http://localhost:8080/api/ais/status` devuelve JSON con `"configured": true`
- [ ] `GET http://localhost:8080/api/ships` devuelve array JSON con barcos
- [ ] `POST http://localhost:8080/register` crea un usuario nuevo
- [ ] `POST http://localhost:8080/login` autentica y redirige a `/map`
- [ ] `GET http://localhost:8080/favorites` redirige a login si no autenticado
- [ ] El WebSocket `ws://localhost:8080/ws/ships` se conecta (ver en DevTools → Network → WS)
- [ ] `GET http://localhost:8080/h2-console` muestra la consola H2
- [ ] Tabla `users` tiene la fila del usuario registrado en el paso anterior

---

## 11. Registro de versiones

| Versión | Fecha | Cambios |
|---------|-------|---------|
| 1.0.0 | 2026-05-18 | Versión inicial: mapa en vivo, favoritos, alertas email, modo mock |
