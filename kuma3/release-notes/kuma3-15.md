**Changes since release 14**

For everyone:

- **Check media no longer trashes sound files that cards play with `[audio:…]` tags.** Before, "Delete unused" moved them to the trash and synced the removal to every device. Deleting unused media now runs in batches with a done/total count, and Empty trash shows done/total too.
- No more false "Media sync failed" on Android 17.
- Deck list: decks with nothing due fold into one row ("▸ 3 decks done for today", tap to show them; searching shows every deck). The "Studied … today" line and the RWKV forecast are in the list after the decks instead of a bar at the bottom, where the + button covered them. The heatmap shows half a year around today (18 weeks back, 8 ahead) instead of January to December. The title says kuma3. Coming back from the reviewer, the list updates in place instead of sliding rows over each other for a moment.
- The RWKV forecast reads in plain lines: "43 below target: 35 ready, 8 waiting", "42 close · 214 safe", "If you stop now: +0 in 1 h · +1 in 3 h · 51 by tomorrow". In the RWKV recall chart, "close" cards are a deeper orange with a pale band just right of the red line, and the end labels (60%, 100%) are no longer cut off.
- Light themes: zero counts, forecast colours and heatmap numbers are darker and easier to read (4.5:1 contrast or more).
- Reviewer: undo no longer undoes choosing the deck (it silently switched you to another deck; now it says "Nothing left to undo in this review"). The undo message stays long enough to read, and the undo button has a name for screen readers.

New day theme **Anki XP** (Settings > Appearance > Day theme):

- kuma3 looks like Windows XP: Luna blue title bars with XP caption buttons, the Tahoma font (Wine's free copy), XP push buttons, dialogs as XP message boxes, XP progress windows, balloon tips, XP-style statistics, deck options and congrats pages, and a retro CRT screen on every screen, menu and dialog (can be turned off).
- The deck list is a set of XP group boxes: Player (level, XP bar, streak, daily goal, Quests and Trophies), Decks, Today (studied today + forecast), RWKV recall and Activity.
- Reviews are the Anki XP game from kuma3 Skins: XP for every answer, a combo for answers within 10 s (the clock starts after the word audio; a tip explains it the first time), ×2 to ×5 multipliers, 1.5× overtime after the daily goal, a boss card per deck per day (×3 XP), three daily quests, levels, 17 trophies and 8-bit sounds. The card fills the screen under a game bar. A session report shows when you leave the reviewer.
- The streak is your review streak (the same as the heatmap's). Undoing an answer takes back its XP, rep, quest progress and boss win.
- XP and trophies are shared with kuma3 Skins through the same "🐻 Anki XP progress" notes, so every app and device adds up to one total.
- Switching the theme on or off takes effect right away.

<!-- es -->
**Cambios desde la versión 14**

Para todos:

- **Check media ya no manda a la papelera los audios que las tarjetas reproducen con etiquetas `[audio:…]`.** Antes, "Delete unused" los movía a la papelera y la sincronización los borraba en todos los dispositivos. Borrar los medios sin usar ahora va por partes con un contador hecho/total, y Empty trash también muestra hecho/total.
- Ya no aparece un falso "Media sync failed" en Android 17.
- Lista de mazos: los mazos sin nada pendiente se pliegan en una fila ("▸ 3 decks done for today", tócala para verlos; al buscar se ven todos). La línea "Studied … today" y el pronóstico de RWKV están en la lista después de los mazos, en vez de en una barra abajo donde el botón + los tapaba. El mapa de actividad muestra medio año alrededor de hoy (18 semanas atrás, 8 adelante) en vez de enero a diciembre. El título dice kuma3. Al volver de los repasos, la lista se actualiza en su sitio en vez de mover filas unas sobre otras por un momento.
- El pronóstico de RWKV se lee en líneas simples: "43 below target: 35 ready, 8 waiting", "42 close · 214 safe", "If you stop now: +0 in 1 h · +1 in 3 h · 51 by tomorrow". En el gráfico RWKV recall, las tarjetas "close" son de un naranja más oscuro con una franja clara justo a la derecha de la línea roja, y las etiquetas de los extremos (60%, 100%) ya no salen cortadas.
- Temas claros: los ceros, los colores del pronóstico y los números del mapa de actividad son más oscuros y se leen mejor (contraste de 4,5:1 o más).
- Repasos: deshacer ya no deshace la elección del mazo (te cambiaba a otro mazo sin avisar; ahora dice "Nothing left to undo in this review"). El mensaje de deshacer dura lo suficiente para leerlo, y el botón de deshacer tiene nombre para lectores de pantalla.

Nuevo tema de día **Anki XP** (Settings > Appearance > Day theme):

- kuma3 se ve como Windows XP: barras de título azul Luna con botones XP, la letra Tahoma (la copia libre de Wine), botones XP, diálogos como cuadros de mensaje de XP, ventanas de progreso XP, globos, páginas de estadísticas, opciones del mazo y felicitación al estilo XP, y una pantalla CRT retro en cada pantalla, menú y diálogo (se puede apagar).
- La lista de mazos son cuadros de grupo de XP: Player (nivel, barra de XP, racha, meta diaria, Quests y Trophies), Decks, Today (lo estudiado hoy + pronóstico), RWKV recall y Activity.
- Los repasos son el juego de Anki XP de kuma3 Skins: XP por cada respuesta, un combo por responder en 10 s (el reloj empieza después del audio de la palabra; un consejo lo explica la primera vez), multiplicadores de ×2 a ×5, 1,5× de horas extra después de la meta diaria, una tarjeta jefe por mazo y día (×3 XP), tres misiones diarias, niveles, 17 trofeos y sonidos de 8 bits. La tarjeta ocupa la pantalla bajo una barra de juego. Al salir de los repasos se muestra un informe de la sesión.
- La racha es tu racha de repasos (la misma del mapa de actividad). Deshacer una respuesta devuelve su XP, el repaso, el progreso de misiones y la victoria contra el jefe.
- El XP y los trofeos se comparten con kuma3 Skins mediante las mismas notas "🐻 Anki XP progress", así que todas las apps y dispositivos suman un solo total.
- Activar o desactivar el tema tiene efecto al momento.
