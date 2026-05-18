/* ============================================================
   ShipTracker — map.js
   Mapa interactivo con Leaflet.js + actualizaciones en tiempo real
   ============================================================ */

const TYPE_COLORS = {
  'Cargo':           '#3b82f6',
  'Tanque':          '#f59e0b',
  'Pasajeros':       '#22c55e',
  'RoRo':            '#a855f7',
  'Pesca':           '#f97316',
  'Alta velocidad':  '#ec4899',
  'Especial':        '#6366f1',
};

const STATUS_COLORS = {
  'En navegación': '#22c55e',
  'Fondeado':      '#f59e0b',
  'Atracado':      '#3b82f6',
};

function getShipColor(ship) {
  return STATUS_COLORS[ship.status] || '#94a3b8';
}

// ── Inicializar mapa ────────────────────────────────────────
const map = L.map('map').setView([28.3, -15.5], 8);

L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Ocean/World_Ocean_Base/MapServer/tile/{z}/{y}/{x}', {
  attribution: 'Tiles &copy; Esri | Sources: GEBCO, NOAA, NGA, &amp; OpenStreetMap',
  maxZoom: 14,
}).addTo(map);

L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Ocean/World_Ocean_Reference/MapServer/tile/{z}/{y}/{x}', {
  maxZoom: 14,
  opacity: 0.7,
}).addTo(map);

// ── Estado de barcos ────────────────────────────────────────
// Mapa MMSI → ShipDTO para acceso O(1)
const ships = new Map(shipsData.map(s => [s.mmsi, s]));
const markers = {};

// ── Popup ───────────────────────────────────────────────────
function buildPopup(ship) {
  const isFav = Array.isArray(favoriteMmsis) && favoriteMmsis.includes(ship.mmsi);
  return `
    <div class="ship-popup">
      <div class="popup-header">
        <strong>${ship.name || 'Sin nombre'}</strong>
        <span class="popup-flag">${ship.flag || '??'}</span>
      </div>
      <table class="popup-table">
        <tr><td>Tipo</td><td>${ship.type || 'N/D'}</td></tr>
        <tr><td>Estado</td><td>${ship.status || 'N/D'}</td></tr>
        <tr><td>Velocidad</td><td>${ship.speed != null ? ship.speed + ' kn' : 'N/D'}</td></tr>
        <tr><td>Rumbo</td><td>${ship.course != null ? ship.course + '°' : 'N/D'}</td></tr>
        <tr><td>Destino</td><td>${ship.destination || 'N/D'}</td></tr>
        <tr><td>MMSI</td><td>${ship.mmsi}</td></tr>
      </table>
      ${isFav ? '<p class="popup-fav">⭐ En favoritos</p>' : ''}
      <a href="/ships/${ship.mmsi}" class="popup-link">Ver detalles &rarr;</a>
    </div>
  `;
}

// ── Marcadores ──────────────────────────────────────────────
function addShipMarker(ship) {
  if (!ship.latitude || !ship.longitude) return;
  const color  = getShipColor(ship);
  const radius = ship.type === 'Pasajeros' ? 8 : 7;
  const marker = L.circleMarker([ship.latitude, ship.longitude], {
    color, fillColor: color, fillOpacity: 0.85, radius, weight: 2,
  }).addTo(map);
  marker.bindPopup(buildPopup(ship), { maxWidth: 280 });
  markers[ship.mmsi] = { marker, ship };
}

function updateShipMarker(ship) {
  const entry = markers[ship.mmsi];
  if (!entry) {
    addShipMarker(ship);
    addListItem(ship);
    return;
  }
  // Mover marcador y refrescar popup
  if (ship.latitude && ship.longitude) {
    entry.marker.setLatLng([ship.latitude, ship.longitude]);
  }
  const color = getShipColor(ship);
  entry.marker.setStyle({ color, fillColor: color });
  entry.marker.setPopupContent(buildPopup(ship));
  entry.ship = ship;
  updateListItem(ship);
}

// Render inicial
shipsData.forEach(addShipMarker);

// ── Lista lateral ───────────────────────────────────────────
const shipList    = document.getElementById('shipList');
const searchInput = document.getElementById('searchInput');
const typeFilter  = document.getElementById('typeFilter');
const shipCountEl = document.getElementById('shipCount');

function createListItem(ship) {
  const li = document.createElement('li');
  li.className = 'ship-list-item';
  li.dataset.mmsi = ship.mmsi;
  li.dataset.lat  = ship.latitude  || '';
  li.dataset.lng  = ship.longitude || '';
  li.onclick = () => focusShip(li);
  li.innerHTML = `
    <div class="list-item-header">
      <span class="list-ship-name">${ship.name || 'Sin nombre'}</span>
      <span class="list-ship-flag">${ship.flag || '??'}</span>
    </div>
    <div class="list-item-meta">
      <span class="list-type-tag">${ship.type || 'N/D'}</span>
      <span class="list-status ${statusClass(ship.status)}">${ship.status || 'N/D'}</span>
    </div>
  `;
  return li;
}

function addListItem(ship) {
  shipList.appendChild(createListItem(ship));
  shipCountEl.textContent = ships.size + ' barcos';
}

function updateListItem(ship) {
  const item = shipList.querySelector(`[data-mmsi="${ship.mmsi}"]`);
  if (!item) return;
  item.dataset.lat = ship.latitude  || '';
  item.dataset.lng = ship.longitude || '';
  const nameEl   = item.querySelector('.list-ship-name');
  const statusEl = item.querySelector('.list-status');
  if (nameEl)   nameEl.textContent = ship.name || 'Sin nombre';
  if (statusEl) {
    statusEl.textContent  = ship.status || 'N/D';
    statusEl.className    = 'list-status ' + statusClass(ship.status);
  }
}

