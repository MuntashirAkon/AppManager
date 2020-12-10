/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.server.common;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.NonNull;

@SuppressWarnings("unused")
public final class Shell {
    private final static String TAG = "Shell";
    private static final String TOKEN = "ZL@LOVE^TYS"; //U+1F430 U+2764 U+1F431

    private final Process proc;
    private final BufferedReader in;
    private final OutputStream out;
    private final LinkedBlockingQueue<Command> commandQueue = new LinkedBlockingQueue<>();
    private final AtomicInteger mNextCmdID = new AtomicInteger(0);
    private volatile boolean close = false;

    private static Shell sShell;
    private static Shell sRootShell;

    @NonNull
    public static Shell getShell(String path) throws IOException {
        if (sShell == null) {
            synchronized (Shell.class) {
                if (sShell == null) {
                    sShell = new Shell("sh", false);
                    sShell.exec("export PATH=" + path + ":$PATH");
                }
            }
        }
        return sShell;
    }

    @NonNull
    public static Shell getRootShell(String path) throws IOException {
        if (sRootShell == null) {
            synchronized (Shell.class) {
                if (sRootShell == null) {
                    sRootShell = new Shell("su", true);
                    sShell.exec("export PATH=" + path + ":$PATH");
                }
            }
        }
        return sRootShell;
    }

