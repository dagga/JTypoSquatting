#!/bin/bash

# Script to launch both backend and frontend
# Run this from an external terminal (not IntelliJ)

# Get the project root directory (parent of scripts/)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"
cd "$BASE_DIR"

# Database file path
DB_FILE="$BASE_DIR/backend/typosquatting_db.mv.db"
DB_LOCK_FILE="$BASE_DIR/backend/typosquatting_db.lck"

echo "=========================================="
echo "  JTypoSquatting Launcher"
echo "=========================================="
echo ""

# Function to check if database is locked
check_database_lock() {
    if [ -f "$DB_LOCK_FILE" ]; then
        echo "[WARN] Database lock file found: $DB_LOCK_FILE"
        return 0  # locked
    fi
    
    # Check if .mv.db file is being accessed by another process
    if [ -f "$DB_FILE" ]; then
        # Use lsof to check if the database file is open
        if command -v lsof >/dev/null 2>&1; then
            LOCKED_PID=$(lsof -t "$DB_FILE" 2>/dev/null | head -1)
            if [ -n "$LOCKED_PID" ]; then
                echo "[WARN] Database is locked by PID: $LOCKED_PID"
                return 0  # locked
            fi
        fi
    fi
    
    return 1  # not locked
}

# Function to kill process using the database
kill_db_user() {
    local pid=$1
    echo "[INFO] Killing process $pid that is using the database..."
    kill -9 "$pid" 2>/dev/null
    sleep 2
}

# Function to clean database locks
clean_database_locks() {
    echo "[INFO] Cleaning database lock files..."
    rm -f "$DB_LOCK_FILE" 2>/dev/null
    rm -f "$BASE_DIR/backend/*.lock" 2>/dev/null
    rm -f "$BASE_DIR/backend/*.lck" 2>/dev/null
    sleep 1
}

