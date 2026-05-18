# ShipTracker

**ShipTracker** es una aplicación web que permite rastrear barcos en tiempo real sobre un mapa interactivo. Consume datos AIS (*Automatic Identification System*), la señal que emiten los propios buques para identificarse y reportar su posición, y los muestra directamente en el navegador sin necesidad de recargar la página.

---

## ¿Qué es AIS?

AIS es el sistema de identificación automática que llevan todos los barcos de cierto tonelaje obligatoriamente. Emiten su posición, velocidad, rumbo, nombre y destino de forma continua. ShipTracker se conecta a esa señal en tiempo real a través de la API pública de [aisstream.io](https://aisstream.io).

---

## ¿Qué puedes hacer con ShipTracker?

### Sin registrarte
- Ver un mapa en vivo con los barcos activos en la zona de las Islas Canarias
- Consultar el listado de barcos y filtrarlo por nombre o tipo (carga, tanquero, pasajeros, pesca…)
- Ver la ficha completa de cualquier barco: MMSI, bandera, velocidad, rumbo, destino, dimensiones y foto

### Con cuenta registrada
- Guardar barcos en tu lista de favoritos con notas personales
- Activar alertas por email que te avisan cuando un barco favorito **zarpa** o **atraca**

---

## ¿Para qué sirve?

ShipTracker nació como proyecto de fin de ciclo DAW con un objetivo claro: demostrar que se puede construir un sistema de seguimiento marítimo funcional y gratuito usando únicamente tecnologías open-source y APIs públicas, sin depender de plataformas de pago como MarineTraffic o VesselFinder.

Es útil para cualquier persona curiosa sobre el tráfico marítimo, especialmente en entornos insulares o portuarios donde los movimientos de barcos son parte del día a día.

---

## Tecnologías

| Capa | Tecnología |
|------|-----------|
| Back-end | Java 17 + Spring Boot 3 |
| Base de datos | H2 (embebida, sin instalación) |
| Seguridad | Spring Security + BCrypt |
| Mapa | Leaflet.js + OpenStreetMap |
| Tiempo real | WebSocket (AIS → servidor → navegador) |
| Plantillas | Thymeleaf |
| Alertas | Gmail SMTP |
| Datos AIS | [aisstream.io](https://aisstream.io) |

---

## Cómo ejecutarlo

**Requisitos:** Java 17 y Maven instalados.

```bash
git clone https://github.com/aaronseteh/shiptracker.git
cd shiptracker/shiptracker
mvn spring-boot:run
```

La aplicación arranca en `http://localhost:8080`.

> Para ver barcos reales necesitas una API key gratuita de [aisstream.io](https://aisstream.io) y configurarla en `application.properties`. Sin ella, la app funciona con datos de ejemplo.

---

## Autor

**Aaron Del Toro Arias** · Proyecto DAW-N 2026
