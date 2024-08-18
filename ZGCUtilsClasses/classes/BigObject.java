package classes;

public class BigObject {
    // 16k * 4B
    private static final Integer INIT_SIZE = 1024 * 16;

    private String name;
    private int[] data;

    // 无参构造函数
    public BigObject() {
        this.name = "utils.BigObject";
        this.data = new int[INIT_SIZE];
    }

    // 有参构造函数
    public BigObject(String name, int[] data) {
        this.name = name;
        this.data = new int[1024 * 1024 * 2];
        if (data != null && data.length == 1024 * 1024 * 2) {
            System.arraycopy(data, 0, this.data, 0, 1024 * 1024 * 2);
        }
    }

    // 省略getter和setter方法...
}