JAVA_CMD=""
for java in /home/nicolas/.jdks/openjdk-24/bin/java /usr/lib/jvm/*/bin/java java; do
    if [ -x "$java" ]; then
        JAVA_CMD=$java
        break
    fi
done

if [ -z "$JAVA_CMD" ]; then
    echo "[ERROR] Java non trouvé!"
    exit 1
fi

echo "[INFO] Using Java: $JAVA_CMD"

XVFB_RUNNING=false

if [ -n "$DISPLAY" ]; then
    if xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
        echo "[OK] Environnement X11 détecté (DISPLAY=$DISPLAY)"
    else
        echo "[INFO] DISPLAY=$DISPLAY (présumé disponible)"
    fi
elif pgrep -f "Xvfb :99" >/dev/null 2>&1; then
    echo "[INFO] Xvfb est déjà lancé sur :99"
    export DISPLAY=:99
    XVFB_RUNNING=true
elif pgrep -f "Xvfb" >/dev/null 2>&1; then
    for disp in 0 1 2 3 4 5; do
        if xdpyinfo -display ":$disp" >/dev/null 2>&1; then
            echo "[INFO] Xvfb détecté sur :$disp"
            export DISPLAY=:$disp
            XVFB_RUNNING=true
            break
        fi
    done
fi

if [ "$XVFB_RUNNING" = false ] && [ -z "$DISPLAY" ]; then
    XVFB_CMD=""
    for cmd in Xvfb xvfb-run; do
        if command -v $cmd >/dev/null 2>&1; then
            XVFB_CMD=$cmd
            break
        fi
    done
    
    if [ -z "$XVFB_CMD" ]; then
        echo "[AVERT] Xvfb n'est pas installé. Continuation sans environnement graphique..."
    else
        echo "[INFO] Lancement de Xvfb sur :99..."
        export DISPLAY=:99
        
        if [ "$XVFB_CMD" = "xvfb-run" ]; then
            nohup xvfb-run -a --server-args="-screen 0 1280x1024x24" sleep infinity > /tmp/xvfb.log 2>&1 &
        else
            nohup Xvfb :99 -screen 0 1280x1024x24 > /tmp/xvfb.log 2>&1 &
        fi
        sleep 2
        
        if xdpyinfo -display :99 >/dev/null 2>&1; then
            echo "[OK] Xvfb lancé sur DISPLAY=:99"
        else
            echo "[AVERT] Impossible de lancer Xvfb. Continuation sans environnement graphique..."
            unset DISPLAY
        fi
    fi
fi

echo ""

# Kill any existing JTypoSquatting processes
echo ""
echo "[INFO] Checking for existing JTypoSquatting processes..."
pkill -9 -f "JTypoSquattingApplication" 2>/dev/null
pkill -9 -f "JTypoFrame" 2>/dev/null
pkill -9 -f "bootRun" 2>/dev/null
pkill -9 -f "JTypoSquatting" 2>/dev/null

# Wait for processes to die
echo "[INFO] Waiting for processes to stop..."
for i in {1..10}; do
    if ! pgrep -f "JTypoSquatting" >/dev/null 2>&1; then
        echo "[OK] All processes stopped."
        break
    fi
    sleep 1
done

# Force kill if still running
if pgrep -f "JTypoSquatting" >/dev/null 2>&1; then
    echo "[WARN] Force killing remaining processes..."
    pkill -9 -f "JTypoSquatting" 2>/dev/null
    sleep 3
fi

# Check and handle database lock
echo ""
echo "[INFO] Checking database lock status..."
if check_database_lock; then
    echo "[WARN] Database appears to be locked!"
    echo "[INFO] Searching for processes holding the database..."
    
    # Find and kill processes using the database
    if command -v lsof >/dev/null 2>&1; then
        DB_PIDS=$(lsof -t "$DB_FILE" 2>/dev/null)
        if [ -n "$DB_PIDS" ]; then
            echo "[WARN] Found processes using database: $DB_PIDS"
            for pid in $DB_PIDS; do
                PROC_NAME=$(ps -p $pid -o comm= 2>/dev/null || echo "unknown")
                echo "[WARN] PID $pid ($PROC_NAME) is using the database"
                kill_db_user $pid
            done
        fi
    fi
    
    # Also kill any Java/Gradle processes that might be using the DB
    echo "[INFO] Checking for Java/Gradle processes..."
    for pid in $(pgrep -f "java.*JTypoSquatting" 2>/dev/null); do
        echo "[WARN] Killing Java process $pid"
        kill_db_user $pid
    done
    for pid in $(pgrep -f "gradle.*bootRun" 2>/dev/null); do
        echo "[WARN] Killing Gradle process $pid"
        kill_db_user $pid
    done
    
    # Clean lock files
    clean_database_locks
    
    # Verify lock is cleared
    sleep 2
    if check_database_lock; then
        echo "[ERROR] Database is still locked after cleanup!"
        echo "[ERROR] Manual intervention required:"
        echo "[ERROR]   1. Close any running instances"
        echo "[ERROR]   2. Remove lock file: rm -f $DB_LOCK_FILE"
        exit 1
    else
        echo "[OK] Database lock cleared!"
    fi
else
    echo "[OK] Database is not locked."
    # Clean any stale lock files anyway
    clean_database_locks
fi

# Wait for port 8080 to be free
echo "Waiting for port 8080 to be free..."
for i in {1..15}; do
    if ! curl -s --connect-timeout 1 http://localhost:8080/ >/dev/null 2>&1; then
        echo "Port 8080 is free!"
        break
    fi
    echo "  Waiting for port 8080... ($i/15)"
    sleep 2
done

# Check if port is still in use
if curl -s --connect-timeout 1 http://localhost:8080/ >/dev/null 2>&1; then
    echo "ERROR: Port 8080 is still in use!"
    echo "Please manually kill the process using port 8080:"
    echo "  pkill -9 -f JTypoSquatting"
    echo "Or use a different port:"
    echo "  SERVER_PORT=8082 ./scripts/run.sh"
    exit 1
fi

# Build fatjar
echo ""
echo "[INFO] Building fatjar..."
./gradlew :frontend:copyFatJar --no-daemon
if [ $? -ne 0 ]; then
    echo "[ERROR] Fatjar build failed!"
    exit 1
fi

# Verify fatjar exists
FATJAR="$BASE_DIR/JTypoSquatting.jar"
if [ ! -f "$FATJAR" ]; then
    echo "[ERROR] Fatjar not found at: $FATJAR"
    exit 1
fi
echo "[OK] Fatjar built successfully: $FATJAR"

# Start backend in background
echo ""
echo "Starting backend on http://localhost:8080..."

BACKEND_CMD="./gradlew :backend:bootRun --no-daemon"
if command -v xvfb-run >/dev/null 2>&1; then
    nohup xvfb-run -a $BACKEND_CMD > /tmp/jtypo-backend.log 2>&1 &
else
    nohup env DISPLAY="$DISPLAY" $BACKEND_CMD > /tmp/jtypo-backend.log 2>&1 &
fi
BACKEND_PID=$!
echo "Backend PID: $BACKEND_PID"

# Wait for backend
echo "Waiting for backend (20s)..."
for i in {1..20}; do
    if curl -s http://localhost:8080/ >/dev/null 2>&1; then
        echo "Backend is ready!"
        break
    fi
    sleep 1
done

# Verify backend is running
if ! curl -s http://localhost:8080/ >/dev/null 2>&1; then
    echo "ERROR: Backend failed to start!"
    echo "Check logs: /tmp/jtypo-backend.log"
    exit 1
fi

# Start frontend using fatjar
echo ""
echo "Starting frontend (DISPLAY=$DISPLAY)..."

echo "Starting frontend from fatjar... (Ctrl+C to stop)"
nohup $JAVA_CMD --add-opens java.desktop/javax.swing=ALL-UNNAMED --add-opens java.desktop/java.awt=ALL-UNNAMED -Djava.awt.headless=false -jar "$FATJAR" > /tmp/jtypo-frontend.log 2>&1 &
FRONTEND_PID=$!
echo "Frontend PID: $FRONTEND_PID"

echo ""
echo "=========================================="
echo "  Applications started!"
echo "=========================================="
echo "Backend:  http://localhost:8080 (PID: $BACKEND_PID)"
echo "Frontend: GUI window (PID: $FRONTEND_PID)"
echo ""
echo "Logs:"
echo "  Backend:  /tmp/jtypo-backend.log"
echo "  Frontend: /tmp/jtypo-frontend.log"
echo ""
echo "To stop:"
echo "  pkill -f JTypoSquatting"
echo "  kill $BACKEND_PID $FRONTEND_PID"
