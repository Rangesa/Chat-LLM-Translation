@echo off
setlocal enabledelayedexpansion

REM ============================================================
REM llama.cpp Server Startup Script
REM ============================================================

set "LLAMA_SERVER_PATH=llama-server.exe"
set "PORT=8080"
set "CONTEXT_SIZE=4096"
set "PARALLEL=4"
set "N_GPU_LAYERS=-1"
set "BATCH_SIZE=512"
set "THREADS=8"

REM ============================================================
REM Multi-GPU Configuration
REM ============================================================
REM Set which GPU to use for LLM inference
REM Default: GPU 0 (change to 1, 2, etc. for other GPUs)
REM Leave empty to use all available GPUs
set "LLM_GPU_ID=1"
set "MAIN_GPU=0"

REM ============================================================
REM Check models folder
REM ============================================================

if not exist "models\" (
    echo [ERROR] models folder not found
    pause
    exit /b 1
)

REM ============================================================
REM Model Selection
REM ============================================================

echo ============================================================
echo Model Selection
echo ============================================================
echo.

set count=0
for %%F in (models\*.gguf) do (
    set /a count+=1
    set "model!count!=%%F"
    echo [!count!] %%~nxF
)

if %count% equ 0 (
    echo [ERROR] No GGUF files found
    pause
    exit /b 1
)

echo.
echo ============================================================

if %count% equ 1 (
    set "SELECTED_MODEL=!model1!"
    echo Using: !SELECTED_MODEL!
) else (
    set /p "selection=Enter model number [1-%count%]: "

    set "valid=0"
    for /l %%i in (1,1,%count%) do (
        if "!selection!"=="%%i" (
            set "SELECTED_MODEL=!model%%i!"
            set "valid=1"
        )
    )

    if !valid! equ 0 (
        echo [ERROR] Invalid selection
        pause
        exit /b 1
    )
)

echo.
echo Selected: !SELECTED_MODEL!
echo.
pause

REM ============================================================
REM Start Server
REM ============================================================

echo ============================================================
echo Starting llama.cpp Server
echo ============================================================
echo Model:    !SELECTED_MODEL!
echo Port:     %PORT%
echo Context:  %CONTEXT_SIZE% tokens
echo GPU:      %N_GPU_LAYERS% layers
if defined LLM_GPU_ID (
    echo GPU ID:   %LLM_GPU_ID% ^(isolated from Minecraft^)
)
echo Caching:  Enabled ^(--cache-prompt^)
echo ============================================================
echo.

REM Use delayed expansion variable
set "MODEL_ARG=!SELECTED_MODEL!"

REM Set CUDA_VISIBLE_DEVICES to isolate GPU for LLM
if defined LLM_GPU_ID (
    set "CUDA_VISIBLE_DEVICES=%LLM_GPU_ID%"
    echo [INFO] Running LLM on GPU %LLM_GPU_ID%
    echo.
)

llama-server.exe --model "!MODEL_ARG!" --port %PORT% --ctx-size %CONTEXT_SIZE% --n-gpu-layers %N_GPU_LAYERS% --batch-size %BATCH_SIZE% --threads %THREADS% --parallel %PARALLEL% --main-gpu %MAIN_GPU% --host 0.0.0.0 --metrics --cache-prompt

if errorlevel 1 (
    echo.
    echo [ERROR] Server failed to start
    pause
    exit /b 1
)

endlocal
