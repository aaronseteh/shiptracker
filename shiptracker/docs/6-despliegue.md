# Documento de Despliegue — ShipTracker
## Actividad 7 · UT2 · "Memoria de implantación"

---

## 1. Requisitos previos

| Herramienta | Versión mínima | Descarga |
|---|---|---|
| Java JDK | 17 o superior | https://adoptium.net |
| Maven | 3.8+ | https://maven.apache.org |
| IDE (opcional) | IntelliJ IDEA / Eclipse / VS Code | — |
| Git (opcional) | cualquiera | https://git-scm.com |
| Navegador moderno | Chrome, Firefox, Edge | — |

**Verificar instalación:**
```bash
java -version   # debe mostrar "17.x.x" o superior
mvn -version    # debe mostrar "Apache Maven 3.x.x"
```

---

## 2. Obtención del proyecto

### Opción A — Desde ZIP
1. Descomprimir el archivo `shiptracker.zip`
2. Abrir una terminal en la carpeta `shiptracker/` (donde está `pom.xml`)

### Opción B — Desde Git
```bash
git clone <url-del-repositorio>
cd shiptracker
```

---

## 3. Estructura del proyecto

```
shiptracker/
├── pom.xml
├── data/                          ← Base de datos H2 (se crea al arrancar)
│   ├── shiptracker.mv.db
│   └── shiptracker.lock.db
├── docs/                          ← Documentación del proyecto
│   ├── 1-alcance.md
│   ├── 2-casos-de-uso.md
│   ├── 3-modelo-datos.md
│   ├── 4-estandar-desarrollo.md
│   ├── 5-presentacion.md
│   ├── 6-defensa.md
│   └── 6-despliegue.md
└── src/
    └── main/
        ├── java/com/shiptracker/
        │   ├── ShipTrackerApplication.java
        │   ├── config/
        │   ├── controller/
        │   ├── dto/
        │   ├── model/
        │   ├── repository/
        │   ├── service/
        │   └── websocket/
        └── resources/
            ├── application.properties
            ├── static/
            │   ├── css/styles.css
            │   ├── js/
            │   └── images/ships/   ← iconos SVG por tipo de barco
            └── templates/
                ├── fragments/
                │   ├── navbar.html
                │   └── footer.html
                ├── index.html
                ├── map.html
                ├── ships.html
                ├── ship-detail.html
                ├── favorites.html
                ├── login.html
                └── register.html
```

---

## 4. Configuración

El archivo de configuración principal es `src/main/resources/application.properties`.

### 4.1 Configuración básica (modo demostración — sin API keys)

La aplicación funciona **sin configuración adicional**. Si no se proporciona API key de aisstream.io, el sistema activa automáticamente 18 barcos de demostración (modo mock):

```properties
# Puerto del servidor
server.port=8080

# Base de datos H2 en fichero (no requiere instalación)
spring.datasource.url=jdbc:h2:file:./data/shiptracker;AUTO_SERVER=TRUE
spring.jpa.hibernate.ddl-auto=update
spring.h2.console.enabled=true

# Modo mock activo si no hay API key de AIS
marine.api.mock=true
```

### 4.2 Configuración con datos AIS reales (aisstream.io)

1. Registrarse gratuitamente en https://aisstream.io
2. Obtener la API key desde el panel de usuario
3. Añadir en `application.properties`:

```properties
# API key de aisstream.io
aisstream.api.key=TU_API_KEY_AQUI

# Zona de seguimiento (Islas Canarias por defecto)
# Cambiar las coordenadas para otra zona del mundo
aisstream.bbox.minLat=25.0
aisstream.bbox.maxLat=32.0
aisstream.bbox.minLng=-20.5
aisstream.bbox.maxLng=-11.5
```

Con la API key configurada, la app se conecta automáticamente a aisstream.io al arrancar y empieza a recibir datos AIS reales.

### 4.3 Configuración con datos técnicos de Equasis (opcional)

Equasis.org es el registro oficial de la OMI, gratuito para uso personal:

1. Registrarse en https://www.equasis.org (cuenta gratuita)
2. Añadir en `application.properties`:

```properties
equasis.username=tu_email@ejemplo.com
equasis.password=tu_contraseña
```

Con esta configuración, la página de detalle de cada barco mostrará datos técnicos oficiales (GT, DWT, año de construcción, operador).

### 4.4 Configuración para producción con MySQL

Añadir al `pom.xml`:
```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
</dependency>
```

Crear la base de datos MySQL:
```sql
CREATE DATABASE shiptracker CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'shiptracker_user'@'localhost' IDENTIFIED BY 'TU_PASSWORD_SEGURA';
GRANT ALL PRIVILEGES ON shiptracker.* TO 'shiptracker_user'@'localhost';
FLUSH PRIVILEGES;
```

Cambiar en `application.properties`:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/shiptracker?useSSL=false&serverTimezone=UTC
spring.datasource.username=shiptracker_user
spring.datasource.password=TU_PASSWORD_SEGURA
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
spring.jpa.hibernate.ddl-auto=update
spring.h2.console.enabled=false
```

---

## 5. Arranque del proyecto

### Opción A — Con Maven (recomendado)
```bash
# Desde la carpeta raíz del proyecto (donde está pom.xml)
mvn spring-boot:run
```

### Opción B — Compilar y ejecutar JAR
```bash
# Compilar (genera target/shiptracker-1.0.0.jar)
mvn clean package -DskipTests

# Ejecutar
java -jar target/shiptracker-1.0.0.jar
```

### Opción C — Desde IntelliJ IDEA
1. File → Open → seleccionar carpeta `shiptracker`
2. Esperar a que Maven descargue dependencias (~2 minutos primera vez)
3. Abrir `src/main/java/com/shiptracker/ShipTrackerApplication.java`
4. Clic en el triángulo verde junto a `main()` → Run

### Opción D — Desde Eclipse
1. File → Import → Maven → Existing Maven Projects
2. Seleccionar la carpeta `shiptracker`
3. Click derecho en el proyecto → Run As → Spring Boot App

### Opción E — Desde VS Code
1. Abrir la carpeta `shiptracker`
2. Instalar extensión "Extension Pack for Java" si no está instalada
3. Abrir `ShipTrackerApplication.java` → clic en "Run" encima del método `main`

---

## 6. Verificación del arranque

En la consola debe aparecer al final:
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.2.3)

...
...Started ShipTrackerApplication in X.XXX seconds (process running for X.X)
```

Si se ha configurado aisstream.io, también aparecerá:
```
INFO  AisStreamService - Conectado a aisstream.io — enviando suscripción...
INFO  AisStreamService - Suscripción enviada OK — esperando mensajes AIS...
```

---

## 7. URLs de acceso

| URL | Descripción | Acceso |
|---|---|---|
| http://localhost:8080 | Página principal con estadísticas | Público |
| http://localhost:8080/map | Mapa interactivo en tiempo real | Público |
| http://localhost:8080/ships | Listado de barcos con búsqueda | Público |
| http://localhost:8080/ships/{mmsi} | Detalle de barco | Público |
| http://localhost:8080/login | Inicio de sesión | Público |
| http://localhost:8080/register | Registro de usuario | Público |
| http://localhost:8080/favorites | Favoritos | Autenticado |
| http://localhost:8080/h2-console | Consola base de datos H2 | Dev |
| http://localhost:8080/api/ships | API REST — lista de barcos (JSON) | Público |
| http://localhost:8080/api/ais/status | Estado de conexión AIS (JSON) | Público |
| ws://localhost:8080/ws/ships | WebSocket actualizaciones en tiempo real | Público |

### Acceso a la consola H2 (solo en desarrollo)
1. Navegar a http://localhost:8080/h2-console
2. **JDBC URL:** `jdbc:h2:file:./data/shiptracker`
3. **User Name:** `sa`
4. **Password:** *(vacío)*
5. Clic en "Connect"

Consultas útiles:
```sql
SELECT * FROM users;
SELECT u.username, f.ship_name, f.notes FROM favorites f JOIN users u ON f.user_id = u.id;
SELECT * FROM ports;
```

