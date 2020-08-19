#!/system/bin/sh

SERVER_NAME="am_local_server"
PACKAGE_NAME="io.github.muntashirakon.AppManager"
JAR_NAME="am.jar"
JAR_MAIN_CLASS="${PACKAGE_NAME}.server.ServerRunner"
TMP_PATH="/data/local/tmp"
EXT_DATA_PATH="/sdcard/Android/data/${PACKAGE_NAME}/files"
EXEC_JAR_PATH=${TMP_PATH}/${JAR_NAME}

jar_path=%s
args=%s

echo "Script started."
id
ls -l ${EXT_DATA_PATH}
# Copy am.jar to executable directory
cp -f ${jar_path} ${EXEC_JAR_PATH}
if [[ $? -ne 0 ]]; then
    # Copy failed, try default path
    jar_path=${EXT_DATA_PATH}/${JAR_NAME}
    cp -f ${jar_path} ${EXEC_JAR_PATH}
fi
if [[ $? -ne 0 ]]; then
    # Copy failed
    echo "Error! Copying jar file to the executable directory failed!"
    exit 1
fi
# Fix permission
chmod 755 ${EXEC_JAR_PATH}
chown shell:shell ${EXEC_JAR_PATH}
# Debug log
echo "Jar path: $jar_path"
echo "Args: $args"
# Save jar path to environment variable
export CLASSPATH=${EXEC_JAR_PATH}
# Execute local server
exec app_process /system/bin --nice-name=${SERVER_NAME} ${JAR_MAIN_CLASS} "$args" $@  &
if [[ $? -ne 0 ]]; then
    # Start failed
    echo "Error! Failed to start local server."
else
    # Start success
    echo "Success! Local server has started."
    echo "\n\nUse Ctrl+C to exit.\n"
fi
# Print pid
ps | grep ${SERVER_NAME}