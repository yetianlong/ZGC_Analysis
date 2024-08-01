# ZGC特性
## ZGC简介
ZGC（Z Garbage Collector） 是一款性能比 G1 更加优秀的垃圾收集器。ZGC 第一次出现是在  JDK 11 中以实验性的特性引入，这也是 JDK 11 中最大的亮点。在 JDK 15 中 ZGC 不再是实验功能，可以正式投入生产使用了，使用 –XX:+UseZGC 可以启用 ZGC。
ZGC 有几个重要特性：

1. **_低延迟_**：ZGC 的主要目标是最小化 GC 暂停时间。因此，ZGC 使用了基于读屏障（Read Barrier）的堆栈式（Stack-Style）替换算法，以及基于标记颜色（Mark-Color）的压缩算法，从而**避免了传统 GC 中的根扫描和整理等阶段，大幅减少了 GC 暂停时间**。（ps：JDK 16 发布后，GC 暂停时间已经缩小到 1 ms 以内，并且时间复杂度是 o(1)，这也就是说 GC 停顿时间是一个固定值了，并不会受堆内存大小影响。）
2. **_高吞吐_**：虽然 ZGC 的主要目标是低延迟，但它的吞吐性能也很不错。在低延迟的基础上，**ZGC 通过多线程并行处理垃圾回收任务，以及使用更大的堆空间和更高效的内存分配器等技术**，提高了垃圾回收的效率和吞吐量。
3. **_大堆支持_**：ZGC 支持的最大堆内存大小为 16TB，这使得它可以处理非常大的内存数据，例如云计算、大数据等领域的应用。
4. **_透明性_**：ZGC 对应用程序是透明的，应用程序无需进行任何修改，即可使用 ZGC 进行垃圾回收。
5. **_并发性_**：ZGC 是一款并发的垃圾回收器，它可以在运行应用程序的同时，进行垃圾回收操作。这使得 ZGC 可以在多核 CPU 上充分发挥并行处理能力，提高垃圾回收的效率。

---

