package classes;

public class MediumObject {
    // 8k * 4B
    private static final Integer INIT_SIZE = 1024 * 8;

    private String name;
    private int[] data;

    // 无参构造函数
    public MediumObject() {
        this.name = "MediumObject";
        this.data = new int[INIT_SIZE];
    }

    // 有参构造函数
    public MediumObject(String name, int[] data) {
        this.name = name;
        this.data = new int[1024 * 1024];
        if (data != null && data.length == 1024 * 1024) {
            System.arraycopy(data, 0, this.data, 0, 1024 * 1024);
        }
    }

    // 省略getter和setter方法...
}
