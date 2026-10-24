**Which file?**
- `kuma3-anki-arm64.apk`: **kuma3 Anki**, the app to use. Fast release build with its own icon, and its own collection folder (`kuma3`), so it installs next to AnkiDroid.
- The older debug app (`com.ichi2.anki.debug`) is no longer published: move to kuma3 Anki (sync the debug app first, then sign in to AnkiWeb in kuma3 Anki and choose download).

**What is in it**
AnkiDroid 2.26.0alpha2 built against the JSchoreels FSRS-7 fork of Anki (26.09.3+fsrs7, build 107), with RWKV-Instant and RWKV-Curve running on the device. Unofficial personal build: not from the AnkiDroid team, the Anki project or the fork's author.
- RWKV-Instant: presets with "Use RWKV-Instant to choose review cards" pick review cards by predicted recall, as on the fork's desktop.
- RWKV-Curve: presets set to RWKV-Curve (or Instant with Curve as its due-date calculator) get Curve's answer intervals, as on the fork's desktop, and deck options has "Reschedule Cards with RWKV-Curve Intervals".
- FSRS-7, with the fork's own repair of the state that AnkiWeb sync drops (`s_int`, `s_fast`).
- Deck options page working on phones, with "Rebuild RWKV State" and a status line.
- Deck list extras, each switchable in Settings > kuma3: review heatmap, time answered today per deck, leaderboard (the desktop Anki Leaderboard add-on's server; off until you turn it on).
- Custom study button on the finished-deck screen.

**Before you install**
- arm64 phones and tablets only.
- kuma3 Anki keeps its own collection: open it, allow "All files access", sign in to AnkiWeb and choose download. If you also keep another Anki app, study in one at a time and sync before switching.
- Sync before uninstalling. Keep AnkiWeb sync on so your collection has a copy elsewhere.
- Big collection (thousands of reviews due) and a wait after each answer with RWKV-Instant? In the deck's options
  (RWKV section) turn on "Use faster, approximate queue updates", or raise "Update the RWKV queue every" N answers.
  On a 22,000-card collection the wait went from about 2 s to under 0.3 s.

<!-- es -->
**¿Qué archivo?**
- `kuma3-anki-arm64.apk`: **kuma3 Anki**, la app que hay que usar. Versión rápida, con su propio icono y su propia carpeta de colección (`kuma3`), así que se instala junto a AnkiDroid.
- La app anterior (`com.ichi2.anki.debug`) ya no se publica: pásate a kuma3 Anki (sincroniza antes la app anterior, luego inicia sesión en AnkiWeb en kuma3 Anki y elige descargar).

**Qué incluye**
AnkiDroid 2.26.0alpha2 compilado con el fork FSRS-7 de Anki de JSchoreels (26.09.3+fsrs7, build 107), con RWKV-Instant y RWKV-Curve funcionando en el dispositivo. Versión personal no oficial: no es del equipo de AnkiDroid, del proyecto Anki ni del autor del fork.
- RWKV-Instant: los presets con "Use RWKV-Instant to choose review cards" eligen las tarjetas de repaso según el recuerdo previsto, como en el escritorio del fork.
- RWKV-Curve: los presets con RWKV-Curve (o Instant con Curve como calculador de fechas) reciben los intervalos de respuesta de Curve, como en el escritorio del fork, y las opciones del mazo tienen "Reschedule Cards with RWKV-Curve Intervals".
- FSRS-7, con la reparación del propio fork para el estado que la sincronización con AnkiWeb pierde (`s_int`, `s_fast`).
- La página de opciones del mazo funciona en móviles, con "Rebuild RWKV State" y una línea de estado.
- Extras en la lista de mazos, cada uno se puede apagar en Ajustes > kuma3: mapa de calor de repasos, tiempo respondido hoy por mazo, leaderboard (el servidor del complemento Anki Leaderboard de escritorio; apagado hasta que lo actives).
- Botón de estudio personalizado en la pantalla de mazo terminado.

**Antes de instalar**
- Solo para móviles y tablets arm64.
- kuma3 Anki tiene su propia colección: ábrela, permite "Acceso a todos los archivos", inicia sesión en AnkiWeb y elige descargar. Si usas otra app de Anki, estudia en una sola a la vez y sincroniza antes de cambiar.
- Sincroniza antes de desinstalar. Mantén la sincronización con AnkiWeb para que tu colección tenga una copia en otro lugar.
- ¿Colección grande (miles de repasos pendientes) y una espera después de cada respuesta con RWKV-Instant? En las
  opciones del mazo (sección RWKV) activa "Use faster, approximate queue updates" o sube "Update the RWKV queue every"
  N respuestas. En una colección de 22.000 tarjetas la espera bajó de unos 2 s a menos de 0,3 s.
