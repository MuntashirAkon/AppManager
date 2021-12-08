#!/system/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later

if [[ $# -lt 2 ]]; then
    echo "USAGE: ./run_server.sh <path|port> <token>"
    exit 1
fi

SERVER_NAME=
JAR_NAME=
JAR_PATH=
%ENV_VARS%
PORT="path:$1"
TOKEN=",token:$2"
ARGS="${PORT}${ARGS}${TOKEN}"
JAR_PACKAGE_NAME="io.github.muntashirakon.AppManager"
JAR_MAIN_CLASS="${JAR_PACKAGE_NAME}.server.ServerRunner"
TMP_PATH="/data/local/tmp"
EXEC_JAR_PATH=${TMP_PATH}/${JAR_NAME}

echo "Starting $SERVER_NAME..."
# Copy am.jar to executable directory
cp -f ${JAR_PATH} ${EXEC_JAR_PATH}
if [[ $? -ne 0 ]]; then
    # Copy failed
    echo "Error! Could not copy jar file to the executable directory."
    exit 1
fi
# Fix permission
chmod 755 ${EXEC_JAR_PATH}
chown shell:shell ${EXEC_JAR_PATH}
# Debug log
echo "Jar path: $JAR_PATH"
echo "Args: $ARGS"
# Save jar path to environment variable
export CLASSPATH=${EXEC_JAR_PATH}
# Execute local server
exec app_process /system/bin --nice-name=${SERVER_NAME} ${JAR_MAIN_CLASS} "$ARGS" $@  &
if [[ $? -ne 0 ]]; then
    # Start failed
    echo "Error! Could not start local server."
    exit 1
else
    # Start success
    echo "Local server has started."
    echo "Use Ctrl+C to exit."
fi
# Print pid
ps | grep ${SERVER_NAME}
exit 0