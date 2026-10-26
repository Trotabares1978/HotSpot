# ARGENTAS - Invitados

Aplicación Android en desarrollo para controlar el punto de acceso Wi-Fi de una tablet desde teléfonos autorizados.

## Estado del proyecto

**Prototipo inicial, todavía no apto para uso en el negocio.** El proyecto Android y el protocolo de red local están en construcción. El control real del hotspot aún no está implementado: Android/HyperOS restringe el control de tethering para aplicaciones comunes y hay que validar el método en la Redmi Pad concreta.

## Diseño

- Un mismo APK puede arrancar como **servidor** en la tablet o funcionar como **cliente** en un celular.
- El servidor escucha en el puerto TCP local `38471`.
- El cliente envía órdenes `ON`, `OFF` y `STATUS`.
- El protocolo requiere un PIN compartido de al menos 6 caracteres.
- La interfaz muestra el estado de conexión separado del estado real del hotspot.
- El apagado de seguridad está configurado a 10 minutos en el prototipo, pero por ahora es un temporizador en memoria y no garantiza sobrevivir al cierre del proceso o reinicio del dispositivo.

## Compilación

El workflow de GitHub Actions intenta generar un APK de depuración con `gradle assembleDebug` cuando se actualiza `main`. El artefacto, si la compilación termina correctamente, se publica como `argentas-invitados-debug`.

## Prueba prevista cuando esté disponible la tablet

1. Confirmar modelo y versión de Android/HyperOS.
2. Compilar e instalar el APK en la Redmi Pad y en un teléfono de prueba.
3. En la tablet, introducir un PIN local y arrancar el servidor.
4. Comprobar que ambos dispositivos están en una red que permite comunicación entre clientes.
5. En el celular, introducir la IP de la tablet y el mismo PIN.
6. Probar autenticación, conectividad y comandos antes de habilitar el control real del hotspot.
7. Implementar y validar el adaptador de hotspot y un temporizador resistente a cierres/reinicios.

## Seguridad

El PIN es una protección inicial para la red local, no una solución de seguridad de producción. El tráfico actual no está cifrado; usar solo en una red de confianza durante pruebas. Antes del uso real se debe añadir emparejamiento seguro, protección contra intentos repetidos y transporte autenticado/cifrado cuando sea viable.

## Estructura

- `app/`: aplicación Android Kotlin.
- `.github/workflows/android.yml`: compilación automática del APK.