function statusClass(status) {
  if (status === 'En navegación') return 'green';
  if (status === 'Fondeado')      return 'yellow';
  if (status === 'Atracado')      return 'blue';
  return '';
}

function updateList(filtered) {
  let visible = 0;
  shipList.querySelectorAll('.ship-list-item').forEach(item => {
    const ship = ships.get(item.dataset.mmsi);
    if (!ship) { item.style.display = 'none'; return; }
    const ok = (!filtered.search || (ship.name || '').toLowerCase().includes(filtered.search))
            && (!filtered.type   || (ship.type || '').toLowerCase() === filtered.type.toLowerCase());
    item.style.display = ok ? '' : 'none';
    if (ok) visible++;

    const entry = markers[ship.mmsi];
    if (entry) {
      if (ok) { if (!map.hasLayer(entry.marker)) entry.marker.addTo(map); }
      else    { if (map.hasLayer(entry.marker))  map.removeLayer(entry.marker); }
    }
  });
  shipCountEl.textContent = visible + ' barcos';
}

searchInput.addEventListener('input',  () => applyFilters());
typeFilter.addEventListener('change',  () => applyFilters());

function applyFilters() {
  updateList({ search: searchInput.value.toLowerCase().trim(), type: typeFilter.value });
}

// ── Enfocar barco desde la lista ────────────────────────────
window.focusShip = function(el) {
  const lat  = parseFloat(el.dataset.lat);
  const lng  = parseFloat(el.dataset.lng);
  const mmsi = el.dataset.mmsi;
  if (isNaN(lat) || isNaN(lng)) return;
  document.querySelectorAll('.ship-list-item').forEach(i => i.classList.remove('active-item'));
  el.classList.add('active-item');
  map.flyTo([lat, lng], 7, { animate: true, duration: 1.2 });
  const entry = markers[mmsi];
  if (entry) entry.marker.openPopup();
};

// ── WebSocket — actualizaciones en tiempo real ──────────────
const wsStatusEl = document.getElementById('wsStatus');

function setWsStatus(state) {
  const cfg = {
    connecting:   { text: '● Conectando...',   cls: 'ws-connecting'   },
    connected:    { text: '● Tiempo real',      cls: 'ws-connected'    },
    disconnected: { text: '● Desconectado',     cls: 'ws-disconnected' },
  };
  const s = cfg[state] || cfg.disconnected;
  if (wsStatusEl) {
    wsStatusEl.textContent = s.text;
    wsStatusEl.className   = 'ws-status ' + s.cls;
  }
}

function handleShipUpdate(ship) {
  if (!ship || !ship.mmsi) return;
  ships.set(ship.mmsi, ship);
  updateShipMarker(ship);
}

let ws;

function connectWs() {
  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
  ws = new WebSocket(`${proto}//${location.host}/ws/ships`);

  ws.onopen = () => {
    setWsStatus('connected');
    sendViewport(); // enviar viewport inicial al conectar
  };

  ws.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data);
      if (msg.type === 'snapshot') {
        msg.ships.forEach(handleShipUpdate);
      } else if (msg.type === 'update') {
        handleShipUpdate(msg.ship);
      }
    } catch (e) { /* ignorar mensajes malformados */ }
  };

  ws.onclose = () => {
    setWsStatus('disconnected');
    setTimeout(connectWs, 5000);
  };

  ws.onerror = () => setWsStatus('disconnected');
}

connectWs();

// ── Viewport dinámico ───────────────────────────────────────
function sendViewport() {
  if (!ws || ws.readyState !== WebSocket.OPEN) return;
  const b = map.getBounds();
  ws.send(JSON.stringify({
    type:   'viewport',
    minLat: b.getSouth(),
    maxLat: b.getNorth(),
    minLng: b.getWest(),
    maxLng: b.getEast()
  }));
}

let viewportTimer = null;
map.on('moveend', () => {
  clearTimeout(viewportTimer);
  viewportTimer = setTimeout(sendViewport, 800); // espera 800ms tras soltar el mapa
});

// ── Estilos de popup inline ─────────────────────────────────
const style = document.createElement('style');
style.textContent = `
  .ship-popup { font-family: 'Inter', sans-serif; min-width: 220px; }
  .popup-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; padding-bottom: 8px; border-bottom: 1px solid #e2e8f0; }
  .popup-header strong { font-size: .95rem; color: #0a1628; }
  .popup-flag { font-size: .82rem; font-weight: 700; color: #475569; }
  .popup-table { width: 100%; border-collapse: collapse; font-size: .82rem; }
  .popup-table td { padding: 3px 4px; }
  .popup-table td:first-child { color: #475569; width: 70px; }
  .popup-table td:last-child { font-weight: 500; color: #1e293b; }
  .popup-fav { margin-top: 8px; font-size: .8rem; color: #d97706; font-weight: 600; }
  .popup-link { display: block; margin-top: 10px; text-align: center; background: #00b4d8; color: #0a1628; padding: 6px; border-radius: 6px; font-size: .82rem; font-weight: 700; text-decoration: none; }
  .popup-link:hover { background: #90e0ef; }
  .sidebar-ws-status { padding: 4px 16px 8px; }
  .ws-status { font-size: .75rem; font-weight: 600; }
  .ws-connecting   { color: #94a3b8; }
  .ws-connected    { color: #22c55e; }
  .ws-disconnected { color: #ef4444; }
`;
document.head.appendChild(style);
