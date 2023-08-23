// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io.fs;

import android.system.OsConstants;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.antlr.runtime.RecognitionException;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.writer.io.FileDataStore;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.dex.DexClasses;
import io.github.muntashirakon.AppManager.dex.DexUtils;
import io.github.muntashirakon.AppManager.fm.ContentType2;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.io.ExtendedFile;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class DexFileSystem extends VirtualFileSystem {
    public static final String TYPE = ContentType2.DEX.getMimeType();

    private static class ClassInfo {
        @Nullable
        public final File cachedFile;
        public final boolean physical;
        public final boolean directory;

        private ClassInfo(@Nullable File cachedFile, boolean physical) {
            this(cachedFile, physical, false);
        }

        private ClassInfo(@Nullable File cachedFile, boolean physical, boolean directory) {
            this.cachedFile = cachedFile;
            this.physical = physical;
            this.directory = directory;
        }
    }

    private final LruCache<String, Node<ClassDef>> mCache = new LruCache<>(100);
    @Nullable
    private DexClasses mDexClasses;
    @Nullable
    private Node<ClassDef> mRootNode;

    protected DexFileSystem(@NonNull Path dexPath) {
        super(dexPath);
    }

    public int getApiLevel() {
        // TODO: 26/11/22 Set via MountOptions
        return -1;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @NonNull
    public final DexClasses getDexClasses() {
        checkMounted();
        return Objects.requireNonNull(mDexClasses);
    }

    @Override
    protected Path onMount() throws IOException {
        if (".dex".equals(getFile().getExtension())) {
            try (InputStream is = getFile().openInputStream()) {
                mDexClasses = new DexClasses(is, getApiLevel());
            }
        } else { // APK/Zip file, may need caching
            ExtendedFile file = getFile().getFile();
            if (file != null) {
                mDexClasses = new DexClasses(file, getApiLevel());
            } else {
                File cachedFile = FileCache.getGlobalFileCache().getCachedFile(getFile());
                mDexClasses = new DexClasses(cachedFile, getApiLevel());
            }
        }
        mRootNode = buildTree(Objects.requireNonNull(mDexClasses));
        return Paths.get(this);
    }

    @Override
    protected File onUnmount(@NonNull Map<String, List<Action>> actions) throws IOException {
        File cachedFile = getUpdatedDexFile(actions);
        if (mDexClasses != null) {
            mDexClasses.close();
        }
        mRootNode = null;
        mCache.evictAll();
        return cachedFile;
    }

    @Nullable
    private File getUpdatedDexFile(@NonNull Map<String, List<Action>> actionList) throws IOException {
        if (!Objects.requireNonNull(getOptions()).readWrite || actionList.isEmpty()) {
            return null;
        }
        String extension = getFile().getExtension();
        File file = FileCache.getGlobalFileCache().createCachedFile(extension);
        Map<String, ClassInfo> classInfoMap = new HashMap<>();
        for (String className : Objects.requireNonNull(mDexClasses).getClassNames()) {
            classInfoMap.put(File.separator + className, new ClassInfo(null, true));
        }
        for (String path : actionList.keySet()) {
            // Perform action for each path
            List<Action> actions = actionList.get(path);
            if (actions == null) continue;
            for (Action action : actions) {
                Node<?> targetNode = action.targetNode;
                // Actions are linear
                switch (action.action) {
                    case ACTION_CREATE:
                        // This must be a new file/folder. So, override the existing one.
                        classInfoMap.put(targetNode.getFullPath(), new ClassInfo(null, false, targetNode.isDirectory()));
                        break;
                    case ACTION_DELETE:
                        // Delete the entry
                        classInfoMap.remove(targetNode.getFullPath());
                        break;
                    case ACTION_UPDATE: {
                        // It's a file and it's updated. So, cached file must exist.
                        File cachedFile = Objects.requireNonNull(action.getCachedPath());
                        String targetPath = targetNode.getFullPath();
                        ClassInfo classInfo = classInfoMap.get(targetPath);
                        classInfoMap.put(targetPath, new ClassInfo(cachedFile, classInfo != null && classInfo.physical));
                        break;
                    }
                    case ACTION_MOVE:
                        // File/directory move
                        String sourcePath = Objects.requireNonNull(action.getSourcePath());
                        ClassInfo classInfo = classInfoMap.get(sourcePath);
                        if (classInfo != null) {
                            classInfoMap.put(targetNode.getFullPath(), classInfo);
                        } else {
                            classInfoMap.put(targetNode.getFullPath(), new ClassInfo(null, false, targetNode.isDirectory()));
                        }
                        classInfoMap.remove(sourcePath);
                        break;
                }
            }
        }
        // Build dex
        List<String> paths = new ArrayList<>(classInfoMap.keySet());
        Collections.sort(paths);
        List<ClassDef> classDefList = new ArrayList<>(paths.size());
        for (String path : paths) {
            String className = path.substring(1);
            ClassInfo classInfo = Objects.requireNonNull(classInfoMap.get(path));
            if (classInfo.directory) {
                // Skip all directories
                continue;
            }
            if (classInfo.cachedFile != null) {
                // The class was modified
                try {
                    classDefList.add(DexUtils.toClassDef(classInfo.cachedFile, getApiLevel()));
                } catch (RecognitionException e) {
                    throw new IOException(e);
                }
                continue;
            }
            if (classInfo.physical) {
                try {
                    classDefList.add(mDexClasses.getClassDef(className));
                } catch (ClassNotFoundException e) {
                    throw new IOException(e);
                }
            }
            // Skip other non-physical classes because they were never modified and will fail
        }
        DexUtils.storeDex(classDefList, new FileDataStore(file), getApiLevel());
        return file;
    }

    @Nullable
    @Override
    protected Node<?> getNode(String path) {
        checkMounted();
        Node<ClassDef> targetNode = mCache.get(path);
        if (targetNode == null) {
            if (path.equals(File.separator)) {
                targetNode = mRootNode;
            } else {
                targetNode = Objects.requireNonNull(mRootNode).getLastChild(Paths.sanitize(path, true));
            }
            if (targetNode != null) {
                mCache.put(path, targetNode);
            }
        }
        return targetNode;
    }

    @Override
    protected void invalidate(String path) {
        mCache.remove(path);
    }

    @Override
    public long lastModified(@NonNull Node<?> node) {
        return getFile().lastModified();
    }

    @Override
    public long length(String path) {
        Node<?> targetNode = getNode(path);
        if (targetNode == null) {
            return -1;
        }
        if (targetNode.isDirectory()) {
            return 0L;
        }
        if (!targetNode.isFile() || targetNode.getObject() == null) {
            return -1;
        }
        try {
            return Objects.requireNonNull(mDexClasses)
                    .getClassContents((ClassDef) targetNode.getObject())
                    .getBytes(StandardCharsets.UTF_8)
                    .length;
        } catch (ClassNotFoundException e) {
            return -1;
        }
    }

    @SuppressWarnings({"PointlessBooleanExpression", "ConstantConditions"})
    @Override
    public boolean checkAccess(String path, int access) {
        Node<?> targetNode = getNode(path);
        if (access == OsConstants.F_OK) {
            return targetNode != null;
        }
        boolean canAccess = true;
        if ((access & OsConstants.R_OK) != 0) {
            canAccess &= true;
        }
        if ((access & OsConstants.W_OK) != 0) {
            canAccess &= false;
        }
        if ((access & OsConstants.X_OK) != 0) {
            canAccess &= false;
        }
        return canAccess;
    }

    @NonNull
    @Override
    protected InputStream getInputStream(@NonNull Node<?> node) throws IOException {
        ClassDef classDef = (ClassDef) node.getObject();
        if (classDef == null) {
            throw new FileNotFoundException("Class definition for " + node.getFullPath() + " is not found.");
        }
        try {
            return new ByteArrayInputStream(getDexClasses().getClassContents(classDef).getBytes(StandardCharsets.UTF_8));
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    @NonNull
    private static Node<ClassDef> buildTree(@NonNull DexClasses dexClasses) {
        Node<ClassDef> rootNode = new Node<>(null, File.separator);
        List<String> classNames = dexClasses.getClassNames();
        for (String className : classNames) {
            ClassDef classDef;
            try {
                classDef = dexClasses.getClassDef(className);
            } catch (ClassNotFoundException e) {
                classDef = null;
            }
            buildTree(rootNode, className, Objects.requireNonNull(classDef));
        }
        return rootNode;
    }

    // Build nodes as needed by the entry, entry itself is the last node in the tree if it is not a directory
    private static void buildTree(@NonNull Node<ClassDef> rootNode, @NonNull String className, @NonNull ClassDef classDef) {
        String filename = Paths.sanitize(className, true);
        if (filename == null) {
            return;
        }
        String[] components = filename.split("\\.");
        if (components.length < 1) return;
        Node<ClassDef> lastNode = rootNode;
        for (int i = 0; i < components.length - 1 /* last one will be set manually */; ++i) {
            Node<ClassDef> newNode = lastNode.getChild(components[i]);
            if (newNode == null) {
                // Add children
                newNode = new Node<>(lastNode, components[i]);
                lastNode.addChild(newNode);
            }
            lastNode = newNode;
        }
        lastNode.addChild(new Node<>(lastNode, components[components.length - 1] + ".smali", classDef));
    }
}
