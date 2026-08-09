package com.winlator.cmod.perf;

import android.os.Process;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure Java thread-priority boosting for maximum CPU & GPU performance.
 *
 * Targets guest CPU worker threads (Box64/Wine/Proot) and app renderer/audio/X11
 * threads to ensure high CPU frequency allocation from Android SoC governors
 * (especially Mali / Dimensity / Exynos / Tensor devices).
 */
public class PerfPriority {
    private static final String TAG = "PerfPriority";

    private static final int[] BOOST_LADDER = new int[]{
        Process.THREAD_PRIORITY_URGENT_DISPLAY, // -8
        Process.THREAD_PRIORITY_DISPLAY,        // -4
        Process.THREAD_PRIORITY_FOREGROUND     // -2
    };

    private static final String[] APP_THREAD_TOKENS = new String[]{
        "audio", "worker", "render", "present", "vk", "gl", "xserver", "epoll"
    };

    private static final ConcurrentHashMap<Integer, Integer> originalNice = new ConcurrentHashMap<>();

    public static int boost(int guestRootPid) {
        int count = 0;
        for (int tid : collectAppThreadTids()) {
            if (boostTid(tid)) count++;
        }
        if (guestRootPid > 0) {
            for (int tid : collectGuestTids(guestRootPid)) {
                if (boostTid(tid)) count++;
            }
        }
        Log.d(TAG, "PerfPriority: boosted " + count + " thread(s) (guestRootPid=" + guestRootPid + ")");
        return count;
    }

    public static int restore() {
        int count = 0;
        for (Map.Entry<Integer, Integer> entry : originalNice.entrySet()) {
            try {
                Process.setThreadPriority(entry.getKey(), entry.getValue());
                count++;
            } catch (Throwable ignored) {}
        }
        Log.d(TAG, "PerfPriority: restored " + count + " thread(s)");
        originalNice.clear();
        return count;
    }

    private static boolean boostTid(int tid) {
        int cur;
        try {
            cur = Process.getThreadPriority(tid);
        } catch (Throwable e) {
            return false;
        }

        for (int target : BOOST_LADDER) {
            if (target >= cur) continue;
            try {
                if (!originalNice.containsKey(tid)) {
                    originalNice.put(tid, cur);
                }
                Process.setThreadPriority(tid, target);
                int after;
                try {
                    after = Process.getThreadPriority(tid);
                } catch (Throwable e) {
                    after = target;
                }
                if (after < cur) {
                    Log.d(TAG, "tid=" + tid + " nice " + cur + " -> " + after);
                    return true;
                }
                if (after == cur) {
                    originalNice.remove(tid);
                }
            } catch (Throwable ignored) {
                originalNice.remove(tid);
            }
        }
        return false;
    }

    private static List<Integer> collectAppThreadTids() {
        File taskDir = new File("/proc/self/task");
        File[] tasks = taskDir.listFiles();
        List<Integer> out = new ArrayList<>();
        if (tasks == null) return out;

        for (File task : tasks) {
            try {
                int tid = Integer.parseInt(task.getName());
                File commFile = new File(task, "comm");
                if (commFile.exists()) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(commFile))) {
                        String comm = reader.readLine();
                        if (comm != null) {
                            comm = comm.trim().toLowerCase();
                            for (String token : APP_THREAD_TOKENS) {
                                if (comm.contains(token)) {
                                    out.add(tid);
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        return out;
    }

    private static List<Integer> collectGuestTids(int rootPid) {
        File procDir = new File("/proc");
        File[] procDirs = procDir.listFiles(f -> f.isDirectory() && isInteger(f.getName()));
        List<Integer> tids = new ArrayList<>();
        if (procDirs == null) return tids;

        Map<Integer, Integer> ppidOf = new HashMap<>();
        List<Integer> pids = new ArrayList<>();

        for (File d : procDirs) {
            try {
                int pid = Integer.parseInt(d.getName());
                File statFile = new File(d, "stat");
                if (!statFile.exists()) continue;
                try (BufferedReader reader = new BufferedReader(new FileReader(statFile))) {
                    String stat = reader.readLine();
                    if (stat == null) continue;
                    int rp = stat.lastIndexOf(')');
                    if (rp < 0 || rp + 2 >= stat.length()) continue;
                    String[] after = stat.substring(rp + 2).trim().split("\\s+");
                    if (after.length >= 2) {
                        int ppid = Integer.parseInt(after[1]);
                        ppidOf.put(pid, ppid);
                        pids.add(pid);
                    }
                }
            } catch (Throwable ignored) {}
        }

        Set<Integer> subtree = new HashSet<>();
        subtree.add(rootPid);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int pid : pids) {
                if (subtree.contains(pid)) continue;
                Integer pp = ppidOf.get(pid);
                if (pp != null && subtree.contains(pp)) {
                    subtree.add(pid);
                    changed = true;
                }
            }
        }

        for (int pid : subtree) {
            File taskDir = new File("/proc/" + pid + "/task");
            File[] tasks = taskDir.listFiles();
            if (tasks != null) {
                for (File t : tasks) {
                    try {
                        tids.add(Integer.parseInt(t.getName()));
                    } catch (Throwable ignored) {}
                }
            }
        }
        return tids;
    }

    private static boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
