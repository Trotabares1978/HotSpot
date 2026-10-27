# ARGENTAS · Control de hotspot

Proyecto Android con dos APK generadas por GitHub Actions:

- **ARGENTAS-Invitados.apk**: control remoto desde el celular.
- **ARGENTAS-Control-Tablet.apk**: servidor y panel en la tablet.

## Compilar y descargar

El workflow **Android build** compila los módulos `:client` y `:server`. El artefacto **ARGENTAS-APKs** contiene ambos APK en la raíz del ZIP con nombres claros.

## Requisitos y límites del control

El encendido y apagado del punto de acceso está restringido por Android y puede variar según la versión y la personalización del fabricante (incluido HyperOS). En Android 16, la app intenta la API pública de `TetheringManager` y recurre a Delta/Shizuku si Android rechaza la operación, no devuelve una confirmación a tiempo o lanza una excepción.

Para la vía Delta se requiere que Delta esté instalado en la tablet, Shizuku esté iniciado y autorizado, y la integración Tasker/receptor de Delta esté habilitada. La app envía la acción explícita de Delta, pero ese broadcast no devuelve un resultado directo. Por eso la app intenta comprobar el estado observado del hotspot; si la lectura de estado no funciona en esa versión de Android/HyperOS, el resultado puede quedar como **no confirmado** aunque se haya enviado la orden. Una compilación exitosa no sustituye la prueba en la tablet real.

La indicación de conexión celular-tablet es independiente del estado del hotspot. El servidor considera activa la conexión solo después de recibir un comando autenticado y deja de mostrarla como activa tras 15 segundos sin contacto.

## Red local y seguridad

Ambos dispositivos deben poder comunicarse por la red local. El prototipo usa TCP sin cifrado y un secreto compartido integrado en el código; no debe utilizarse en redes públicas o no confiables. El secreto integrado no es un mecanismo de seguridad robusto frente a alguien que pueda inspeccionar el APK. Para uso fuera de una red estrictamente controlada, se debe implementar autenticación configurable y protección criptográfica del canal.

## Alcance de la validación

CI comprueba que los módulos compilan y que los APK se empaquetan. No puede verificar el comportamiento real del hotspot en una tablet física. La confirmación final requiere probar encendido, apagado, lectura del estado y apagado automático en el dispositivo objetivo.
