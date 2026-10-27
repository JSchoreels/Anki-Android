**Changes since release 27**

- **Calibration graphs: honest only, with a progress bar.** FSRS + RWKV Calibration and FSRS vs RWKV UM+ now always compare both on reviews FSRS was not trained on (its test folds, as on the desktop), worked out by themselves when they are missing or your FSRS settings changed. The "Calibration on test folds" menu item is gone.
- **Progress and time left.** A bar in the calibration graph shows each step (FSRS's test folds, then RWKV going through your whole history once) and the time left, and the graphs redraw themselves when it is done. You can leave the statistics meanwhile.
- **Asks first when it is long.** RWKV's pass over your whole history is done once per model and kept afterwards, but with the big model and millions of reviews it can take most of an hour, and reviews use the standard order meanwhile: when it would take more than 5 minutes, the bar asks before starting.
- **Faster to open.** The calibration graphs no longer wait for the page to replay every review first (minutes for big collections); the other FSRS graphs keep their own "Show?".

<!-- es -->
**Cambios desde la versión 27**

- **Gráficos de calibración: solo el método honesto, con barra de progreso.** FSRS + RWKV Calibration y FSRS vs RWKV UM+ ahora comparan siempre a ambos en repasos con los que FSRS no se entrenó (sus pliegues de prueba, como en el escritorio), calculados solos cuando faltan o cambian tus ajustes de FSRS. Ya no está la opción "Calibración con pliegues de prueba" del menú.
- **Progreso y tiempo restante.** Una barra en el gráfico de calibración muestra cada paso (los pliegues de prueba de FSRS y luego RWKV recorriendo todo tu historial una vez) y el tiempo restante, y los gráficos se redibujan solos al terminar. Mientras tanto puedes salir de las estadísticas.
- **Pregunta antes si va a tardar.** El recorrido de RWKV por todo tu historial se hace una vez por modelo y se guarda, pero con el modelo grande y millones de repasos puede tardar casi una hora, y mientras tanto los repasos usan el orden normal: si va a tardar más de 5 minutos, la barra pregunta antes de empezar.
- **Abre más rápido.** Los gráficos de calibración ya no esperan a que la página repase todos tus repasos primero (minutos en colecciones grandes); los demás gráficos de FSRS mantienen su propio "Show?".