下图来自Oracle官方发布的ZGCpdf，其中展示了不同GC优化的部分
![QQ_1722516342896.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722516401408-c7b02467-28a6-4f66-b27e-b3a85f596107.png#averageHue=%23e6e4e1&clientId=u13b492d2-b0da-4&from=paste&height=294&id=u7ba5590f&originHeight=367&originWidth=841&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=56566&status=done&style=shadow&taskId=u06b2ab57-40a0-42c3-a658-994d0ba2839&title=&width=672.8)

---

下图展示了ZGC和G1停顿时间的对比，可以看到ZGC在G1的停顿时间数量级下几乎都快看不到了（不是没画，只是太小了看不出来）![QQ_1722516679652.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722516682775-0621786a-f03d-489b-ad44-95fe4e8cb39c.png#averageHue=%232d3135&clientId=u13b492d2-b0da-4&from=paste&height=404&id=u38dbe16c&originHeight=505&originWidth=1185&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=77733&status=done&style=shadow&taskId=ud37b07b6-79b0-43b7-94ca-9f999e6ce82&title=&width=948)
![QQ_1722516702047.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722516705549-8d166b4d-df6e-4d85-b85c-0d0ffb0ad3a7.png#averageHue=%232d3035&clientId=u13b492d2-b0da-4&from=paste&height=410&id=u330c2cc2&originHeight=513&originWidth=1190&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=81397&status=done&style=shadow&taskId=uf660241d-9a4a-4043-8e8e-d5d0b9c8552&title=&width=952)

---

吞吐率以及在保持可接受响应时间的同时能处理多少响应在不同JDK版本的表现如下，其中：

- max-jOPS 关注的是系统在单位时间内能够处理的最大响应量。
- critical-jOPS 关注的是系统在保持特定响应时间标准的情况下，能够处理的最大响应量。

![QQ_1722517069105.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722517076481-00cdfb39-d920-466b-93f5-140e6fd1c101.png#averageHue=%232c3034&clientId=u13b492d2-b0da-4&from=paste&height=389&id=u15e43567&originHeight=486&originWidth=1181&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=75602&status=done&style=shadow&taskId=u48d0209c-19f2-4364-be8c-96f6403ab38&title=&width=944.8)
> **max-jOPS (Throughput score):**
> max-jOPS 是指在SPECjbb基准测试中，Java应用程序能够达到的最大响应操作数（Java Operations Per Second，jOPS）。这个指标衡量的是应用程序在给定硬件和配置下的最大吞吐量，即在单位时间内可以处理的最大响应数量。这个分数通常用于评估系统在高负载下的性能，即在系统处理能力达到顶峰时，每秒能处理多少个响应。
> **critical-jOPS (Latency score):**
> critical-jOPS 是指在SPECjbb基准测试中，Java应用程序在满足特定延迟目标的情况下能够达到的响应操作数。这个指标衡量的是在保证特定响应时间（通常是低延迟）的前提下，系统能够处理的最大响应数量。这个分数用于评估系统在保证低延迟情况下的性能，即在保持每个响应的可接受处理时间的同时，系统能处理多少个响应。


---

停顿时间方面的优化随着版本的迭代而有显著的提高：
![QQ_1722517250832.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722517254521-e6bd416c-a3e6-4a38-8be5-65de4c0140cf.png#averageHue=%232c3034&clientId=u13b492d2-b0da-4&from=paste&height=393&id=uecb878ea&originHeight=491&originWidth=1187&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=75836&status=done&style=shadow&taskId=u0d0653bb-6f5f-470b-8c25-e989116bd1d&title=&width=949.6)
> 1. Average (平均):平均响应时间是指所有响应时间的总和除以响应的总数。它提供了一个关于系统性能的整体概览，但可能掩盖了极端值的影响。
> 2. 95th percentile (95百分位):95百分位响应时间表示在所有响应中，有95%的响应时间小于或等于这个值。换句话说，只有5%的响应时间会比这个值更长。这个指标有助于理解大多数用户的体验。
> 3. 99th percentile (99百分位):99百分位响应时间表示在所有响应中，有99%的响应时间小于或等于这个值。这意味着只有1%的响应时间会比这个值更长。这个指标用于衡量极端情况下的性能，通常对于用户体验非常关键。
> 4. 99.9th percentile (99.9百分位):99.9百分位响应时间表示在所有响应中，有99.9%的响应时间小于或等于这个值。只有0.1%的响应时间会比这个值更长。这个指标用于识别非常罕见的性能问题，通常在高度可用的系统中非常重要。
> 5. Max (最大值):最大响应时间是所有响应时间中的最长值。这个指标提供了最坏情况下的性能信息，但它可能受到异常值的影响，不一定能准确反映系统的整体性能。


---

## ZGC关键技术
### 着色指针
#### 三色标记
我们知道 G1 垃圾收集器使用了三色标记，这里先做一个回顾。下面是一个三色标记过程中的对象引用示例图：
![QQ_1722518076761.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722518079079-11e12b35-cf8c-4888-b506-9cd407957e19.png#averageHue=%23f9f9f9&clientId=u13b492d2-b0da-4&from=paste&height=359&id=u6c1b4790&originHeight=449&originWidth=537&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=58598&status=done&style=shadow&taskId=u73124488-9fa1-4c8f-8410-20365dc8529&title=&width=429.6)

- 白色：本对象还没有被标记线程访问过。
- 灰色：本对象已经被访问过，但是本对象引用的其他对象还没有被全部访问。
- 黑色：本对象已经被访问过，并且本对象引用的其他对象也都被访问过了。

标记过程如下：

1. 初始阶段，所有对象都是白色。
2. 将 GC Roots 直接引用的对象标记为灰色。
3. 处理灰色对象，把当前灰色对象引用的所有对象都变成灰色，之后将当前灰色对象变成黑色。
4. 重复步骤 3，直到不存在灰色对象为止。
5. 三色标记结束后，白色对象就是没有被引用的对象（比如上图中的 H  和 G），可以被回收了。

---

#### 着色指针
ZGC 出现之前， GC 信息保存在对象头的 Mark Word 中。前 62位保存了 GC 信息，最后两位保存了锁标志。
ZGC 的一大创举是将 GC 信息保存在了染色指针上。染色指针是一种将少量信息直接存储在指针上的技术。在 64 位 JVM  中，对象指针是 64 位，如下图：
![QQ_1722518266207.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722518269348-cda3731b-571e-4d6e-9027-20650590ab5a.png#averageHue=%23f4f1f0&clientId=u13b492d2-b0da-4&from=paste&height=155&id=u19480c51&originHeight=194&originWidth=969&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=29396&status=done&style=shadow&taskId=ue1cfdffd-cf2c-4fd7-b938-29501801ea0&title=&width=775.2)
其中，[0~4TB) 对应Java堆，[4TB ~ 8TB) 称为M0地址空间，[8TB ~ 12TB) 称为M1地址空间，[12TB ~ 16TB) 预留未使用，[16TB ~ 20TB) 称为Remapped空间。
当应用程序创建对象时，首先在堆空间申请一个虚拟地址，但该虚拟地址并不会映射到真正的物理地址。ZGC同时会为该对象在M0、M1和Remapped地址空间分别申请一个虚拟地址，且这三个虚拟地址对应同一个物理地址，但这三个空间在同一时间有且只有一个空间有效。ZGC之所以设置三个虚拟地址空间，是因为它使用“空间换时间”思想，去降低GC停顿时间。“空间换时间”中的空间是虚拟空间，而不是真正的物理空间。
与上述地址空间划分相对应，ZGC实际仅使用64位地址空间的第0~41位，而第42~45位存储元数据，第47~63位固定为0。
![QQ_1722518314229.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722518317170-4b1ad44b-2c7a-4591-aebd-9ce416b0a248.png#averageHue=%23f4f3f3&clientId=u13b492d2-b0da-4&from=paste&height=204&id=u461ad94d&originHeight=255&originWidth=871&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=70416&status=done&style=shadow&taskId=u1cf233e3-28da-4bdf-924b-ff475b1f3af&title=&width=696.8)
ZGC将对象存活信息存储在42~45位中，这与传统的垃圾回收并将对象存活信息放在对象头中完全不同。

### 读屏障
读屏障类似于 Spring AOP 的前置增强，是 JVM 向应用代码中插入一小段代码，当应用线程从堆中读取对象的引用时，会先执行这段代码。注意：**只有从堆内存中读取对象的引用时，才会执行这个代码**。下面代码只有第一行需要加入读屏障。
```java
Object o = obj.FieldA
Object p = o //不是从堆中读取引用
o.dosomething() //不是从堆中读取引用
int i =  obj.FieldB //不是引用类型
```
读屏障在解释执行时通过 load 相关的字节码指令加载数据。作用是在对象标记和转移过程中，判断对象的引用地址是否满足条件，并作出相应动作。如下图：
![QQ_1722518469013.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722518495393-63f3a7df-29bf-4534-8755-32a5aeee15a2.png#averageHue=%23fdfdfd&clientId=u13b492d2-b0da-4&from=paste&height=624&id=u54b445b3&originHeight=780&originWidth=954&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=178862&status=done&style=shadow&taskId=u96a73550-18ec-4fe9-860a-037f693e2e4&title=&width=763.2)

### Concurrent
和 CMS、G1等垃圾回收器一样，ZGC也采用了标记-复制算法，不过，ZGC对标记-复制算法做了很大的改进，ZGC垃圾回收周期和视图切换可以抽象成下图：
![QQ_1722517631948.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722517634421-a64b8154-a92c-4d2f-a3dd-7f42c74b51d5.png#averageHue=%23acc697&clientId=u13b492d2-b0da-4&from=paste&height=291&id=upzva&originHeight=364&originWidth=410&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=38683&status=done&style=shadow&taskId=u90098153-b4c4-48bb-be53-3e0cf400cb7&title=&width=328)

1. **初始化**：ZGC初始化之后，整个内存空间的地址视图被设置为Remapped。程序正常运行，在内存中分配对象，满足一定条件后垃圾回收启动，此时进入标记阶段。
2. **并发标记阶段**：第一次进入标记阶段时视图为M0，如果对象被GC标记线程或者应用线程访问过，那么就将对象的地址视图从Remapped调整为M0。所以，在标记阶段结束之后，对象的地址要么是M0视图，要么是Remapped。如果对象的地址是M0视图，那么说明对象是活跃的；如果对象的地址是Remapped视图，说明对象是不活跃的。
3. **并发转移阶段**：标记结束后就进入转移阶段，此时地址视图再次被设置为Remapped。如果对象被GC转移线程或者应用线程访问过，那么就将对象的地址视图从M0调整为Remapped。

### Region-based
ZGC 和 G1等垃圾回收器一样，也会将堆划分成很多的小分区，整个堆内存分区如下图：
![QQ_1722519003070.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722519005972-f7870c40-de28-41ad-b6f0-40c80a7a32c5.png#averageHue=%238ea8a7&clientId=u13b492d2-b0da-4&from=paste&height=423&id=MtA0H&originHeight=529&originWidth=886&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=46192&status=done&style=shadow&taskId=ue65e5d10-62e0-429d-bb59-ba814d9109f&title=&width=708.8)
ZGC的 Region 有小、中、大三种类型: - Small Region（小型 Region）：容量固定为 2M， 存放小于 256K的对象； - Medium Region（中型 Region）：容量固定为 32M，放置大于等于 256K，并小于 4M的对象； - Large Region（大型 Region）: 容量不固定，可以动态变化，但必须为 2M的整数倍，用于放置大于等于 4MB的大对象；

## ZGC特性
![QQ_1722517471642.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722517474197-ce98ba51-bf02-41c8-b7e9-dcc803a6d24f.png#averageHue=%232c2f33&clientId=u13b492d2-b0da-4&from=paste&height=562&id=u0965a056&originHeight=702&originWidth=1249&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=104102&status=done&style=shadow&taskId=uc2638623-25b2-4645-9aa5-9f4026e5a6d&title=&width=999.2)
官方给出的PDF中大致描述了八个特性：

1. **Concurrent:** 并发指的是垃圾回收器可以在应用程序线程运行的同时执行其部分或全部工作。垃圾回收活动不会完全暂停应用程序的执行，从而减少停顿时间。
2. **Tracing:** 追踪是垃圾回收器用来确定哪些对象是活动（仍在使用中）的，哪些对象是垃圾（不再被引用）的过程。追踪垃圾回收器会从根集合（如局部变量和静态字段）开始，遍历对象图来标记活动对象。
3. **Compacting:** 压缩是指垃圾回收器在回收垃圾后，将所有活动对象移动到内存的一端，从而消除内存碎片的过程。这有助于提高内存分配的效率。
4. **Single generation:** 单代垃圾回收器只管理一个内存区域，不像分代垃圾回收器那样将对象分为不同的代（如新生代和老年代）。
5. **Region-based:** 基于区域的垃圾回收器将堆划分为多个固定大小的区域。每个区域可以是活动的或空闲的，并且垃圾回收通常在这些区域上进行，有助于简化内存管理并减少碎片。
6. **NUMA-aware:** NUMA（非一致性内存访问）意识意味着垃圾回收器已经优化，可以有效地在具有非一致性内存访问特性的多处理器系统中运行。在这样的系统中，每个处理器都有自己的本地内存，访问远程内存比访问本地内存要慢。
7. **Load barriers:** 加载屏障是垃圾回收器用来确保在并发标记期间正确处理对象引用的一种机制。当应用程序线程访问一个对象引用时，加载屏障会执行必要的操作，如更新引用或检查对象的状态。
8. **Colored pointers:** 着色指针是一种用于并发垃圾回收的技术，其中对象的指针被赋予额外的信息位（颜色），以指示对象的状态（如是否被标记、是否可移动等）。这些额外的位允许垃圾回收器在不需要暂停应用程序线程的情况下执行某些操作。
## ZGC的GC全过程
ZGC垃圾回收全过程包含：初始标记、并发标记、再标记、并发转移准备、初始转移、并发转移 6个阶段，如下图：
![QQ_1722519176694.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722519180465-b73b7db9-1d63-446e-b997-c51b1b7ae56a.png#averageHue=%23f6f1f1&clientId=u13b492d2-b0da-4&from=paste&height=565&id=u3c839b59&originHeight=706&originWidth=1348&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=179122&status=done&style=shadow&taskId=u9e4957d9-8dec-4dd3-8004-c66f3a59d77&title=&width=1078.4)
**ZGC只有三个STW阶段：初始标记，再标记，初始转移。**其中，初始标记和初始转移分别都只需要扫描所有GC Roots，其处理时间和GC Roots的数量成正比，一般情况耗时非常短；再标记阶段STW时间很短，最多1ms，超过1ms则再次进入并发标记阶段。即，ZGC几乎所有暂停都只依赖于GC Roots集合大小，停顿时间不会随着堆的大小或者活跃对象的大小而增加。与ZGC对比，G1的转移阶段完全STW的，且停顿时间随存活对象的大小增加而增加。
### 初始标记
从 GC Roots 出发，找出 GC Roots 直接引用的对象，放入活跃对象集合，这个过程需要 STW，**不过 STW 的时间跟 GC Roots 数量成正比，耗时比较短**。
### 并发标记
并发标记过程中，GC 线程和 Java 应用线程会并行运行。这个过程需要注意下面几点：

- GC 标记线程访问对象时，如果对象地址视图是 Remapped，就把对象地址视图切换到 Marked0，如果对象地址视图已经是 Marked0，说明已经被其他标记线程访问过了，跳过不处理。
- 标记过程中Java 应用线程新创建的对象会直接进入 Marked0 视图。
- 标记过程中Java 应用线程访问对象时，如果对象的地址视图是 Remapped，就把对象地址视图切换到 Marked0，可以参考前面讲的读屏障。
- 标记结束后，如果对象地址视图是 Marked0，那就是活跃的，如果对象地址视图是 Remapped，那就是不活跃的。

**标记阶段的活跃视图也可能是 Marked1，为什么会采用两个视图呢？**
这里采用两个视图是为了区分前一次标记和这一次标记。如果这次标记的视图是 Marked0，那下一次并发标记就会把视图切换到 Marked1。这样做可以配合 ZGC 按照页回收垃圾的做法。如下图：
![QQ_1722519913253.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722519917829-5c49094f-52b5-419d-af1d-91b394d0b54b.png#averageHue=%23fdfdfc&clientId=u13b492d2-b0da-4&from=paste&height=516&id=u1dd43a72&originHeight=645&originWidth=740&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=312256&status=done&style=shadow&taskId=ubb45dbef-fd0d-4283-b99b-dc3af431849&title=&width=592)
第二次标记的时候，如果还是切换到 Marked0，那么 2 这个对象区分不出是活跃的还是上次标记过的。如果第二次标记切换到 Marked1，就可以区分出了。
这时 Marked0 这个视图的对象就是上次标记过程被标记过活跃，转移的时候没有被转移，但这次标记没有被标记为活跃的对象。Marked1 视图的对象是这次标记被标记为活跃的对象。Remapped 视图的对象是上次垃圾回收发生转移或者是被 Java 应用线程访问过，本次垃圾回收中被标记为不活跃的对象。
### 再标记
并发标记阶段 GC 线程和 Java 应用线程并发执行，标记过程中可能会有引用关系发生变化而导致的漏标记问题。**再标记阶段重新标记并发标记阶段发生变化的对象**，还会对非强引用（软应用，虚引用等）进行并行标记。
这个阶段需要 STW，但是需要标记的对象少，耗时很短。
### 初始转移
**转移就是把活跃对象复制到新的内存，之前的内存空间可以被回收。**
初始转移需要扫描 GC Roots 直接引用的对象并进行转移，这个过程需要 STW，STW 时间跟 GC Roots 成正比。
### 并发转移
并发转移过程 GC 线程和 Java 线程是并发进行的。上面已经讲过，转移过程中对象视图会被切回 Remapped 。转移过程需要注意以下几点：

- 如果 GC 线程访问对象的视图是 Marked0，则转移对象，并把对象视图设置成 Remapped。
- 如果 GC 线程访问对象的视图是 Remapped，说明被其他 GC 线程处理过，跳过不再处理。
- 并发转移过程中 Java 应用线程创建的新对象地址视图是 Remapped。
- 如果 Java 应用线程访问的对象被标记为活跃并且对象视图是 Marked0，则转移对象，并把对象视图设置成 Remapped。
### 重定位
转移过程对象的地址发生了变化，在这个阶段，把所有指向对象旧地址的指针调整到对象的新地址上。
# ZGC 日志分析
# ZGC 特性探究
## 调优参数
下表整理了JVM基本参数以及ZGC调优参数

|  | **参数** | **描述** | **用法示例** | **详细解释** |
| --- | --- | --- | --- | --- |
| **General GC Options** | -XX:MinHeapSize, -Xms | 设置JVM启动时的初始堆大小 | -Xms512m | 用于指定JVM在启动时分配给堆内存的初始大小。如果不指定，JVM会根据默认值或当前系统的可用内存自动选择一个大小。这个参数有助于避免JVM在运行时频繁调整堆大小，从而提高性能。 |
|  | -XX:InitialHeapSize, -Xms | 设置JVM启动时的初始堆大小 | -Xms512m | 用于指定JVM在启动时分配给堆内存的初始大小。如果不指定，JVM将根据默认值或当前系统的可用内存自动选择一个大小。这个参数有助于减少JVM在运行时动态调整堆大小的次数，从而提高性能。与`-XX:MaxHeapSize`结合使用，可以有效地控制堆内存的使用。 |
|  | -XX:MaxHeapSize, -Xmx | 设置JVM启动时的最大堆大小 | -Xmx1024m | 用于指定JVM堆内存可以扩展到的最大大小。一旦堆内存达到这个限制，JVM将不会继续扩展堆，而是触发垃圾回收以释放空间。这个参数对于控制JVM的内存占用非常重要。 |
|  | -XX:SoftMaxHeapSize | 设置JVM堆的最大软限制大小 | -XX:SoftMaxHeapSize=800m | 这个参数设置了堆内存的一个软限制，当堆内存使用量接近这个限制时，JVM会尝试减少堆的使用量，但不会强制执行。这有助于在不触发Full GC的情况下控制内存使用。 |
|  | -XX:ConcGCThreads | 设置并发垃圾收集器使用的线程数 | -XX:ConcGCThreads=4 | 这个参数指定了并发垃圾收集器在执行垃圾回收时使用的线程数。增加线程数可以提高垃圾回收的效率，但也可能增加对应用程序线程的干扰。 |
|  | -XX:ParallelGCThreads | 设置并行垃圾收集器使用的线程数 | -XX:ParallelGCThreads=8 | 这个参数用于指定并行垃圾收集器在执行垃圾回收时使用的线程数。并行垃圾收集器在执行时会暂停应用程序线程，因此这个参数对于垃圾回收的性能有直接影响。 |
|  | -XX:UseLargePages | 启用大页面内存使用 | -XX:UseLargePages | 这个参数用于启用大页面内存，这可以减少页面置换的开销，提高内存访问效率。大页面内存通常用于需要大量内存的应用程序。 |
|  | -XX:UseTransparentHugePages | 启用透明大页面内存使用 | -XX:UseTransparentHugePages | 这个参数启用了透明大页面，它允许JVM自动使用大页面内存，而不需要手动配置。这有助于提高内存访问速度，尤其是在处理大量内存时。 |
|  | -XX:UseNUMA | 启用NUMA (非一致性内存访问) 感知内存分配 | -XX:UseNUMA | 这个参数用于启用NUMA感知的内存分配策略，它允许JVM根据NUMA架构来分配内存，从而优化跨多个处理器和内存模块的内存访问速度。 |
|  | -XX:SoftRefLRUPolicyMSPerMB | 设置软引用LRU策略的毫秒数每兆字节 | -XX:SoftRefLRUPolicyMSPerMB=1000 | 这个参数设置了软引用在LRU（最近最少使用）策略下被垃圾回收器清除之前可以存活的时间，单位是毫秒每兆字节。 |
|  | -XX:AllocateHeapAt | 指定堆内存分配的起始地址 | -XX:AllocateHeapAt=0x10000000 | 这个参数用于指定堆内存分配的起始地址，通常用于特殊场景，如性能测试或系统级编程。 |
| **ZGC Options** | -XX:ZAllocationSpikeTolerance | 设置ZGC对内存分配尖峰的容忍度 | -XX:ZAllocationSpikeTolerance=2.0 | 用于设置ZGC对内存分配尖峰的容忍度。它是一个浮点数，表示在垃圾回收周期内允许的最大内存分配量与平均分配量的比率。如果实际分配量超过这个比率，ZGC可能会提前触发垃圾回收以避免内存溢出。较高的值可以减少垃圾回收的频率，但可能会增加内存溢出的风险。 |
|  | -XX:ZCollectionInterval | 设置ZGC垃圾收集的间隔时间（秒） | -XX:ZCollectionInterval=120 | 这个参数用于设置ZGC垃圾收集器执行垃圾回收的时间间隔，以秒为单位。设置较短的间隔可以更频繁地进行垃圾回收，但可能会增加CPU的使用率。 |
|  | -XX:ZFragmentationLimit | 设置ZGC的最大堆碎片限制（百分比） | -XX:ZFragmentationLimit=10 | 这个参数设置了ZGC在执行垃圾回收时允许的最大堆碎片百分比。如果碎片超过这个限制，ZGC会尝试进行压缩以减少碎片。 |
|  | -XX:ZMarkStackSpaceLimit | 设置ZGC标记阶段栈空间的最大大小（MB） | -XX:ZMarkStackSpaceLimit=64 | 这个参数限制了ZGC在标记阶段使用的栈空间的最大大小。如果标记工作负载很大，可能需要增加这个值。 |
|  | -XX:ZProactive | 启用ZGC的主动回收策略 | -XX:ZProactive | 这个参数启用ZGC的主动回收策略，允许ZGC在堆内存使用量未达到阈值时也触发垃圾回收，以减少长时间垃圾回收的可能性。 |
|  | -XX:ZUncommit | 启用ZGC的堆内存取消提交功能 | -XX:ZUncommit | 这个参数允许ZGC在垃圾回收后取消提交不再需要的堆内存，从而减少JVM的内存占用。 |
|  | -XX:ZUncommitDelay | 设置ZGC取消提交堆内存的延迟时间（秒） | -XX:ZUncommitDelay=300 | 这个参数指定了ZGC在垃圾回收后延迟取消提交堆内存的时间。延迟取消提交可以避免频繁的内存提交和取消操作，从而减少系统开销。 |
| **ZGC Diagnostic Options (-XX:+UnlockDiagnosticVMOptions)** | -XX:ZStatisticsInterval | 设置ZGC统计信息打印的间隔时间（秒） | -XX:ZStatisticsInterval=60 | 这个参数用于设置ZGC打印统计信息的频率。统计信息可以帮助监控和调试垃圾回收器的行为。 |
|  | -XX:ZVerifyForwarding | 启用ZGC转发指针的验证 | -XX:ZVerifyForwarding | 这个参数用于启用ZGC在垃圾回收过程中对转发指针的验证。这有助于检测和调试内存管理中的问题，但可能会略微降低性能。 |
|  | -XX:ZVerifyMarking | 启用ZGC标记阶段的验证 | -XX:ZVerifyMarking | 这个参数启用ZGC在标记阶段对标记过程的验证。通过验证，可以确保标记过程的正确性，但可能会增加额外的性能开销。 |
|  | -XX:ZVerifyObjects | 启用ZGC对象引用的验证 | -XX:ZVerifyObjects | 这个参数用于启用ZGC对对象引用的验证，确保对象引用的正确性。这对于调试内存泄漏或错误非常有用，但会降低性能。 |
|  | -XX:ZVerifyRoots | 启用ZGC根引用的验证 | -XX:ZVerifyRoots | 这个参数启用ZGC对根引用的验证，确保所有根引用都被正确处理。这有助于发现和修复垃圾回收过程中的问题。 |
|  | -XX:ZVerifyViews | 启用ZGC视图映射的验证 | -XX:ZVerifyViews | 这个参数用于启用ZGC对内存视图映射的验证。ZGC使用多重映射技术，验证视图映射可以确保映射的正确性，但会影响性能。 |

## ZGC 基本特性
### 暂停时间短
### 高吞吐
## JVM 通用参数调优
## ZGC 参数调优































