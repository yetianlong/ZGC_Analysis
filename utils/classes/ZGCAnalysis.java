package classes;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;

public class ZGCAnalysis {

    private static final String[] CLASS_NAME = {
            "classes.MiniObject",
            "classes.SmallObject",
            "classes.MediumObject",
            "classes.BigObject"
    };
    private static final Integer ITERATOR_COUNT = 20000;


    public static void main(String[] args) throws Exception {
        List<Object> list = new ArrayList<>();
        // 输出表格
        System.out.println("Memory Usage Information:");
        System.out.println("——————————————————————————————————————————————————————————————————————————————————");
        System.out.printf("| %-12s ", "Epoch Count");
        System.out.printf("| %-12s ", "Init");
        System.out.printf("| %-12s ", "Used");
        System.out.printf("| %-12s ", "Committed");
        System.out.printf("| %-12s ", "Heap Max");
        System.out.println();
        System.out.println("——————————————————————————————————————————————————————————————————————————————————");
        // 模拟内存分配
        int i=0;
        while (i<5) {
            for (int j = 0; j < ITERATOR_COUNT; j++) {
                String className = CLASS_NAME[(int) (Math.random() * CLASS_NAME.length)];
                Class<?> cls = Class.forName(className);
                // 模拟在程序中随机创建的对象，一共四种大小的对象
                Object o = cls.getDeclaredConstructor().newInstance();
                list.add(o);
                // 模拟随机删除对象
                if ((System.currentTimeMillis() & 1) == 0 ) {
                    list.remove(System.currentTimeMillis() % list.size());
                }
            }
            // 简单的休眠，以模拟应用程序的其他工作
            Thread.sleep(10);
            i++;
            printMemory(i);
        }
    }

    public static void printMemory(int i) {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        String[][] table = new String[5][]; // 6行，每行一个迭代的数据

        String[] row = new String[5];
        row[0] = "Epoch " + (i);
        row[1] = formatBytes(memoryMXBean.getHeapMemoryUsage().getInit());
        row[2] = formatBytes(memoryMXBean.getHeapMemoryUsage().getUsed());
        row[3] = formatBytes(memoryMXBean.getHeapMemoryUsage().getCommitted());
        row[4] = (memoryMXBean.getHeapMemoryUsage().getMax() == -1 ? "Unlimited" : formatBytes(memoryMXBean.getHeapMemoryUsage().getMax()));
        table[i-1] = row;

        for (String cell : table[i-1]) {
            System.out.printf("| %-12s ", cell);
        }
        System.out.println("|");

        System.out.println("——————————————————————————————————————————————————————————————————————————————————");
    }


    private static String formatBytes(long bytes) {
        if (bytes == 0) {
            return "0 B";
        } else {
            long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
            long unit = 1024;
            if (absB < unit) {
                return bytes + " B";
            }
            int exp = (int) (Math.log(absB) / Math.log(unit));
            String pre = "KMGTPE".charAt(exp - 1) + "i";
            return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
        }
    }
}