---

## 8. Primer uso — Crear una cuenta

1. Navegar a http://localhost:8080/register
2. Rellenar: nombre de usuario (único), email (único), contraseña (mínimo 6 caracteres), confirmación
3. Clic en "Crear cuenta"
4. Iniciar sesión en http://localhost:8080/login
5. Explorar el mapa y añadir barcos a favoritos

---

## 9. Solución de problemas frecuentes

### Error: "Puerto 8080 ya en uso"
```bash
# Windows — encontrar qué proceso usa el puerto 8080
netstat -ano | findstr :8080

# Cambiar el puerto en application.properties
server.port=8090
```

### Error: "Java not found" o versión incorrecta
```bash
java -version
# Si muestra versión < 17 o no aparece → instalar JDK 17+ desde https://adoptium.net
```

### Error de Lombok en IntelliJ IDEA
Settings → Build, Execution, Deployment → Compiler → Annotation Processors → ✓ Enable annotation processing

### Error de Lombok en Eclipse
Ejecutar el JAR de Lombok como instalador: `java -jar lombok.jar` y seguir las instrucciones.

### El mapa no carga los tiles
- Verificar conexión a internet (los tiles de Esri se cargan externamente)
- Como alternativa, cambiar en `map.js` a OpenStreetMap (sin API key):
```javascript
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap contributors'
}).addTo(map);
```

### No aparecen barcos reales (solo mock)
- Verificar que `aisstream.api.key` está configurado en `application.properties`
- Verificar en http://localhost:8080/api/ais/status que `connected: true`
- Los barcos reales aparecen ~10–30 segundos después de conectar

### Error 403 en formularios POST
CSRF está activo. Asegurarse de que los formularios HTML usan `th:action="@{/ruta}"` (no `action="/ruta"` estático), ya que Thymeleaf incluye automáticamente el token CSRF.

### La consola H2 muestra error "iframe"
Está resuelto en `SecurityConfig.java` con `.frameOptions(frame -> frame.sameOrigin())`. Si persiste, verificar que se accede desde el mismo origen (localhost).

### Equasis no devuelve datos técnicos
- Verificar usuario/contraseña de Equasis en `application.properties`
- Comprobar en los logs de inicio: `INFO EquasisService - Login en Equasis exitoso`
- Equasis puede estar temporalmente no disponible

---

## 10. Despliegue en servidor externo (producción)

### En servidor Linux con Java instalado
```bash
# 1. Compilar el JAR en local
mvn clean package -DskipTests

# 2. Transferir al servidor
scp target/shiptracker-1.0.0.jar usuario@servidor:/opt/shiptracker/

# 3. Ejecutar en background con log
nohup java -jar /opt/shiptracker/shiptracker-1.0.0.jar \
  > /var/log/shiptracker.log 2>&1 &

# 4. Ver logs en tiempo real
tail -f /var/log/shiptracker.log
```

### Con Docker (opcional)
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

# Ejecutar contenedor con persistencia de datos
docker run -p 8080:8080 \
  -v $(pwd)/data:/app/data \
  -e AISSTREAM_API_KEY=TU_KEY \
  shiptracker:1.0
```

---

## 11. Checklist de despliegue

- [ ] Java 17+ instalado y verificado (`java -version`)
- [ ] Maven 3.8+ instalado y verificado (`mvn -version`)
- [ ] Proyecto descomprimido/clonado
- [ ] `mvn spring-boot:run` ejecutado sin errores
- [ ] http://localhost:8080 accesible en el navegador
- [ ] Mapa carga con barcos visibles (mock o reales)
- [ ] Registro de usuario funciona
- [ ] Login/Logout funciona
- [ ] Favoritos: añadir, editar nota y eliminar funcionan
- [ ] Consola H2 accesible (modo desarrollo)
- [ ] (Opcional) API key aisstream.io configurada → `api/ais/status` muestra `connected: true`
- [ ] (Opcional) Equasis configurado → detalle de barco muestra GT, DWT, año de construcción

---

*Versión 2.0 — Proyecto académico DAW 2025*
