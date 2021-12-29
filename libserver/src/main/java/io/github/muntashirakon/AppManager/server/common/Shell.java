// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

// Copyright 2017 Zheng Li
public final class Shell {
    private static final String TOKEN = UUID.randomUUID().toString();

    private static Shell sShell;

    @NonNull
    public static Shell getShell(String path) throws IOException {
        if (sShell == null) {
            synchronized (Shell.class) {
                if (sShell == null) {
                    sShell = new Shell("sh");
                    sShell.exec("export PATH=" + path + ":$PATH");
                }
            }
        }
        return sShell;
    }

    private final Process mProcess;
    private final BufferedReader mIn;
    private final OutputStream mOut;
    private final LinkedBlockingQueue<Command> mCommandQueue = new LinkedBlockingQueue<>();
    private final AtomicInteger mNextCmdID = new AtomicInteger(0);

    private volatile boolean mClosed = false;

    private Shell(String cmd) throws IOException {
        mProcess = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        mIn = new BufferedReader(new InputStreamReader(mProcess.getInputStream()));
        mOut = mProcess.getOutputStream();

        Runnable shellRunnable = () -> {
            while (!mClosed) {
                try {
                    Command command = mCommandQueue.take();
                    if (command != null && !mClosed) {
                        Shell.this.writeCommand(command);
                        Shell.this.readCommand(command);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (mClosed) {
                Shell.this.destroyShell();
            }
        };

        new Thread(shellRunnable, "shell").start();
    }

    private void writeCommand(@NonNull Command command) throws IOException {
        OutputStream out = this.mOut;
        command.writeCommand(out);
        String line = "\necho " + TOKEN + " " + command.getID() + " $?\n";
        out.write(line.getBytes());
        out.flush();
    }

    private void readCommand(Command command) throws IOException {
        if (command != null) {
            while (!mClosed) {
                String line = mIn.readLine();
                if (line == null || mClosed) {
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

    public void destroyShell() {
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
            if (mIn != null) {
                mIn.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            if (mOut != null) {
                mOut.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!mCommandQueue.isEmpty()) {
            Command command;
            while ((command = mCommandQueue.poll()) != null) {
                command.terminate("Unexpected Termination.");
                command = null;
            }
        }

        mProcess.destroy();
    }

    public boolean isClosed() {
        return mClosed;
    }

    public void close() {
        this.mClosed = true;
    }

    /**
     * Whether all commands are executed (ie. queue is cleared)
     */
    public boolean allCommandsOver() {
        return mCommandQueue.isEmpty();
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
        if (mClosed) {
            throw new IllegalStateException("Unable to add commands to a closed shell.");
        }
        command.setId(generateCommandID());
        mCommandQueue.offer(command);
        return command;
    }

    @NonNull
    public Result exec(String cmd) {
        Result result = new Result();
        FLog.log("Command:  " + cmd);
        final StringBuilder outLine = new StringBuilder();
        try {
            result.mStatusCode = add(new Command(cmd) {
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
        if (result.mStatusCode == -1) {
            try {
                outLine.setLength(0);
                result.mStatusCode = add(new Command(cmd) {
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
        result.mMessage = outLine.toString();
        return result;
    }

    public int countCommands() {
        return mCommandQueue.size();
    }


    abstract class Command {
        private final String[] mCommands;
        private final long mTimeout;

        private boolean mIsFinished;
        private int mExitCode;
        private int mId;

        public abstract void onUpdate(int id, String message);

        public abstract void onFinished(int id);

        public Command(String... commands) {
            this(1000 * 30, commands);
        }

        public Command(int timeout, String... commands) {
            mTimeout = timeout;
            mCommands = commands;
        }

        void setId(int id) {
            mId = id;
        }

        public int getID() {
            return mId;
        }

        public void setExitCode(int code) {
            synchronized (this) {
                mExitCode = code;
                mIsFinished = true;
                onFinished(mId);
                this.notifyAll();
            }
        }

        public boolean isFinished() {
            synchronized (this) {
                return mIsFinished;
            }
        }

        public void terminate(String reason) {
            close();
            setExitCode(-1);
        }

        public int waitForFinish(long timeout) throws InterruptedException {
            synchronized (this) {
                while (!mIsFinished) {
                    this.wait(timeout);
                    if (!mIsFinished) {
                        mIsFinished = true;
                        terminate("Timeout Exception");
                    }
                }
            }
            return mExitCode;
        }

        public int waitForFinish() throws InterruptedException {
            synchronized (this) {
                waitForFinish(mTimeout);
            }
            return mExitCode;
        }

        public String getCommand() {
            if (mCommands == null || mCommands.length == 0) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            for (String s : mCommands) {
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
        private String mMessage;
        private int mStatusCode = -1;

        Result() {
        }

        protected Result(@NonNull Parcel in) {
            mMessage = in.readString();
            mStatusCode = in.readInt();
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
            return mMessage;
        }

        public int getStatusCode() {
            return mStatusCode;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeString(mMessage);
            dest.writeInt(mStatusCode);
        }
    }
}