    private Shell(String cmd, boolean checkRoot) throws IOException {
        proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        out = proc.getOutputStream();

        if (checkRoot) {
            try {
                RootChecker checker = new RootChecker(proc);
                checker.start();
                checker.join(20000);
                if (checker.exit == -1) {
                    throw new RuntimeException("grant root timeout");
                }
                if (checker.exit != 1) {
                    throw new RuntimeException(checker.errorMsg);
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        Runnable shellRunnable = new Runnable() {
            @Override
            public void run() {
                while (!close) {
                    try {
                        Command command = commandQueue.take();
                        if (command != null && !close) {
                            Shell.this.writeCommand(command);
                            Shell.this.readCommand(command);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (close) {
                    try {
                        Shell.this.destroyShell();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        new Thread(shellRunnable, "shell").start();
    }

    private void writeCommand(@NonNull Command command) throws IOException {
        OutputStream out = this.out;
        command.writeCommand(out);
        String line = "\necho " + TOKEN + " " + command.getID() + " $?\n";
        out.write(line.getBytes());
        out.flush();
    }

    private void readCommand(Command command) throws IOException {
        if (command != null) {
            while (!close) {
                String line = in.readLine();
                if (line == null || close) {
                    break;
                }
                int pos = line.indexOf(TOKEN);
                if (pos > 0) {
                    command.onUpdate(command.getID(), line.substring(0, pos));
                }
                if (pos >= 0) {
                    line = line.substring(pos);
                    String[] fields = line.split(" ");
                    if (fields.length >= 2 && fields[1] != null) {
                        int id = 0;
                        try {
                            id = Integer.parseInt(fields[1]);
                        } catch (NumberFormatException ignored) {
                        }
                        int exitCode = -1;
                        try {
                            exitCode = Integer.parseInt(fields[2]);
                        } catch (NumberFormatException ignored) {
                        }
                        if (id == command.getID()) {
                            command.setExitCode(exitCode);
                            break;
                        }
                    }
                }
                command.onUpdate(command.getID(), line);
            }
        }
    }

    public void destroyShell() throws InterruptedException {
        //proc.waitFor();
        try {
            writeCommand(new Command("exit 33\n") {
                @Override
                public void onUpdate(int id, String message) {

                }

                @Override
                public void onFinished(int id) {

                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!commandQueue.isEmpty()) {
            Command command;
            while ((command = commandQueue.poll()) != null) {
                command.terminate("Unexpected Termination.");
                command = null;
            }
        }

        proc.destroy();
    }

    public boolean isClose() {
        return close;
    }

    public void close() throws IOException {
        this.close = true;
    }

    /**
     * Whether all commands are executed (ie. queue is cleared)
     */
    public boolean allCommandsOver() {
        return null == commandQueue || commandQueue.isEmpty();
    }


    private int generateCommandID() {
        int id = mNextCmdID.getAndIncrement();
        if (id > 0x00FFFFFF) {
            mNextCmdID.set(1);
            id = generateCommandID();
        }
        return id;
    }

    @NonNull
    private Command add(Command command) {
        if (close) {
            throw new IllegalStateException("Unable to add commands to a closed shell.");
        }
        command.setId(generateCommandID());
        commandQueue.offer(command);
        return command;
    }

    @NonNull
    public Result exec(String cmd) throws IOException {
        Result result = new Result();
        FLog.log("Command:  " + cmd);
        final StringBuilder outLine = new StringBuilder();
        try {
            result.statusCode = add(new Command(cmd) {
                @Override
                public void onUpdate(int id, String message) {
                    outLine.append(message).append('\n');
                }

                @Override
                public void onFinished(int id) {
                }
            }).waitForFinish();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (result.statusCode == -1) {
            try {
                outLine.setLength(0);
                result.statusCode = add(new Command(cmd) {
                    @Override
                    public void onUpdate(int id, String message) {
                        outLine.append(message).append('\n');
                    }

                    @Override
                    public void onFinished(int id) {
                    }
                }).waitForFinish();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        result.message = outLine.toString();
        return result;
    }

    public int countCommands() {
        return commandQueue.size();
    }


    abstract class Command {
        private final String[] commands;
        private boolean isFinished;
        private int exitCode;
        private final long timeout;
        private int id;

        public abstract void onUpdate(int id, String message);

        public abstract void onFinished(int id);

        public Command(String... commands) {
            this(1000 * 30, commands);
        }

        public Command(int timeout, String... commands) {
            this.timeout = timeout;
            this.commands = commands;
        }

        void setId(int id) {
            this.id = id;
        }

        public int getID() {
            return id;
        }

        public void setExitCode(int code) {
            synchronized (this) {
                exitCode = code;
                isFinished = true;
                onFinished(id);
                this.notifyAll();
            }
        }

        public boolean isFinished() {
            synchronized (this) {
                return isFinished;
            }
        }

        public void terminate(String reason) {
            try {
                close();
                setExitCode(-1);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public int waitForFinish(long timeout) throws InterruptedException {
            synchronized (this) {
                while (!isFinished) {
                    this.wait(timeout);
                    if (!isFinished) {
                        isFinished = true;
                        terminate("Timeout Exception");
                    }
                }
            }
            return exitCode;
        }

        public int waitForFinish() throws InterruptedException {
            synchronized (this) {
                waitForFinish(timeout);
            }
            return exitCode;
        }

        public String getCommand() {
            if (commands == null || commands.length == 0) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            for (String s : commands) {
                sb.append(s);
                sb.append('\n');
            }
            return sb.toString();
        }

        public void writeCommand(@NonNull OutputStream out) throws IOException {
            out.write(getCommand().getBytes());
            out.flush();
        }

    }

    public static class Result implements Parcelable {
        private String message;
        private int statusCode = -1;

        Result() {}

        protected Result(@NonNull Parcel in) {
            message = in.readString();
            statusCode = in.readInt();
        }

        public static final Creator<Result> CREATOR = new Creator<Result>() {
            @NonNull
            @Override
            public Result createFromParcel(Parcel in) {
                return new Result(in);
            }

            @NonNull
            @Override
            public Result[] newArray(int size) {
                return new Result[size];
            }
        };

        public String getMessage() {
            return message;
        }

        public int getStatusCode() {
            return statusCode;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeString(message);
            dest.writeInt(statusCode);
        }
    }


    private static class RootChecker extends Thread {
        int exit = -1;
        String errorMsg = null;
        Process process;

        private RootChecker(Process process) {
            this.process = process;
        }

        @Override
        public void run() {
            try {
                BufferedReader inputStream = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter outputStream = new BufferedWriter(
                        new OutputStreamWriter(this.process.getOutputStream(), StandardCharsets.UTF_8));

                outputStream.write("echo Started");
                outputStream.newLine();
                outputStream.flush();

                while (true) {
                    String line = inputStream.readLine();
                    if (line == null) {
                        throw new EOFException();
                    }
                    if ("".equals(line)) {
                        continue;
                    }
                    if ("Started".equals(line)) {
                        this.exit = 1;
                        break;
                    }
                    errorMsg = "unkown error occured.";
                }
            } catch (IOException e) {
                exit = -42;
                if (e.getMessage() != null) {
                    errorMsg = e.getMessage();
                } else {
                    errorMsg = "RootAccess denied?.";
                }
            }

        }
    }

}
