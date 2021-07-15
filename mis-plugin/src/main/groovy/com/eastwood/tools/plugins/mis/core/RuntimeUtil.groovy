package com.eastwood.tools.plugins.mis.core

import org.apache.commons.io.IOUtils

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

public final class RuntimeUtil {
    private RuntimeUtil() {

    }

    public static int exec(String[] cmdArray, String[] env, File dir) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(cmdArray, env, dir);
            readStream(process.getInputStream(), process.getErrorStream());
            return process.waitFor()
        } finally {
            if (process != null) {
                process.destroy()
            }
        }
    }

    private static void readStream(InputStream... inputStreams) {
        final ExecutorService executor = Executors.newFixedThreadPool(inputStreams.length)
        inputStreams.each {
            executor.execute(new Runnable() {
                @Override
                void run() {
                    try {
                        BufferedReader br = new BufferedReader(new InputStreamReader(it, "GBK"));
                        String line
                        while ((line = br.readLine()) != null) {
                            println(line)
                        }
                    } finally {
                        IOUtils.close(it)
                    }
                }
            })
        };
        executor.shutdown()
    }
}