// SPDX-License-Identifier: GPL-3.0-or-later

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <fcntl.h>
#include <errno.h>

#define SERVER_NAME "am_local_server"
#define JAR_MAIN_CLASS "io.github.muntashirakon.AppManager.server.ServerRunner"
#define TMP_PATH "/data/local/tmp"

int is_safe_string(const char *str) {
    if (!str || str[0] == '\0') {
        return 0;
    }

    // Prevent exactly "." or ".."
    if (strcmp(str, ".") == 0 || strcmp(str, "..") == 0) {
        return 0;
    }

    // Only allow alphanumeric, dot, underscore, and dash
    for (int i = 0; str[i] != '\0'; i++) {
        char c = str[i];
        if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
              (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-')) {
            // Invalid character found
            return 0;
        }
    }
    // All characters are valid
    return 1;
}

int copy_file(const char *src, const char *dst) {
    int fd_src = open(src, O_RDONLY | O_CLOEXEC);
    if (fd_src < 0) {
        return -1;
    }

    // Remove any existing file at destination to prevent symlink following
    unlink(dst);

    int fd_dst = open(dst, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC, 0755);
    if (fd_dst < 0) {
        close(fd_src);
        return -1;
    }

    char buf[BUFSIZ];
    ssize_t n;
    while ((n = read(fd_src, buf, sizeof(buf))) > 0) {
        if (write(fd_dst, buf, n) != n) {
            // Write failed
            close(fd_src);
            close(fd_dst);
            unlink(dst);
            return -1;
        }
    }

    close(fd_src);
    close(fd_dst);

    return (n < 0) ? -1 : 0;
}

