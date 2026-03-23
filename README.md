<div align="center">
  <img src="https://img.icons8.com/nolan/256/timer.png" width="100" alt="AI Timer Logo">
  <h1>⚡ AI Session Monitor ⚡</h1>
  <p><b>Un widget flotante y siempre visible para monitorizar el uso de tus sesiones de IA (Claude Code & Gemini Antigravity).</b></p>
  
  [![Java](https://img.shields.io/badge/Java-8%2B-ED8B00?style=for-the-badge&logo=java&logoColor=white)]()
  [![Swing](https://img.shields.io/badge/Swing-UI-blue?style=for-the-badge&logo=java&logoColor=white)]()
  [![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)]()
  
</div>

---

## 📖 Acerca del Proyecto

**AI Session Monitor** es una aplicación de escritorio muy ligera desarrollada en Java (Swing) que se ejecuta como una ventana flotante sin bordes. Su objetivo principal es ayudarte a mantener un control total sobre tus **cuotas y rate-limits** mientras trabajas con asistentes de programación como **Claude Code** y el **Antigravity Daemon de Gemini**.

Especialmente útil si cuentas con planes Pro (ej. Claude Pro con un límite estimado de ~40,000 tokens cada 5 horas) permitiéndote visualizar tu avance en tiempo real para no quedarte sin tokens a mitad del desarrollo.

## ✨ Características Principales

- **👁️ Siempre Visible (Always-On-Top):** Widget flotante sin bordes y con diseño moderno (modo oscuro), ideal para tenerlo en una esquina de tu pantalla.
- **🟣 Integración con Claude Code:**
  - Lee los archivos de sesión locales en `~/.claude/projects`.
  - Calcula tokens de entrada, salida y creación de caché.
  - Alertas visuales con barras de colores dependiendo de lo cerca que estés del límite de tu ventana de 5 horas.
  - Tiempo restante para el reinicio de la cuota.
- **🔵 Integración con Antigravity / Gemini:**
  - Se conecta con el daemon local leyendo los JSON de descubrimiento en `~/.gemini/antigravity/daemon`.
  - Obtiene el uso de la cuota local de la API por HTTP (identificando puertos y tokens CSRF automáticamente).
  - Escanea tu directorio de conversaciones y revisa el conteo de sesiones en la carpeta `~/.gemini/antigravity/conversations`.
- **🖱️ Fácil de Usar:**
  - **Click Izquierdo y arrastrar:** Para mover el widget libremente por la pantalla.
  - **Click Derecho:** Menú contextual para forzar la sincronización (refresh) o cerrar la app.
  - Sincronización transparente en segundo plano cada 30 segundos.

## 🛠️ Requisitos Previos

- Tener instalado **Java** (JDK o JRE versión 8 o superior).
- *Opcional*: Las carpetas `~/.claude/` y `~/.gemini/` generadas por las herramientas requeridas.

## 🚀 Instalación y Uso (Windows)

Si estás en un entorno Windows, hemos preparado unos scripts muy sencillos para ti:

1. **Clonar este repositorio:**
   ```bash
   git clone https://github.com/kalel92/aitimer.git
   cd aitimer
   ```

2. **Compilar el proyecto:**
   Ejecuta el archivo `build.bat` haciendo doble click, o desde tu consola:
   ```cmd
   build.bat
   ```
   *Esto generará los archivos `.class` a partir del `AITimerApp.java`.*

3. **Ejecutar la App:**
   Ejecuta el archivo `run.bat`:
   ```cmd
   run.bat
   ```
   *El programa se lanzará minimizado en tu terminal y aparecerá en la esquina superior derecha de tu pantalla.*

> **Nota para otros SO:** El código es 100% Java estándar. En Linux o macOS, puedes compilarlo con `javac AITimerApp.java` y correrlo con `java AITimerApp`.

## 🎨 Apariencia

El monitor cuenta con barras progresivas:
- **🟣 Morado / 🔵 Azul:** Uso normal.
- **🟡 Amarillo / Naranja:** Acercándose al límite de uso (>65%).
- **🔴 Rojo:** Límite casi alcanzado o alcanzado (>90%). Daemons desconectados.

## 🤝 Contribución

¡Cualquier mejora es bienvenida! Si usas otros asistentes con cuotas locales (ej. Cursor, GitHub Copilot CLI), siéntete libre de hacer un _fork_, abrir un _Pull Request_ agregando nuevas integraciones o abrir un _Issue_ con sugerencias.

## 📜 Licencia

Este proyecto se distribuye bajo la licencia MIT. Eres libre de usarlo, modificarlo y distribuirlo con fines personales o comerciales.
