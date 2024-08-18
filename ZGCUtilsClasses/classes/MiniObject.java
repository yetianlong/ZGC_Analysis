package classes;

public class MiniObject {
    // 1 * 4B
    private static final Integer INIT_SIZE = 1;

    private String name;
    private int[] data;

    // 无参构造函数
    public MiniObject() {
        this.name = "MiniObject";
        this.data = new int[INIT_SIZE];
    }

    // 有参构造函数
    public MiniObject(String name, int[] data) {
        this.name = name;
        this.data = new int[1];
        if (data != null && data.length == 1) {
            this.data[0] = data[0];
        }
    }
}