int main(int argc, char *argv[]) {
    if (argc < 8) {
        fprintf(stderr,
                "USAGE: %s <port> <token> <am_jar_name> <main_jar_name> <app_id> <user_id> <debug(1|0)> [extra_args...]\n",
                argv[0]);
        return 1;
    }

    const char *port = argv[1];
    const char *token = argv[2];
    const char *am_jar_name = argv[3];
    const char *main_jar_name = argv[4];
    const char *app_id = argv[5];
    const char *user_id = argv[6];
    const char *debug = argv[7];
    const char *bgrun = "1";

    // Validate Paths
    if (!is_safe_string(am_jar_name) || !is_safe_string(main_jar_name) || !is_safe_string(app_id) ||
        !is_safe_string(user_id)) {
        fprintf(stderr, "Error! Invalid characters in arguments.\n");
        return 1;
    }

    // Validate debug and bgrun
    if ((strcmp(debug, "0") != 0 && strcmp(debug, "1") != 0)) {
        fprintf(stderr, "Error! debug must be either 0 or 1.\n");
        return 1;
    }

    // /data/local/tmp/am.jar
    char exec_jar_path[512];
    if (snprintf(exec_jar_path, sizeof(exec_jar_path), "%s/%s", TMP_PATH, am_jar_name) >=
        sizeof(exec_jar_path)) {
        fprintf(stderr, "Error! Buffer overflow on exec_jar_path.\n");
        return 1;
    }

    // /data/local/tmp/main.jar
    char main_jar_path[512];
    if (snprintf(main_jar_path, sizeof(main_jar_path), "%s/%s", TMP_PATH, main_jar_name) >=
        sizeof(main_jar_path)) {
        fprintf(stderr, "Error! Buffer overflow on main_jar_path.\n");
        return 1;
    }

    // Prioritized list of fallback source paths
    char am_jar_fallbacks[4][512];
    snprintf(am_jar_fallbacks[0], sizeof(am_jar_fallbacks[0]), "/sdcard/Android/data/%s/cache/%s",
             app_id,
             am_jar_name);
    snprintf(am_jar_fallbacks[1], sizeof(am_jar_fallbacks[1]),
             "/storage/emulated/%s/Android/data/%s/cache/%s",
             user_id, app_id, am_jar_name);
    snprintf(am_jar_fallbacks[2], sizeof(am_jar_fallbacks[2]), "/sdcard/AppManager/%s",
             am_jar_name);
    snprintf(am_jar_fallbacks[3], sizeof(am_jar_fallbacks[3]), "/storage/emulated/%s/AppManager/%s",
             user_id,
             am_jar_name);

    char main_jar_fallbacks[4][512];
    snprintf(main_jar_fallbacks[0], sizeof(main_jar_fallbacks[0]), "/sdcard/Android/data/%s/cache/%s",
             app_id,
             main_jar_name);
    snprintf(main_jar_fallbacks[1], sizeof(main_jar_fallbacks[1]),
             "/storage/emulated/%s/Android/data/%s/cache/%s",
             user_id, app_id, main_jar_name);
    snprintf(main_jar_fallbacks[2], sizeof(main_jar_fallbacks[2]), "/sdcard/AppManager/%s",
             main_jar_name);
    snprintf(main_jar_fallbacks[3], sizeof(main_jar_fallbacks[3]), "/storage/emulated/%s/AppManager/%s",
             user_id,
             main_jar_name);

    uid_t uid = getuid();
    gid_t gid = getgid();
    printf("Starting %s as %d:%d...\n", SERVER_NAME, uid, gid);

    // Copy am.jar as /data/local/tmp/am.jar
    const char *resolved_am_jar_path = NULL;
    for (int i = 0; i < 4; i++) {
        if (copy_file(am_jar_fallbacks[i], exec_jar_path) == 0) {
            resolved_am_jar_path = am_jar_fallbacks[i];
            break;
        }
    }
    if (resolved_am_jar_path == NULL) {
        fprintf(stderr, "Error! %s could not be found or copied.\n", am_jar_name);
        return 1;
    }
    // Fix ownership
    if (chown(exec_jar_path, uid, gid) != 0) {
        fprintf(stderr, "Warning: chown failed: %s\n", strerror(errno));
        // Although it failed, still proceed
    }

    // Copy main.jar as /data/local/tmp/main.jar
    const char *resolved_main_jar_path = NULL;
    for (int i = 0; i < 4; i++) {
        if (copy_file(main_jar_fallbacks[i], main_jar_path) == 0) {
            resolved_main_jar_path = main_jar_fallbacks[i];
            break;
        }
    }
    if (resolved_main_jar_path == NULL) {
        fprintf(stderr, "Error! %s could not be found or copied.\n", main_jar_name);
        return 1;
    }
    // Fix ownership
    if (chown(main_jar_path, uid, gid) != 0) {
        fprintf(stderr, "Warning: chown failed: %s\n", strerror(errno));
        // Although it failed, still proceed
    }

    // Build argument for am.jar
    char args_buf[2048];
    if (snprintf(args_buf, sizeof(args_buf), "path:%s,token:%s,app:%s,bgrun:%s,debug:%s",
                 port, token, app_id, bgrun, debug) >= sizeof(args_buf)) {
        fprintf(stderr, "Error! Buffer overflow on args_buf.\n");
        unlink(exec_jar_path);
        return 1;
    }

    printf("Resolved Jar path: %s\n", resolved_am_jar_path);
    printf("Args: %s\n", args_buf);

    // Execute app_process
    if (setenv("CLASSPATH", exec_jar_path, 1) != 0) {
        fprintf(stderr, "Error setting CLASSPATH\n");
        unlink(exec_jar_path);
        return 1;
    }

    int extra_args_count = argc - 8;
    int exec_argc = 6 + extra_args_count;
    char **exec_argv = malloc(sizeof(char *) * exec_argc);
    if (!exec_argv) {
        fprintf(stderr, "Out of memory\n");
        unlink(exec_jar_path);
        return 1;
    }

    exec_argv[0] = "app_process";
    exec_argv[1] = "/system/bin";

    char nice_name_buf[128];
    snprintf(nice_name_buf, sizeof(nice_name_buf), "--nice-name=%s", SERVER_NAME);
    exec_argv[2] = nice_name_buf;

    exec_argv[3] = JAR_MAIN_CLASS;
    exec_argv[4] = args_buf;

    for (int i = 0; i < extra_args_count; i++) {
        exec_argv[5 + i] = argv[8 + i];
    }
    exec_argv[exec_argc - 1] = NULL;

    printf("Local server has started.\n");
    fflush(stdout);

    // Start in the background
    pid_t pid = fork();

    if (pid < 0) {
        fprintf(stderr, "Error! Fork failed: %s\n", strerror(errno));
        unlink(exec_jar_path);
        free(exec_argv);
        return 1;
    }

    if (pid > 0) {
        // Parent process exits successfully
        printf("Local server started successfully (PID: %d).\n", pid);
        free(exec_argv);
        return 0;
    }

    // Detach the background process
    if (setsid() < 0) {
        unlink(exec_jar_path);
        exit(1);
    }

    // Execute local server
    execv("/system/bin/app_process", exec_argv);

    // If execv returns, an error occurred
    fprintf(stderr, "Error! Could not start local server: %s\n", strerror(errno));

    // Cleanup
    unlink(exec_jar_path);
    unlink(main_jar_path);
    free(exec_argv);
    exit(1);
}