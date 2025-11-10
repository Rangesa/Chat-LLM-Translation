# Project.md - Chat LLM Translation Project

## Project Overview

This is a **Minecraft Fabric Mod** designed to provide real-time chat translation using a locally hosted Large Language Model (LLM). It integrates the `llama.cpp` engine to run the Gemma 3 4B model, ensuring all translation happens privately on the user's machine.

The project follows a client-server architecture:
-   **The Mod (Client):** Written in Java using the Fabric toolchain. It captures Minecraft chat messages, sends them to a local LLM server, and displays the translated text in-game.
-   **The LLM Server (Server):** A standard `llama.cpp` server instance (`llama-server.exe`) that exposes an OpenAI-compatible REST API. The mod communicates with this server via HTTP requests.

Key features include a translation cache, a Retrieval-Augmented Generation (RAG) system to improve accuracy over time from past translations, and a configuration screen accessible via ModMenu.

## Technical Specifications

-   **Minecraft Version:** `1.21.10`
-   **Fabric Loader Version:** `0.17.3`
-   **Fabric API Version:** `0.136.0+1.21.10`
-   **Java Version:** `21`
-   **LLM Backend:** `llama.cpp`
-   **LLM Model:** Gemma 3 4B (or other GGUF-compatible models)

## Building and Running

There are two main components to run: the **LLM Server** and the **Minecraft Client**.

### 1. Running the LLM Server

The project includes a convenient batch script to manage the LLM server.

1.  **Place Model:** Ensure your desired GGUF model file (e.g., `gemma-3-4b-it-Q4_K_M.gguf`) is placed in the `models/` directory.
2.  **Run Script:** Execute the `start-llama-server.bat` script. It will automatically detect models in the `models/` folder and prompt you to select one.
3.  **Server Ready:** The script will start `llama-server.exe` with the correct parameters. The server will be available at `http://localhost:8080`.

### 2. Building and Running the Mod

You can run the mod in a development environment or build the JAR for distribution.

**To run in the development environment:**

```bash
# This command will launch the Minecraft client with the mod loaded.
./gradlew runClient
```

**To build the JAR file:**

```bash
# This command will compile the code and create the mod JAR.
./gradlew build
```

The final JAR file will be located in `build/libs/`. You can then install it by copying it to the `mods/` folder of a standard Fabric Minecraft installation.

## Development Conventions

### Code Structure

The core logic is organized into several packages under `jp.chat_llm_translation`:

-   `client/`: Contains the client-side entrypoint (`Chat_llm_translationClient.java`) which hooks into chat events.
-   `chat/`: Holds the central `ChatHandler.java` class, responsible for orchestrating the translation process (caching, RAG, LLM calls).
-   `llm/`: Includes `LLMClient.java`, which handles the HTTP communication with the `llama-server` using Java's built-in `HttpClient`.
-   `config/`: Manages configuration loading (`ModConfig.java`) and ModMenu integration.
-   `rag/`: Implements the Retrieval-Augmented Generation logic for learning from past translations.

### Key Commands

-   **Build:** `./gradlew build`
-   **Run Client:** `./gradlew runClient`
-   **Clean:** `./gradlew clean`
-   **Start LLM Server:** `start-llama-server.bat`

### Dependencies

-   Dependencies are managed by Gradle and defined in `build.gradle` and `gradle.properties`.
-   The project uses the Fabric API for modding and ModMenu for in-game configuration.
-   HTTP communication relies on the standard Java 21 `HttpClient`, and JSON processing uses the `Gson` library (which is bundled with Minecraft).
