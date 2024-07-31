package classes;

public class SmallObject {
    // 4k * 4B
    private static final Integer INIT_SIZE = 1024 * 4;

    private String name;
    private int[] data;

    // 无参构造函数
    public SmallObject() {
        this.name = "SmallObject";
        this.data = new int[INIT_SIZE];
    }

    // 有参构造函数
    public SmallObject(String name, int[] data) {
        this.name = name;
        this.data = new int[1024];
        if (data != null && data.length == 1024) {
            System.arraycopy(data, 0, this.data, 0, 1024);
        }
    }
}