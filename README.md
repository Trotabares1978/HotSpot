# ARGENTAS HotSpot

Aplicación Android para controlar de forma remota y autorizada el punto de acceso Wi-Fi de una tablet desde teléfonos clientes.

## Objetivo

- App **Servidor** instalada en la tablet Redmi: recibe órdenes autenticadas, informa el estado y administra un apagado automático a los 10 minutos.
- App **Cliente** instalada en los teléfonos autorizados: una pantalla con un único botón Activar/Desactivar y un indicador de conexión con la tablet.
- Comunicación por la red local; no requiere un servicio en la nube para las órdenes.
- Código organizado para que el mecanismo de control del hotspot pueda adaptarse al Android/HyperOS concreto de la tablet.

## Requisito técnico importante

Android limita el control programático del hotspot de tethering. Los permisos normales no garantizan que una app de terceros pueda encenderlo o apagarlo. Antes de dar por funcional ese módulo hay que probarlo en la Redmi Pad real y elegir un mecanismo compatible. El hotspot local de `WifiManager.startLocalOnlyHotspot()` no equivale a compartir internet por tethering y no debe usarse como sustituto sin validar el comportamiento requerido.

## Seguridad y comportamiento

- Solo clientes autorizados pueden enviar órdenes.
- El temporizador de 10 minutos se ejecuta en la tablet, no en el teléfono.
- La interfaz debe distinguir entre **conexión al servidor** y **estado real del hotspot**.
- Si el servidor no puede verificar el estado, debe mostrarlo como desconocido, no asumir que está apagado.
- Debe probarse el comportamiento tras cerrar la app, reiniciar la tablet y perder la conexión.

## Próximos pasos

1. Crear el esqueleto Android y separar `client`, `server` y `hotspot-control`.
2. Implementar el protocolo local y autenticación.
3. Implementar el botón y los indicadores de estado.
4. Probar las opciones de control del hotspot cuando se conozcan el modelo exacto y la versión de Android/HyperOS de la tablet.
5. Configurar la compilación automática de APK mediante GitHub Actions.

## Estado actual

Plan inicial documentado. Todavía no hay una compilación funcional ni se ha validado el control del hotspot en el dispositivo objetivo.
