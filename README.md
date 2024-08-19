* [ZGC特性](#zgc特性)
  * [ZGC简介](#zgc简介)
  * [ZGC关键技术](#zgc关键技术)
    * [着色指针](#着色指针)
      * [三色标记](#三色标记)
      * [着色指针](#着色指针-1)
    * [读屏障](#读屏障)
    * [Concurrent](#concurrent)
    * [Region\-based](#region-based)
  * [ZGC特性](#zgc特性-1)
  * [ZGC的GC全过程](#zgc的gc全过程)
    * [初始标记](#初始标记)
    * [并发标记](#并发标记)
    * [再标记](#再标记)
    * [初始转移](#初始转移)
    * [并发转移](#并发转移)
    * [重定位](#重定位)
* [ZGC 日志分析](#zgc-日志分析)
  * [程序介绍](#程序介绍)
    * [模拟分析程序](#模拟分析程序)
    * [工具类](#工具类)
    * [JVM参数设置](#jvm参数设置)
  * [初始化日志分析](#初始化日志分析)
    * [日志详细解释](#日志详细解释)
  * [收集过程日志分析](#收集过程日志分析)
    * [日志详细解释](#日志详细解释-1)
    * [总结](#总结)
  * [结束阶段日志分析](#结束阶段日志分析)
    * [日志详细解释](#日志详细解释-2)
* [ZGC 特性探究](#zgc-特性探究)
  * [调优参数大全](#调优参数大全)
  * [ZGC 基本特性](#zgc-基本特性)
    * [暂停时间短](#暂停时间短)
    * [高吞吐](#高吞吐)
    * [其他特性](#其他特性)
      * [内存分配](#内存分配)
      * [GC 阶段](#gc-阶段)
      * [暂停时间和并发时间](#暂停时间和并发时间)
  * [JVM 通用参数调优](#jvm-通用参数调优)
    * [<strong>\-Xms \-Xmx</strong>](#-xms--xmx)
    * [<strong>\-XX:ConcGCThreads</strong>](#-xxconcgcthreads)
    * [<strong>\-XX:ParallelGCThreads</strong>](#-xxparallelgcthreads)
  * [ZGC 参数调优](#zgc-参数调优)
    * [\-XX:ZAllocationSpikeTolerance](#-xxzallocationspiketolerance)
    * [<strong>\-XX:ZCollectionInterval</strong>](#-xxzcollectioninterval)
    * [<strong>\-XX:\+UnlockDiagnosticVMOptions \-XX:\-ZProactive</strong>](#-xxunlockdiagnosticvmoptions--xx-zproactive)
    * [\-XX:ZFragmentationLimit](#-xxzfragmentationlimit)
* [参考](#参考)

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

其中，[0\~4TB) 对应Java堆，[4TB \~ 8TB) 称为M0地址空间，[8TB \~ 12TB) 称为M1地址空间，[12TB \~ 16TB) 预留未使用，[16TB \~ 20TB) 称为Remapped空间。
当应用程序创建对象时，首先在堆空间申请一个虚拟地址，但该虚拟地址并不会映射到真正的物理地址。ZGC同时会为该对象在M0、M1和Remapped地址空间分别申请一个虚拟地址，且这三个虚拟地址对应同一个物理地址，但这三个空间在同一时间有且只有一个空间有效。ZGC之所以设置三个虚拟地址空间，是因为它使用“空间换时间”思想，去降低GC停顿时间。“空间换时间”中的空间是虚拟空间，而不是真正的物理空间。
与上述地址空间划分相对应，ZGC实际仅使用64位地址空间的第0\~41位，而第42\~45位存储元数据，第47\~63位固定为0。
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
**ZGC只有三个STW阶段：初始标记，再标记，初始转移**。其中，初始标记和初始转移分别都只需要扫描所有GC Roots，其处理时间和GC Roots的数量成正比，一般情况耗时非常短；再标记阶段STW时间很短，最多1ms，超过1ms则再次进入并发标记阶段。即，ZGC几乎所有暂停都只依赖于GC Roots集合大小，停顿时间不会随着堆的大小或者活跃对象的大小而增加。与ZGC对比，G1的转移阶段完全STW的，且停顿时间随存活对象的大小增加而增加。

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

完整日志信息参考：BasicCharacteristics/logs/PauseTime/zgc_4g_0.2.log
完整地址：[ZGC 4G](https://github.com/yetianlong/ZGC_Analysis/blob/master/BasicCharacteristics/logs/PauseTime/zgc_4g_0.2.log)

## 程序介绍

### 模拟分析程序

模拟分析程序如下：

```java
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
```

程序中包含了：

- **内存监控**： 程序通过MemoryMXBean获取堆内存的使用情况，包括初始容量、已使用容量、已提交容量和最大容量。
- **对象创建和销毁**： 程序模拟了对象的随机创建和销毁，触发JVM的垃圾回收。
  - 创建ITERATOR_COUNT个对象，对象类型随机选择自CLASS_NAME数组。
  - 对象创建后添加到列表中。
  - 机删除列表中的对象，模拟对象销毁。
  - 单休眠10毫秒，模拟应用程序的其他工作。
  - 调用printMemory方法输出当前迭代的内存使用情况。
- **格式化输出**： 程序通过formatBytes方法将内存使用量格式化为易读的字符串，便于观察。
- **循环迭代**： 程序执行5次迭代，每次迭代创建和销毁一定数量的对象，并打印内存使用情况。

### 工具类

设计了四个工具类模拟程序中创建不同大小的对象，其中包含了：

- MiniObject：模拟超小对象，大小为 1 * 4B
- SmallObject：模拟小对象，大小为 4k * 4B
- MediumObject：模拟中对象，大小为 8k * 4B
- BigObject：模拟大对象，大小为 16k * 4B

同时在程序中通过反射创建各个对象，并且采用随机数来随机生成对象。
超小对象：

```java
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
```

小对象：

```java
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
```

中对象：

```java
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
```

大对象：

```java
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
```

### JVM参数设置

JVM参数设置如下，其中包括ZGC的参数，以及对比实验所使用的G1 GC以及Parallel GC

```
# Z GC 4g
-Xms4g
-Xmx4g
-XX:+UseZGC
-XX:SoftMaxHeapSize=4g
-XX:ZCollectionInterval=0.2
-XX:+PrintGCDetails
-Xloggc:D:\PROJECT\Tencent\ZGC-Analysis\ZGC_Analysis\BasicCharacteristics\PauseTime\logs\zgc_4g_0.2.log

# G1 GC 4g
-Xms4g
-Xmx4g
-XX:+UseG1GC
-XX:SoftMaxHeapSize=4g
-XX:MaxGCPauseMillis=200
-XX:+PrintGCDetails
-Xloggc:D:\PROJECT\Tencent\ZGC-Analysis\ZGC_Analysis\BasicCharacteristics\PauseTime\logs\g1_4g_0.2.log


# Parallel GC 4g
-Xms4g
-Xmx4g
-XX:+UseParallelGC
-XX:SoftMaxHeapSize=4g
-XX:MaxGCPauseMillis=200
-XX:+PrintGCDetails
-Xloggc:D:\PROJECT\Tencent\ZGC-Analysis\ZGC_Analysis\BasicCharacteristics\PauseTime\logs\parallelgc_4g_0.2.log
```

**ZGC参数解释如下：**

- -Xms4g：设置JVM启动时的初始堆内存大小为4GB。
- -Xmx4g：设置JVM运行期间的最大堆内存大小为4GB。
- -XX:+UseZGC：启用ZGC（Z Garbage Collector）垃圾回收器。
- -XX:SoftMaxHeapSize=4g：设置堆内存的软最大值限制为4GB。
- -XX:ZCollectionInterval=0.2：设置ZGC的垃圾回收周期为0.2秒。
- -XX:+PrintGCDetails：启用详细垃圾回收日志的打印。

**G1 参数解释如下：**

- -Xms4g：设置JVM启动时的初始堆内存大小为4GB。
- -Xmx4g：设置JVM运行期间的最大堆内存大小为4GB。
- -XX:+UseZGC：启用G1（Garbage-First Garbage Collector）垃圾回收器。
- -XX:SoftMaxHeapSize=4g：设置堆内存的软最大值限制为4GB。
- -XX:MaxGCPauseMillis=200：设置G1垃圾回收器尝试达到的最大暂停时间为200毫秒。
- -XX:+PrintGCDetails：启用详细垃圾回收日志的打印。

**Parallel GC参数解释如下：**

- -Xms4g：设置JVM启动时的初始堆内存大小为4GB。
- -Xmx4g：设置JVM运行期间的最大堆内存大小为4GB。
- -XX:+UseZGC：启用Parallel垃圾回收器。
- -XX:SoftMaxHeapSize=4g：设置堆内存的软最大值限制为4GB。
- -XX:MaxGCPauseMillis=200：设置垃圾回收暂停的最大目标时间为200毫秒。
- -XX:+PrintGCDetails：启用详细垃圾回收日志的打印。

## 初始化日志分析

### 日志详细解释

| **日志行数** | **日志内容**                                                 | **解释内容**                                                 |
| ------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 1            | 0.022s][info][gc,init] Initializing The Z Garbage Collector  | 初始化Z垃圾回收器                                            |
| 2            | [0.022s][info][gc,init] Version: 17.0.11+1-LTS (release)     | ZGC版本：17.0.11+1-LTS（发布版）                             |
| 3            | [0.022s][info][gc,init] NUMA Support: Disabled               | NUMA支持：禁用                                               |
| 4            | [0.022s][info][gc,init] CPUs: 12 total, 12 available         | CPU总数：12，可用CPU：12                                     |
| 5            | [0.022s][info][gc,init] Memory: 24496M                       | 内存：24496MB                                                |
| 6            | [0.022s][info][gc,init] Large Page Support: Disabled         | 大页面支持：禁用                                             |
| 7            | [0.022s][info][gc,init] GC Workers: 3 (dynamic)              | GC工作线程：3（动态）                                        |
| 8            | [0.023s][info][gc,init] Address Space Type: Contiguous/Unrestricted/Complete | 地址空间类型：连续/无限制/完整                               |
| 9            | [0.023s][info][gc,init] Address Space Size: 65536M x 3 = 196608M | 地址空间大小：65536MB x 3 = 196608MB                         |
| 10           | [0.023s][info][gc,init] Min Capacity: 4096M                  | 最小容量：4096MB                                             |
| 11           | [0.023s][info][gc,init] Initial Capacity: 4096M              | 初始容量：4096MB                                             |
| 12           | [0.024s][info][gc,init] Max Capacity: 4096M                  | 最大容量：4096MB                                             |
| 13           | [0.024s][info][gc,init] Medium Page Size: 32M                | 中等页面大小：32MB                                           |
| 14           | [0.024s][info][gc,init] Pre-touch: Disabled                  | 预触摸：禁用                                                 |
| 15           | [0.024s][info][gc,init] Uncommit: Implicitly Disabled (-Xms equals -Xmx) | 取消提交：隐式禁用（-Xms等于-Xmx）                           |
| 16           | [0.239s][info][gc,init] Runtime Workers: 8                   | 运行时工作线程：8                                            |
| 17           | [0.240s][info][gc     ] Using The Z Garbage Collector        | 使用Z垃圾回收器                                              |
| 18           | [0.250s][info][gc,metaspace] CDS archive(s) mapped at: [0x000002540f000000-0x000002540fbc0000-0x000002540fbc0000), size 12320768, SharedBaseAddress: 0x000002540f000000, ArchiveRelocationMode: 1. | CDS存档映射在：[0x000002540f000000-0x000002540fbc0000-0x000002540fbc0000)，大小12320768，共享基址：0x000002540f000000，存档重定位模式：1 |
| 19           | [0.250s][info][gc,metaspace] Compressed class space mapped at: 0x0000025410000000-0x0000025450000000, reserved size: 1073741824 | 压缩类空间映射在：0x0000025410000000-0x0000025450000000，保留大小：1073741824 |
| 20           | [0.250s][info][gc,metaspace] Narrow klass base: 0x000002540f000000, Narrow klass shift: 0, Narrow klass range: 0x100000000 | 窄类基础：0x000002540f000000，窄类移位：0，窄类范围：0x100000000 |

## 收集过程日志分析

### 日志详细解释

| **日志行数 (+390)** | **日志内容**                                                 | **解释内容**                                                 | **阶段描述**        | **分析**                                                     |
| ----------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------- | ------------------------------------------------------------ |
| 1                       | [3.173s][info][gc,start    ] GC(10) Garbage Collection (Timer) | 垃圾收集器开始第10次垃圾收集                                 | 启动阶段            | 每次GC收集的开始标志                                         |
| 2                       | [3.173s][info][gc,task     ] GC(10) Using 3 workers          | 在第10次垃圾收集期间，使用了3个工作线程来并行执行垃圾收集任务。 | 执行阶段            | 垃圾收集器启动了3个工作线程来并行执行垃圾收集任务。垃圾收集器通常会将工作负载分配给多个工作线程，以提高垃圾收集的效率和吞吐量。 |
| 3                       | [3.173s][info][gc,phases   ] GC(10) Pause Mark Start 0.006ms | 标记阶段开始，暂停了0.006毫秒。                              | 垃圾收集阶段        | 暂停时间越短，对应用程序性能的影响就越小。                   |
| 4                       | [3.195s][info][gc,phases   ] GC(10) Concurrent Mark 21.603ms | 并发标记阶段耗时21.603毫秒。                                 | 垃圾收集阶段        | 并发标记允许垃圾收集器在应用程序运行时标记对象，而不是暂停应用程序的执行。 |
| 5                       | [3.195s][info][gc,phases   ] GC(10) Pause Mark End 0.009ms   | 标记阶段结束，暂停了0.009毫秒。                              | 垃圾收集阶段        | 标记阶段结束，暂停时间越短，对应用程序性能的影响就越小。     |
| 6                       | [3.195s][info][gc,phases   ] GC(10) Concurrent Mark Free 0.001ms | 并发标记空闲阶段耗时0.001毫秒。                              | 垃圾收集阶段        | 并发标记阶段的空闲时间非常短，说明垃圾收集器在并发标记阶段几乎没有等待时间 |
| 7                       | [3.196s][info][gc,phases   ] GC(10) Concurrent Process Non-Strong References 0.952ms | 并发处理非强引用阶段耗时0.952毫秒。                          | 垃圾收集阶段        | 垃圾收集器在并发处理非强引用阶段的所有操作时间               |
| 8                       | [3.196s][info][gc,phases   ] GC(10) Concurrent Reset Relocation Set 0.001ms | 并发重置迁移集阶段耗时0.001毫秒。                            | 垃圾收集阶段        | 并发重置迁移集阶段耗时0.001毫秒，可见该阶段执行很快          |
| 9                       | [3.199s][info][gc,phases   ] GC(10) Concurrent Select Relocation Set 2.154ms | 并发选择迁移集阶段耗时2.154毫秒。                            | 垃圾收集阶段        | 并发选择迁移集阶段的所有操作时间，迁移集是指在重定位阶段需要移动的对象集合。这个阶段发生在并发标记阶段之后，垃圾收集器会根据标记的结果选择需要移动的对象集合，以便在重定位阶段可以高效地移动对象。 |
| 10                      | [3.199s][info][gc,phases   ] GC(10) Pause Relocate Start 0.005ms | 重定位阶段开始，暂停了0.005毫秒。                            | 垃圾收集阶段        | 重定位阶段是用来移动那些被标记为存活的对象，以避免在后续的垃圾收集操作中产生内存碎片。 |
| 11                      | [3.200s][info][gc,phases   ] GC(10) Concurrent Relocate 1.372ms | 并发重定位阶段耗时1.372毫秒。                                | 垃圾收集阶段        | 1.372ms相对较长，垃圾收集器在这个阶段进行了较多的操作        |
| 12                      | [3.200s][info][gc,load     ] GC(10) Load: 0.00/0.00/0.00     | 垃圾收集期间的系统负载信息，三个数值都为0.00，表示系统负载很低。 | 系统负载信息        | 在垃圾收集期间，系统的负载越低，说明垃圾收集对系统性能的影响越小。 |
| 13                      | [3.200s][info][gc,mmu      ] GC(10) MMU: 2ms/99.4%, 5ms/99.7%, 10ms/99.8%, 20ms/99.9%, 50ms/100.0%, 100ms/100.0% | 最大内存利用率（MMU）信息，显示了不同时间窗口内的内存利用率百分比。 | 最大内存 利用率信息 | 在过去的2毫秒、5毫秒、10毫秒、20毫秒、50毫秒和100毫秒内，内存利用率分别达到了99.4%、99.7%、99.8%、99.9%、100.0%和100.0% MMU信息用于描述垃圾收集器在不同时间窗口内达到的最大内存利用率。 |
| 14                      | [3.200s][info][gc,marking  ] GC(10) Mark: 2 stripe(s), 2 proactive flush(es), 1 terminate flush(es), 0 completion(s), 0 continuation(s) | 标记阶段信息，包括使用的stripes数、主动刷新次数等。          | 标记阶段            | 使用了2个stripes来执行标记。stripe是一种并行处理技术，它允许垃圾收集器同时处理堆内存的不同部分。 执行了2次主动刷新。主动刷新是指垃圾收集器在标记过程中主动清理一些不再使用的对象，以减少后续阶段的处理量。 执行了1次终止刷新。终止刷新是在标记阶段结束时执行的，用于清理标记阶段中未处理完的对象。 没有执行任何完成操作。完成操作是在标记阶段完成后执行的，用于清理标记阶段中的某些资源。 没有执行任何延续操作。延续操作是在标记阶段完成后执行的，用于继续处理标记阶段中未完成的工作。 |
| 15                      | [3.200s][info][gc,marking  ] GC(10) Mark Stack Usage: 32M    | 标记阶段使用的标记栈大小为32M。                              | 标记阶段            | 标记栈是垃圾收集器在执行标记操作时使用的内存空间，用于存储标记过程中的中间结果和数据结构。 |
| 16                      | [3.200s][info][gc,nmethod  ] GC(10) NMethods: 469 registered, 0 unregistered | 垃圾收集期间，有469个方法被注册，没有方法被注销。            | 方法信息            | “NMethods”指的是Java虚拟机中与类相关的部分，包括类定义、方法字节码等。 |
| 17                      | [3.200s][info][gc,metaspace] GC(10) Metaspace: 1M used, 1M committed, 1088M reserved | 元空间使用了1M，提交了1M，保留了1088M。                      | 元空间信息          | 元空间是Java虚拟机中用于存储类和静态变量信息的区域，与传统的永久代（PermGen）或老年代（Old Gen）不同，它通常使用堆外内存，并且由不同的垃圾收集器管理。 |
| 18                      | [3.200s][info][gc,ref      ] GC(10) Soft: 181 encountered, 0 discovered, 0 enqueued | 软引用统计信息：遇到了181个，没有发现或入队的软引用。        | 引用信息            | 软引用是指那些不会阻止对象被垃圾收集，但一旦内存不足，垃圾收集器会尝试回收这些引用的对象。 |
| 19                      | [3.200s][info][gc,ref      ] GC(10) Weak: 263 encountered, 142 discovered, 0 enqueued | 弱引用统计信息：遇到了263个，发现了142个，没有入队的弱引用。 | 引用信息            | 弱引用是指那些不会阻止对象被垃圾收集，一旦没有强引用指向该对象，垃圾收集器就会回收该对象。 |
| 20                      | [3.200s][info][gc,ref      ] GC(10) Final: 0 encountered, 0 discovered, 0 enqueued | 终结引用统计信息：没有遇到、发现或入队的终结引用。           | 引用信息            | 终结引用是指那些不会阻止对象被垃圾收集，一旦对象被终结（finalize），垃圾收集器就会回收该对象。 |
| 21                      | [3.200s][info][gc,ref      ] GC(10) Phantom: 7 encountered, 3 discovered, 0 enqueued | 幻象引用统计信息：遇到了7个，发现了3个，没有入队的幻象引用。 | 引用信息            | 幻象引用是指那些不会阻止对象被垃圾收集，并且不会被垃圾收集器用于回收对象。 |
| 22                      | [3.200s][info][gc,reloc    ] GC(10) Small Pages: 991 / 1982M, Empty: 0M, Relocated: 1M, In-Place: 0 | 小页面的使用情况：991页/1982M，没有空页，重定位了1M，没有原地更新的内存。 | 重定位信息          | 原地更新是指对象在原内存位置上进行更新，而不是移动到另一个位置。 |
| 23                      | [3.200s][info][gc,reloc    ] GC(10) Medium Pages: 1 / 32M, Empty: 0M, Relocated: 0M, In-Place: 0 | 页面的使用情况：1页/32M，没有空页，没有重定位或原地更新的内存。 | 重定位信息          | 垃圾收集器处理的中页面（Medium Pages）的情况                 |
| 24                      | [3.201s][info][gc,reloc    ] GC(10) Large Pages: 0 / 0M, Empty: 0M, Relocated: 0M, In-Place: 0 | 大页面的使用情况：没有使用大页面，没有空页，没有重定位或原地更新的内存。 | 重定位信息          | 垃圾收集器处理的大页面（Large Pages）的情况                  |
| 25                      | [3.201s][info][gc,reloc    ] GC(10) Forwarding Usage: 0M     | 前向引用的使用情况：没有使用前向引用内存。                   | 重定位信息          | 前向引用允许垃圾收集器在重定位阶段将对象从一个内存位置移动到另一个位置时，同时保持对旧位置的引用，直到新位置的引用被设置。 |
| 26                      | [3.201s][info][gc,heap     ] GC(10) Min Capacity: 4096M(100%) | 堆的最小容量：4096M（100%）。                                | 堆信息              | 堆最小容量，和启动时设置的JVM参数有关                        |
| 27                      | [3.201s][info][gc,heap     ] GC(10) Max Capacity: 4096M(100%) | 堆的最大容量：4096兆字节（100%）。                           | 堆信息              | 堆最大容量，和启动时设置的JVM参数有关                        |
| 28                      | [3.201s][info][gc,heap     ] GC(10) Soft Max Capacity: 4096M(100%) | 堆的软最大容量：4096兆字节（100%）。                         | 堆信息              | 堆软最大容量，和启动时设置的JVM参数有关。软最大容量是指垃圾收集器允许堆内存使用达到的一个限制值。当堆内存使用达到这个值时，垃圾收集器会启动垃圾收集，以避免堆内存耗尽。 |
| 29                      | [3.201s][info][gc,heap     ] GC(10)                Mark Start          Mark End        Relocate Start      Relocate End           High               Low | 堆的标记开始、标记结束、重定位开始和重定位结束的容量信息。   | 堆信息              | 堆的信息，用于分析堆各个阶段容量信息                         |
| 30                      | [3.201s][info][gc,heap     ] GC(10)  Capacity:     4096M (100%)       4096M (100%)       4096M (100%)       4096M (100%)       4096M (100%)       4096M (100%) | 堆的总容量。                                                 | 堆信息              | 堆的总容量信息                                               |
| 31                      | [3.201s][info][gc,heap     ] GC(10)      Free:     2082M (51%)        2078M (51%)        2076M (51%)        2076M (51%)        2082M (51%)        2072M (51%) | 堆的空闲内存信息。                                           | 堆信息              | 堆的空闲信息                                                 |
| 32                      | [3.201s][info][gc,heap     ] GC(10)      Used:     2014M (49%)        2018M (49%)        2020M (49%)        2020M (49%)        2024M (49%)        2014M (49%) | 堆的使用内存信息。                                           | 堆信息              | 堆的使用信息                                                 |
| 33                      | [3.201s][info][gc,heap     ] GC(10)      Live:         -              1806M (44%)        1806M (44%)        1806M (44%)            -                  - | 堆的存活对象信息。                                           | 堆信息              | 堆中存活对象信息                                             |
| 34                      | [3.201s][info][gc,heap     ] GC(10) Allocated:         -                 4M (0%)            6M (0%)            6M (0%)             -                  - | 堆的分配对象信息。                                           | 堆信息              | 堆的分配对象信息                                             |
| 35                      | [3.201s][info][gc,heap     ] GC(10)   Garbage:         -               207M (5%)          207M (5%)          207M (5%)             -                  - | 堆的垃圾信息。                                               | 堆信息              | 堆的垃圾信息。                                               |
| 36                      | [3.201s][info][gc,heap     ] GC(10) Reclaimed:         -                  -                 0M (0%)            0M (0%)             -                  - | 堆中回收（Reclaimed）的对象的大小。                          | 堆信息              | 堆中回收（Reclaimed）的对象的大小。                          |
| 37                      | [3.201s][info][gc          ] GC(10) Garbage Collection (Timer) 2014M(49%)->2020M(49%) | 垃圾收集之前，堆的总容量是2014M（49%的使用率）。垃圾收集之后，堆的总容量增加到了2020M（仍然是49%的使用率）。 | 容量变化            | 第十次回收前后容量变化，虽然有所增加，这是因为GC是和用户程序并行的，所以在收集过程中会有新的对象产生造成容量增加 |

堆的使用情况：
![QQ_1722589694994.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722589699241-1a72be3b-f795-4689-bb98-6baa597bd44b.png#averageHue=%23fefefe&clientId=u1b7633c6-712b-4&from=paste&height=172&id=u34b79fe8&originHeight=215&originWidth=1104&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=30223&status=done&style=stroke&taskId=ua31a2444-e888-4ca6-b635-47f335408ef&title=&width=883.2)

| **-**      | **Mark Start** | **Mark End** | **Relocate Start ** | **Relocate End** | **High**     | **Low **     |
| ---------- | -------------- | ------------ | ------------------- | ---------------- | ------------ | ------------ |
| Capacity:  | 4096M (100%)   | 4096M (100%) | 4096M (100%)        | 4096M (100%)     | 4096M (100%) | 4096M (100%) |
| Free:      | 1518M (37%)    | 1514M (37%)  | 1512M (37%)         | 1512M (37%)      | 1518M (37%)  | 1506M (37%)  |
| Used:      | 2578M (63%)    | 2582M (63%)  | 2584M (63%)         | 2584M (63%)      | 2590M (63%)  | 2578M (63%)  |
| Live:      | -              | 2323M (57%)  | 2323M (57%)         | 2323M (57%)      | -            | -            |
| Allocated: | -              | 4M (0%)      | 6M (0%)             | 6M (0%)          | -            | -            |
| Garbage:   | -              | 254M (6%)    | 254M (6%)           | 254M (6%)        | -            | -            |
| Reclaimed: | -              | -            | 0M (0%)             | 0M (0%)          | -            | -            |

### 总结

**GC(10)事件概览：**

- 触发时间：3.173秒
- 结束时间：3.201秒
- 使用了3个工作线程进行垃圾回收

**各个阶段的耗时：**

- Pause Mark Start: 停顿标记开始阶段，耗时0.006ms
- Concurrent Mark: 并发标记阶段，耗时21.603ms
- Pause Mark End: 停顿标记结束阶段，耗时0.009ms
- Concurrent Mark Free: 并发标记释放阶段，耗时0.001ms
- Concurrent Process Non-Strong References: 并发处理非强引用阶段，耗时0.952ms
- Concurrent Reset Relocation Set: 并发重置重新定位集阶段，耗时0.001ms
- Concurrent Select Relocation Set: 并发选择重新定位集阶段，耗时2.154ms
- Pause Relocate Start: 停顿重新定位开始阶段，耗时0.005ms
- Concurrent Relocate: 并发重新定位阶段，耗时1.372ms

**系统负载和MMU（最小暂停时间利用率）：**

- Load: 1分钟、5分钟、15分钟的系统负载都是0.00，表示系统在这些时间窗口内没有显著的负载变化。
- MMU: 在不同的时间窗口内，最小暂停时间的利用率非常高，从99.4%到100.0%，这表明ZGC在此次GC事件中非常高效，对应用暂停的影响非常小。

**其他信息：**

- Mark Stack Usage: 标记阶段使用了32MB的栈空间。
- NMethods: 注册的NMethods为469，没有注销的NMethods。
- Metaspace: 元空间使用了1MB，提交了1MB，保留了1088MB。
- 引用处理：记录了软引用、弱引用、最终引用和幻象引用的统计数据。
- 小页面、中页面和大页面的使用情况：记录了不同类型页面的数量和大小。
- Forwarding Usage: 重新定位使用了0MB。

**堆的使用情况：**

- 最小、最大和软最大容量都是4096MB，表明堆的容量是固定的。
- 在标记开始时，堆的使用量为2014MB（49%），到标记结束时，堆的使用量为2018MB（49%），重新定位开始时为2020MB（49%），重新定位结束时也为2020MB（49%）。
- 堆的空闲容量在2072MB（51%）到2082MB（51%）之间变化。
- 活动对象占用的内存为1806MB（44%）。
- 已分配的内存为4MB（0%）到6MB（0%）。
- 垃圾对象占用的内存为207MB（5%）。

我们可以发现，这次GC事件中，ZGC显示出了非常高的MMU，**说明对应用暂停的影响很小**。

## 结束阶段日志分析

### 日志详细解释

| **日志行数** | **日志内容**                                                 | **解释内容**                                                 |
| ------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 1            | [5.235s][info][gc,heap,exit] Heap                            | 记录了堆和元空间的使用情况。                                 |
| 2            | [5.235s][info][gc,heap,exit] ZHeap used 3034M, capacity 4096M, max capacity 4096M | Z堆（ZHeap）的使用情况：已使用3034MB，容量4096MB，最大容量4096MB。 |
| 3            | [5.236s][info][gc,heap,exit] Metaspace used 1601K, committed 1792K, reserved 1114112K | 元空间的使用情况：已使用1601KB，已提交1792KB，已保留1114112KB。 |
| 4            | [5.236s][info][gc,heap,exit] class space used 147K, committed 256K, reserved 1048576K | 类空间的使用情况：已使用147KB，已提交256KB，已保留1048576KB。 |

# ZGC 特性探究

## 调优参数大全

下表整理了JVM基本参数以及ZGC调优参数

|                        | **参数**                      | **描述**                                 | **用法示例**                      | **详细解释**                                                 |
| ---------------------- | ----------------------------- | ---------------------------------------- | --------------------------------- | ------------------------------------------------------------ |
| General GC Options     | -XX:MinHeapSize, -Xms         | 设置JVM启动时的初始堆大小                | -Xms512m                          | 用于指定JVM在启动时分配给堆内存的初始大小。如果不指定，JVM会根据默认值或当前系统的可用内存自动选择一个大小。这个参数有助于避免JVM在运行时频繁调整堆大小，从而提高性能。 |
| General GC Options     | -XX:InitialHeapSize, -Xms     | 设置JVM启动时的初始堆大小                | -Xms512m                          | 用于指定JVM在启动时分配给堆内存的初始大小。如果不指定，JVM将根据默认值或当前系统的可用内存自动选择一个大小。这个参数有助于减少JVM在运行时动态调整堆大小的次数，从而提高性能。与`-XX:MaxHeapSize`结合使用，可以有效地控制堆内存的使用。 |
| General GC Options     | -XX:MaxHeapSize, -Xmx         | 设置JVM启动时的最大堆大小                | -Xmx1024m                         | 用于指定JVM堆内存可以扩展到的最大大小。一旦堆内存达到这个限制，JVM将不会继续扩展堆，而是触发垃圾回收以释放空间。这个参数对于控制JVM的内存占用非常重要。 |
| General GC Options     | -XX:SoftMaxHeapSize           | 设置JVM堆的最大软限制大小                | -XX:SoftMaxHeapSize=800m          | 设置了堆内存的一个软限制，当堆内存使用量接近这个限制时，JVM会尝试减少堆的使用量，但不会强制执行。这有助于在不触发Full GC的情况下控制内存使用。 |
| General GC Options     | -XX:ConcGCThreads             | 设置并发垃圾收集器使用的线程数           | -XX:ConcGCThreads=4               | 指定了并发垃圾收集器在执行垃圾回收时使用的线程数。增加线程数可以提高垃圾回收的效率，但也可能增加对应用程序线程的干扰。 |
| General GC Options     | -XX:ParallelGCThreads         | 设置并行垃圾收集器使用的线程数           | -XX:ParallelGCThreads=8           | 用于指定并行垃圾收集器在执行垃圾回收时使用的线程数。并行垃圾收集器在执行时会暂停应用程序线程，因此这个参数对于垃圾回收的性能有直接影响。 |
| General GC Options     | -XX:UseLargePages             | 启用大页面内存使用                       | -XX:UseLargePages                 | 用于启用大页面内存，这可以减少页面置换的开销，提高内存访问效率。大页面内存通常用于需要大量内存的应用程序。 |
| General GC Options     | -XX:UseTransparentHugePages   | 启用透明大页面内存使用                   | -XX:UseTransparentHugePages       | 启用了透明大页面，它允许JVM自动使用大页面内存，而不需要手动配置。这有助于提高内存访问速度，尤其是在处理大量内存时。 |
| General GC Options     | -XX:UseNUMA                   | 启用NUMA (非一致性内存访问) 感知内存分配 | -XX:UseNUMA                       | 用于启用NUMA感知的内存分配策略，它允许JVM根据NUMA架构来分配内存，从而优化跨多个处理器和内存模块的内存访问速度。 |
| General GC Options     | -XX:SoftRefLRUPolicyMSPerMB   | 设置软引用LRU策略的毫秒数每兆字节        | -XX:SoftRefLRUPolicyMSPerMB=1000  | 设置了软引用在LRU（最近最少使用）策略下被垃圾回收器清除之前可以存活的时间，单位是毫秒每兆字节。 |
| General GC Options     | -XX:AllocateHeapAt            | 指定堆内存分配的起始地址                 | -XX:AllocateHeapAt=0x10000000     | 用于指定堆内存分配的起始地址，通常用于特殊场景，如性能测试或系统级编程。 |
| ZGC Options            | -XX:ZAllocationSpikeTolerance | 设置ZGC对内存分配尖峰的容忍度            | -XX:ZAllocationSpikeTolerance=2.0 | 用于设置ZGC对内存分配尖峰的容忍度。它是一个浮点数，表示在垃圾回收周期内允许的最大内存分配量与平均分配量的比率。如果实际分配量超过这个比率，ZGC可能会提前触发垃圾回收以避免内存溢出。较高的值可以减少垃圾回收的频率，但可能会增加内存溢出的风险。 |
| ZGC Options            | -XX:ZCollectionInterval       | 设置ZGC垃圾收集的间隔时间（秒）          | -XX:ZCollectionInterval=120       | 用于设置ZGC垃圾收集器执行垃圾回收的时间间隔，以秒为单位。设置较短的间隔可以更频繁地进行垃圾回收，但可能会增加CPU的使用率。 |
| ZGC Options            | -XX:ZFragmentationLimit       | 设置ZGC的最大堆碎片限制（百分比）        | -XX:ZFragmentationLimit=10        | 设置了ZGC在执行垃圾回收时允许的最大堆碎片百分比。如果碎片超过这个限制，ZGC会尝试进行压缩以减少碎片。 |
| ZGC Options            | -XX:ZMarkStackSpaceLimit      | 设置ZGC标记阶段栈空间的最大大小（MB）    | -XX:ZMarkStackSpaceLimit=64       | 限制了ZGC在标记阶段使用的栈空间的最大大小。如果标记工作负载很大，可能需要增加这个值。 |
| ZGC Options            | -XX:ZProactive                | 启用ZGC的主动回收策略                    | -XX:ZProactive                    | 启用ZGC的主动回收策略，允许ZGC在堆内存使用量未达到阈值时也触发垃圾回收，以减少长时间垃圾回收的可能性。 |
| ZGC Options            | -XX:ZUncommit                 | 启用ZGC的堆内存取消提交功能              | -XX:ZUncommit                     | 允许ZGC在垃圾回收后取消提交不再需要的堆内存，从而减少JVM的内存占用。 |
| ZGC Options            | -XX:ZUncommitDelay            | 设置ZGC取消提交堆内存的延迟时间（秒）    | -XX:ZUncommitDelay=300            | 指定了ZGC在垃圾回收后延迟取消提交堆内存的时间。延迟取消提交可以避免频繁的内存提交和取消操作，从而减少系统开销。 |
| ZGC Diagnostic Options | -XX:ZStatisticsInterval       | 设置ZGC统计信息打印的间隔时间（秒）      | -XX:ZStatisticsInterval=60        | 用于设置ZGC打印统计信息的频率。统计信息可以帮助监控和调试垃圾回收器的行为。 |
| ZGC Diagnostic Options | -XX:ZVerifyForwarding         | 启用ZGC转发指针的验证                    | -XX:ZVerifyForwarding             | 用于启用ZGC在垃圾回收过程中对转发指针的验证。这有助于检测和调试内存管理中的问题，但可能会略微降低性能。 |
| ZGC Diagnostic Options | -XX:ZVerifyMarking            | 启用ZGC标记阶段的验证                    | -XX:ZVerifyMarking                | 启用ZGC在标记阶段对标记过程的验证。通过验证，可以确保标记过程的正确性，但可能会增加额外的性能开销。 |
| ZGC Diagnostic Options | -XX:ZVerifyObjects            | 启用ZGC对象引用的验证                    | -XX:ZVerifyObjects                | 用于启用ZGC对对象引用的验证，确保对象引用的正确性。这对于调试内存泄漏或错误非常有用，但会降低性能。 |
| ZGC Diagnostic Options | -XX:ZVerifyRoots              | 启用ZGC根引用的验证                      | -XX:ZVerifyRoots                  | 启用ZGC对根引用的验证，确保所有根引用都被正确处理。这有助于发现和修复垃圾回收过程中的问题。 |
| ZGC Diagnostic Options | -XX:ZVerifyViews              | 启用ZGC视图映射的验证                    | -XX:ZVerifyViews                  | 用于启用ZGC对内存视图映射的验证。ZGC使用多重映射技术，验证视图映射可以确保映射的正确性，但会影响性能。 |



## ZGC 基本特性

### 暂停时间短

为了体现 ZGC 的暂停时间短，我分别测试了 G1(Garbage-First) 和 Parallel GC ，在保证设置 ZGC 的垃圾收集周期间隔为0.2s的同时，设置了 G1 和 Parallel 的目标最大暂停时间为200ms，同时保证三次日志设置的堆大小均为4g，日志具体的内容分别对应于 BasicCharacteristics/logs/zgc_4g_0.2.log，BasicCharacteristics/logs/parallelgc_4g_0.2.log，BasicCharacteristics/logs/g1_4g_0.2.log，后续的基本特性的分析如吞吐量等也会基于这三个日志分析。
对于日志的分析，使用更加全面可视化的[GCEasy](https://gceasy.io/)进行分析。对三种GC的暂停时间的分析如下：
![image.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722763127634-872d4310-34cd-4488-9553-9afd08b43264.png#averageHue=%23dbc5a4&clientId=uff89f78a-647d-4&from=paste&height=241&id=u4fdab8c0&originHeight=301&originWidth=1441&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=61596&status=done&style=stroke&taskId=uf8f7c992-d03b-4268-9df9-de83599b541&title=&width=1152.8)

| **-**             | **Parallel GC** | **Z GC**  | **G1 GC** |
| ----------------- | --------------- | --------- | --------- |
| Pause GC Time     | 277ms           | 0.00673ms | 58ms      |
| Max Pause GC Time | 380ms           | 0.0120ms  | 80ms      |

通过对比三者的暂停时间可以发现，Parallel GC 的暂停时间是最长的，有277ms，G1 次之，有58ms，**Z GC最低，甚至已经是数量级上的差距，仅有0.00673ms**，可以看出在暂停时间方面，Z GC有很大的优势。**符合Z GC低延迟的特性，同时也符合JDK16优化后所提出的GC时间缩小到1ms的特性**。
另外在JDK16有提到"_GC 暂停时间已经缩小到 1 ms 以内，并且时间复杂度是 o(1)，这也就是说 GC 停顿时间是一个固定值了，**并不会受堆内存大小影响**_"，我对该特性也进行了验证，分别设置堆内存大小和循环内迭代次数（模拟产生对象数量）为4g_2w、4g_4w、8g_2w、8g_4w、16g_2w、16g_4w，得到的实验结果如下：

![image.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722765258476-7f226c94-4e38-451f-91b5-6280667ababc.png#averageHue=%237a9b79&clientId=uff89f78a-647d-4&from=paste&height=473&id=u198ef26d&originHeight=591&originWidth=606&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=85361&status=done&style=stroke&taskId=u660dd722-205d-495a-a86b-a7d927f07fc&title=&width=484.8)

| **-**                   | **平均暂停时间 (AVG)** | **最大暂停时间 (MAX)** |
| ----------------------- | ---------------------- | ---------------------- |
| Z GC (4g 2w)            | 0.00673 ms             | 0.0120 ms              |
| Z GC (4g 4w)（存在OOM） | 0.00663 ms             | 0.0120 ms              |
| Z GC (8g 2w)            | 0.00725 ms             | 0.0120 ms              |
| Z GC (8g 4w)            | 0.00716 ms             | 0.0160 ms              |
| Z GC (16g 2w)           | 0.00735 ms             | 0.0140 ms              |
| Z GC (16g 4w)           | 0.00684 ms             | 0.0170 ms              |

通过分析上面的数据，可以看出**平均暂停时间确实不受堆的大小影响了，整体在0.00700ms左右波动**。对于迭代次数为 4w 的数据和为 2w 的数据进行比较，暂停时间也没有明显的差别，**说明停顿时间不随着活跃对象的大小而增加**，符合Z GC在JDK16中的“GC 暂停时间已经缩小到 1 ms 以内，并且时间复杂度是 o(1)”设定。

### 高吞吐

ZGC具有高吞吐的优点，为了体现Z GC的高吞吐，实验选择了两个其他的垃圾回收器进行对比实验，最终实验的数据如下图所示，其中，ZGC/G1GC/Parallal GC代表垃圾收集器的类型，4g/8g/16g代表设置的内存大小，2w/4w表示迭代次数，越大表示生成的对象越多。
![image.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722766685593-7af71748-6928-4887-bed8-735a256d508e.png#averageHue=%23dcdbc5&clientId=uff89f78a-647d-4&from=paste&height=442&id=ud4e1a95c&originHeight=553&originWidth=1264&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=137738&status=done&style=stroke&taskId=u2c22ecf4-eb02-454a-9cde-65c7069b27e&title=&width=1011.2)

| **Report Name**   | **Throughput** | **Avg GC Time** | **Max GC Time** |
| ----------------- | -------------- | --------------- | --------------- |
| G1 GC 4g 2w       | 55.821%        | 58.0 ms         | 80.0 ms         |
| Parallel GC 4g 2w | 69.906%        | 277 ms          | 380 ms          |
| Z GC 4g 2w        | 99.993%        | 0.00673 ms      | 0.0120 ms       |
| Z GC 4g 4w        | 99.991%        | 0.00663 ms      | 0.0120 ms       |
| Z GC 8g 2w        | 99.993%        | 0.00725 ms      | 0.0120 ms       |
| Z GC 8g 4w        | 99.993%        | 0.00716 ms      | 0.0160 ms       |
| Z GC 16g 2w       | 99.994%        | 0.00735 ms      | 0.0140 ms       |
| Z GC 16g 4w       | 99.993%        | 0.00684 ms      | 0.0170 ms       |

通过对实验的数据总结得出结论：
 **ZGC 的优越吞吐性能**：在所有内存大小（4g、8g、16g）和迭代次数（2w、4w）的配置下，ZGC 显示了极高的吞吐量（99.99%以上），**说明 ZGC 能有效处理大量对象生成和内存管理，适用于对高吞吐量要求较高的应用**。 G1GC 在 4g 内存和 2w 迭代次数的配置下，吞吐量明显较低（55.821%）。Parallel GC 的吞吐量（69.906%）比 G1GC 高，但也要远低于ZGC的量级。
**内存大小和迭代次数对性能的影响**： 随着内存大小从 4g 增加到 8g 和 16g，ZGC 的性能保持稳定，显示出良好的扩展性。不同迭代次数下，ZGC 依旧能维持高吞吐量，表明其对高并发和大量对象生成的处理能力强大，对比之下，G1GC 和 Parallel GC 在相同配置下表现出较大差异。

### 其他特性

#### 内存分配

首先内存消耗方面，可以看出Z GC主要分配给堆区，并且堆区不做新生代，老年代的分区，永久代占用空间很小；G1 和 Parallal 分出了新生代和老年代，ParallalGC新生代比G1新生代占比高，老年代情况相反。![image.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722781755074-65ab6b53-a1a7-49e5-917a-36355c5519e0.png#averageHue=%23b1c0a5&clientId=uff89f78a-647d-4&from=paste&height=244&id=u0102f0e0&originHeight=305&originWidth=1365&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=55187&status=done&style=stroke&taskId=ud97ee2de-9c0f-49b0-b96c-565a4554739&title=&width=1092)

#### GC 阶段

**Z GC阶段：**共统计了并发标记、选择重定位集、并发重定位、处理非强引用、暂停标记结束、暂停标记开始、暂停重定位开始和重置重定位集。  其中**选择重定位集和并发重定位阶段耗时较短且波动较小，显示出较高的效率**。**暂停阶段**（包括标记结束、标记开始和重定位开始）**耗时极短，波动性极小，表明暂停对应用的影响微乎其微**。处理非强引用和重置重定位集阶段耗时最短，且几乎没有波动，显示出极高的稳定性和效率  
![image.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722784233081-3b1a5f99-04d0-44c9-bd50-e5866e5b9e3c.png#averageHue=%238ea58c&clientId=uff89f78a-647d-4&from=paste&height=398&id=uec8f10a7&originHeight=498&originWidth=1425&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=77387&status=done&style=stroke&taskId=udf477654-73e5-4692-ab2a-8a63ae5c062&title=&width=1140)
**G1 GC阶段 **：Young GC 阶段耗时最长，且运行次数最多，**表明当前程序年轻代垃圾回收对应用的影响较大**。Concurrent Marking 和 Root Region Scanning 的运行次数较少，但每次运行的平均时间较长，**显示出并发标记和根区域扫描过程的开销较大**。Remark 和 Cleanup 阶段耗时极短，且标准差小，说明这些阶段的性能稳定，对应用的影响较小。
![image.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722784247645-e4d8159d-9e38-4ac0-9946-602efa10eaea.png#averageHue=%2387a187&clientId=uff89f78a-647d-4&from=paste&height=291&id=u6ea6542c&originHeight=364&originWidth=1429&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=61446&status=done&style=stroke&taskId=ud3ffe596-273c-478b-b5ad-42cc6fb863a&title=&width=1143.2)
**Parallal GC 阶段**：**Minor GC（年轻代GC）的垃圾回收在所有GC活动中耗时最长且执行次数最多**，表明当前应用程序年轻代的垃圾回收对应用程序的运行影响最为显著。**Major GC（老年代GC）的执行次数相对较少，但其每次执行的时间较长**，表明老年代GC对应用程序性能的影响较大，尤其是在单次回收过程中**。Concurrent Marking（并发标记）和Root Region Scanning（根区域扫描）这两个阶段的运行次数相对较少，但每次操作的平均时间较长，**表明并发标记和根区域扫描是GC过程中的主要耗时步骤**，对整体性能有一定的开销。Remark（最终标记）和Cleanup（清理）阶段这两个阶段的耗时相对较短，且性能表现稳定，表明它们对应用程序的运行影响较小。
![image.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722784274006-00f2628e-c99d-41bb-8df2-3ca534cb4681.png#averageHue=%23829f7f&clientId=uff89f78a-647d-4&from=paste&height=382&id=ufb8227d6&originHeight=477&originWidth=1451&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=117823&status=done&style=stroke&taskId=ubcae4c68-6c55-4261-9a78-e8872bc48f2&title=&width=1160.8)

#### 暂停时间和并发时间

下面是G1 GC和Z GC的暂停时间和并发时间的对比：
![image.png](https://cdn.nlark.com/yuque/0/2024/png/23042613/1722784940282-bd588c46-f4f9-4f28-ba04-55a480a3113c.png#averageHue=%23b4c6a9&clientId=uff89f78a-647d-4&from=paste&height=527&id=uc07a11e1&originHeight=659&originWidth=1446&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=84041&status=done&style=stroke&taskId=ua5c58fb9-fb94-4db2-b760-5375e644089&title=&width=1156.8)
从图中可以看出，G1 GC的较长暂停时间是由于其**回收策略需要更频繁地暂停应用线程来处理复杂的内存回收任务**。Z GC的极短暂停时间归功于其并发回收机制。该数据指标也反映了它们各自的设计目标：**G1 GC更注重吞吐量和可预测的暂停时间，而Z GC则侧重于提供低延迟的垃圾回收。**

## JVM 通用参数调优

### **-Xms -Xmx**

根据JVM调优建议，建议将 Xms 和 Xmx 的值设置为相等。这样可以避免在运行过程中进行堆内存的动态扩展，减少由于内存扩展带来的性能开销和不确定性。
所以下面的实验参数中-Xms -Xmx大小设置的相同。实验参数设置如下：

1. 其他参数相同，-Xms -Xmx分别设置为4g,8g,16g，程序产生的对象大小和数量几乎一致
2. 其他参数相同，-Xms -Xmx设置为8g，程序产生的对象数量和总容量依次递增（程序迭代2w次，3w次，4w次）

**zgc_4g_0.2_2w    vs    zgc_8g_0.2_2w    vs    zgc_16g_0.2_2w**

> 日志分析地址：
> 
> 4g：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wNy8zMS96Z2NfNGdfMC4yLnppcC0tMTUtMC0zMg==&channel=WEB](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wNy8zMS96Z2NfNGdfMC4yLnppcC0tMTUtMC0zMg==&channel=WEB)
> 
> 8g：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC80L2U2NmRiODg3LTcyYTctNGRkYi04Zjg0LTgzOGRkMjZiN2M5OC50eHQtLTktMzMtMjM=&channel=WEB](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC80L2U2NmRiODg3LTcyYTctNGRkYi04Zjg0LTgzOGRkMjZiN2M5OC50eHQtLTktMzMtMjM=&channel=WEB)
> 
> 16g：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC80L2E5MzE5MGJkLWI3MjItNDViMy04NzgyLTljMDU2NGQ1YjM4MS50eHQtLTktNDYtOA==&channel=WEB](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC80L2E5MzE5MGJkLWI3MjItNDViMy04NzgyLTljMDU2NGQ1YjM4MS50eHQtLTktNDYtOA==&channel=WEB)

其中4g（8g16g）代表分配的堆内存大小，0.2代表回收周期0.2s，2w代表程序迭代2w次

| **参数**           | **zgc_4g_0.2_2w** | **zgc_8g_0.2_2w** | **zgc_16g_0.2_2w** |
| ------------------ | ----------------- | ----------------- | ------------------ |
| 申请堆空间大小     | 4G                | 8G                | 16G                |
| 使用堆空间峰值大小 | 2.8G              | 2.82G             | 2.88G              |
| GC平均暂停时间     | 0.00673 ms        | 0.00725 ms        | 0.00735 ms         |
| GC最大暂停时间     | 0.0120 ms         | 0.0120 ms         | 0.0140 ms          |
| GC暂停总时间       | 0.343 ms          | 0.413 ms          | 0.397 ms           |

各阶段的对比：

| **平均时间** | **Concurrent Mark** | **Concurrent Select Relocation Set** | **Concurrent Relocate** | **Concurrent Process Non-Strong References** | **Pause Mark End ** | **Pause Relocate Start ** | **Pause Mark Start ** | **Concurrent Reset Relocation Set** |
| ------------ | ------------------- | ------------------------------------ | ----------------------- | -------------------------------------------- | ------------------- | ------------------------- | --------------------- | ----------------------------------- |
| 16g          | 10.8 ms             | 2.96 ms                              | 1.26 ms                 | 0.805 ms                                     | 0.00989 ms          | 0.00628 ms                | 0.00589 ms            | 0.000944 ms                         |
| 8g           | 9.37 ms             | 2.78 ms                              | 1.29 ms                 | 0.766 ms                                     | 0.00958 ms          | 0.00621 ms                | 0.00595 ms            | 0.000947 ms                         |
| 4g           | 9.71 ms             | 2.72 ms                              | 1.65 ms                 | 0.844 ms                                     | 0.00918 ms          | 0.00576 ms                | 0.00524 ms            | 0.00100 ms                          |

可以看出，在堆内存足够容纳分配的所有对象的情况下，-Xms -Xmx设置的大小堆GC的影响不大，参数为4G,8G,16G的GC平均暂停时间十分接近，同时也满足ZGC的暂停时间不会受到堆内存大小的影响的设定。

---

**zgc_8g_0.2_2w    vs    zgc_8g_0.2_3w    vs    zgc_8g_0.2_4w**

> 日志分析地址：
> 
> 2w：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC80L2U2NmRiODg3LTcyYTctNGRkYi04Zjg0LTgzOGRkMjZiN2M5OC50eHQtLTktMzMtMjM=&channel=WEB](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC80L2U2NmRiODg3LTcyYTctNGRkYi04Zjg0LTgzOGRkMjZiN2M5OC50eHQtLTktMzMtMjM=&channel=WEB)
> 
> 3w：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xNy9jNzZiMmM0NC0zNTlhLTQzZmQtYjBhMC1mMTMyMWYwM2RjYWIudHh0LS0xMC0yNC00OA==&channel=WEB](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xNy9jNzZiMmM0NC0zNTlhLTQzZmQtYjBhMC1mMTMyMWYwM2RjYWIudHh0LS0xMC0yNC00OA==&channel=WEB)
> 
> 4w：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC80LzIwYzRiNGU3LTcyY2UtNGVlMy1hOWU2LTUwY2ZiYjBiNTdhNi50eHQtLTktMzYtMzc=&channel=WEB](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC80LzIwYzRiNGU3LTcyY2UtNGVlMy1hOWU2LTUwY2ZiYjBiNTdhNi50eHQtLTktMzYtMzc=&channel=WEB)

其中，2w代表程序迭代2w次，3w代表程序迭代3w次，4w代表程序迭代4w次。

| **参数**           | **zgc_8g_0.2_2w** | **zgc_8g_0.2_3w** | **zgc_8g_0.2_4w** |
| ------------------ | ----------------- | ----------------- | ----------------- |
| 申请堆空间大小     | 8G                | 8G                | 8G                |
| 使用堆空间峰值大小 | 2.82G             | 4.38G             | 5.89G             |
| GC平均暂停时间     | 0.00725 ms        | 0.00727 ms        | 0.00716 ms        |
| GC最大暂停时间     | 0.0120 ms         | 0.0150 ms         | 0.0160 ms         |
| GC暂停总时间       | 0.413 ms          | 0.654 ms          | 1.55 ms           |

各阶段的对比：

| **平均时间** | **Concurrent Mark** | **Concurrent Select Relocation Set** | **Concurrent Relocate** | **Concurrent Process Non-Strong References** | **Pause Mark End ** | **Pause Relocate Start ** | **Pause Mark Start ** | **Concurrent Reset Relocation Set** |
| ------------ | ------------------- | ------------------------------------ | ----------------------- | -------------------------------------------- | ------------------- | ------------------------- | --------------------- | ----------------------------------- |
| 2w           | 9.37 ms             | 2.78 ms                              | 1.29 ms                 | 0.766 ms                                     | 0.00958 ms          | 0.00621 ms                | 0.00595 ms            | 0.000947 ms                         |
| 3w           | 11.7 ms             | 2.78 ms                              | 1.31 ms                 | 0.746 ms                                     | 0.00987 ms          | 0.00620 ms                | 0.00573 ms            | 0.00100 ms                          |
| 4w           | 14.4 ms             | 2.59 ms                              | 1.21 ms                 | 0.759 ms                                     | 0.00986 ms          | 0.00592 ms                | 0.00571 ms            | 0.00103 ms                          |

可以看出，在内存大小分配的相同的情况下（8g），随着迭代次数的增加，产生的对象越来越多，占用的空间越来越大，**相应的GC平均时间依然没有太大变化**，但是对象越多，**触发的GC次数也会越来越多，导致GC的总时间会逐渐增加，并发标记的时间也会增加，表明并发标记的时间和对象的数量是成正比的**。对于涉及STW的几个阶段，几组数据都差不多，**表明ZGC的暂停时间不会随着对象的增多而呈现出正比的趋势，而是保持O(1)的时间复杂度**。

###  **-XX:ConcGCThreads**

通常设置为处理器核心数的1/4到1/2是一个合理的开始点。例如，如果服务器有8个核心，可以设置-XX:ConcGCThreads=4或-XX:ConcGCThreads=6。如果系统上有大量内存或需要处理大量并发请求，可能需要增加线程数。但是，过多的线程可能会导致上下文切换的开销增加，反而降低性能。
实验设置了线程数分别为2/4/6/8来测试各种指标。

> 日志分析地址：
> 
> 2Threads：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xNy84MzU2MWQwNS1iZmI3LTRlNWYtODhhOC1hMzQ2Y2NlYWY1MTkudHh0LS0xMy01Ny00&channel=WEB](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xNy84MzU2MWQwNS1iZmI3LTRlNWYtODhhOC1hMzQ2Y2NlYWY1MTkudHh0LS0xMy01Ny00&channel=WEB)
> 
> 4Threads：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xNy8wYzlmM2MyNS1lMTcyLTRhYzEtYTZjOS1lNGRiZGM3MmZlZGUudHh0LS0xMy01Ny00Mw==&channel=WEB](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xNy8wYzlmM2MyNS1lMTcyLTRhYzEtYTZjOS1lNGRiZGM3MmZlZGUudHh0LS0xMy01Ny00Mw==&channel=WEB)
> 
> 6Threads：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xNy9iMWI0YmJjYi01MTgxLTRhZDQtYWJjYy1mYWRkNTEwODlkMjUudHh0LS0xMy01OC0xOQ==&channel=WEB](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xNy9iMWI0YmJjYi01MTgxLTRhZDQtYWJjYy1mYWRkNTEwODlkMjUudHh0LS0xMy01OC0xOQ==&channel=WEB)
> 
> 8Threads：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xNy81MjFlNjgyOC1jNDRiLTRiMmUtYmUzZi0xZjhmODYzZTZkYWYudHh0LS0xMy01OC00NQ==&channel=WEB](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xNy81MjFlNjgyOC1jNDRiLTRiMmUtYmUzZi0xZjhmODYzZTZkYWYudHh0LS0xMy01OC00NQ==&channel=WEB)

| **参数**           | **2Threads** | **4Threads** | **6Threads** | **8Threads** |
| ------------------ | ------------ | ------------ | ------------ | ------------ |
| 申请堆空间大小     | 4G           | 4G           | 4G           | 4G           |
| 使用堆空间峰值大小 | 2.89G        | 2.9G         | 2.86G        | 2.96G        |
| GC平均暂停时间     | 0.00683 ms   | 0.00706 ms   | 0.00745 ms   | 0.00741 ms   |
| GC最大暂停时间     | 0.0130 ms    | 0.0150 ms    | 0.0130 ms    | 0.0170 ms    |
| GC暂停总时间       | 0.369 ms     | 0.381 ms     | 0.380 ms     | 0.378 ms     |
| 并发标记时间       | 436 ms       | 334 ms       | 324 ms       | 308 ms       |
| 并发总时间         | 523 ms       | 429 ms       | 418 ms       | 402 ms       |

各阶段对比：

| **平均时间** | **Concurrent Mark** | **Concurrent Select Relocation Set** | **Concurrent Relocate** | **Concurrent Process Non-Strong References** | **Pause Mark End ** | **Pause Relocate Start ** | **Pause Mark Start ** | **Concurrent Reset Relocation Set** |
| ------------ | ------------------- | ------------------------------------ | ----------------------- | -------------------------------------------- | ------------------- | ------------------------- | --------------------- | ----------------------------------- |
| 2Threads     | 12.1 ms             | 2.77 ms                              | 1.20 ms                 | 0.880 ms                                     | 0.00939 ms          | 0.00567 ms                | 0.00544 ms            | 0.000944 ms                         |
| 4Threads     | 9.27 ms             | 2.85 ms                              | 1.77 ms                 | 0.698 ms                                     | 0.00956 ms          | 0.00589 ms                | 0.00572 ms            | 0.00111 ms                          |
| 6Threads     | 9.53 ms             | 3.01 ms                              | 1.88 ms                 | 0.666 ms                                     | 0.00982 ms          | 0.00653 ms                | 0.00600 ms            | 0.00100 ms                          |
| 8Threads     | 9.06 ms             | 2.84 ms                              | 2.04 ms                 | 0.640 ms                                     | 0.0105 ms           | 0.00594 ms                | 0.00582 ms            | 0.00124 ms                          |

从表一分析可知，**随着设置的并发线程数增加，并发标记的时间会随之减少，当减少到一定程度时回达到并发性能瓶颈，也就不会有明显的变化**，但是从实验结果可以看出，在设置线程数为2和4之间的差距还是很大的，说明合理的设置并发线程数确实会减少并发时间。另外对于暂停时间，可以看出GC暂停的总时间和平均时间随着并发线程数的增加变化不大，**说明并发线程数并不会影响ZGC的暂停时间**。从表二也可以看出并发线程数的增加会使并发标记时间减少。但是4threads和6threads的数据所表达的趋势和结论略有差异，考虑可能是因为程序之间生成的对象的差异导致的微小差异，对实验整体的结果并不会产生过大的影响。

### **-XX:ParallelGCThreads**

该参数表示STW阶段使用线程数，默认是总核数的60%。在当前环境中核心数为12，所以实验分别设置线程数为2，4，6，8，10，12进行探究。

> 日志分析地址：
> 
> 2：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC8zOTQzNWYyYi01MDNlLTQ3YzEtYmJiOC1hZDg3OThkODNlY2YudHh0LS0xNS0xNC0xNg==&channel=API](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC8zOTQzNWYyYi01MDNlLTQ3YzEtYmJiOC1hZDg3OThkODNlY2YudHh0LS0xNS0xNC0xNg==&channel=API)
> 
> 4：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC9jOWYzYzBiMy0zNWQ1LTQ4YWQtOTk0MC0xYTY0ZDBlMDljOWYudHh0LS0xNS0xNC0yNA==&channel=API](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC9jOWYzYzBiMy0zNWQ1LTQ4YWQtOTk0MC0xYTY0ZDBlMDljOWYudHh0LS0xNS0xNC0yNA==&channel=API)
> 
> 6：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC9jMTU5ZmQ5Yy1iNmFlLTQzODAtYmI2Yy1hMWJlMTM2MTc0MzUudHh0LS0xNS0xNC0zOA==&channel=API](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC9jMTU5ZmQ5Yy1iNmFlLTQzODAtYmI2Yy1hMWJlMTM2MTc0MzUudHh0LS0xNS0xNC0zOA==&channel=API)
> 
> 8：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC9hZGEyZTM4ZC1mYTU3LTRiMzMtYTU3OS1lNjAxODEyOTdlZTAudHh0LS0xNS0xNC00MQ==&channel=API](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC9hZGEyZTM4ZC1mYTU3LTRiMzMtYTU3OS1lNjAxODEyOTdlZTAudHh0LS0xNS0xNC00MQ==&channel=API)
> 
> 10：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC80ZDVjNjAxYS1jY2MzLTRlMzMtYmIwYi1jMmJkMTFlZWM1YzMudHh0LS0xNS0xNC00OQ==&channel=API](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC80ZDVjNjAxYS1jY2MzLTRlMzMtYmIwYi1jMmJkMTFlZWM1YzMudHh0LS0xNS0xNC00OQ==&channel=API)
> 
> 12：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC9hNGU1YWViOS00ZDRkLTQ1YjgtYjZjMi0yMzUyMWNkN2NjNWMudHh0LS0xNS0xNC01OA==&channel=API](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC9hNGU1YWViOS00ZDRkLTQ1YjgtYjZjMi0yMzUyMWNkN2NjNWMudHh0LS0xNS0xNC01OA==&channel=API)

实验结果如下：

| **参数**           | **2Threads** | **4Threads** | **6Threads** | **8Threads** | **10Threads** | **12Threads** |
| ------------------ | ------------ | ------------ | ------------ | ------------ | ------------- | ------------- |
| 申请堆空间大小     | 4G           | 4G           | 4G           | 4G           | 4G            | 4G            |
| 使用堆空间峰值大小 | 3.34G        | 3.34G        | 3.34G        | 3.33G        | 3.34G         | 3.32G         |
| GC平均暂停时间     | 0.00692ms    | 0.00704ms    | 0.00703 ms   | 0.00711 ms   | 0.00779 ms    | 0.00767 ms    |
| GC暂停总时间       | 1.29 ms      | 1.29 ms      | 1.32 ms      | 1.26 ms      | 1.54 ms       | 1.40 ms       |
| 并发总时间         | 1429 ms      | 1316 ms      | 1543 ms      | 1290 ms      | 1655 ms       | 1499 ms       |
| GC次数             | 61           | 60           | 62           | 58           | 65            | 60            |

各阶段对比：

| **平均时间** | **Concurrent Mark** | **Concurrent Select Relocation Set** | **Concurrent Relocate** | **Concurrent Process Non-Strong References** | **Pause Mark End ** | **Pause Relocate Start ** | **Pause Mark Start ** | **Concurrent Reset Relocation Set** |
| ------------ | ------------------- | ------------------------------------ | ----------------------- | -------------------------------------------- | ------------------- | ------------------------- | --------------------- | ----------------------------------- |
| 2Threads     | 8.64 ms             | 2.55 ms                              | 2.53 ms                 | 0.682 ms                                     | 0.00926 ms          | 0.00595 ms                | 0.00556 ms            | 0.00108 ms                          |
| 4Threads     | 8.09 ms             | 2.43 ms                              | 2.27 ms                 | 0.695 ms                                     | 0.00967 ms          | 0.00613 ms                | 0.00531 ms            | 0.000934 ms                         |
| 6Threads     | 9.36 ms             | 2.70 ms                              | 2.48 ms                 | 0.657 ms                                     | 0.00968 ms          | 0.00598 ms                | 0.00540 ms            | 0.00105 ms                          |
| 8Threads     | 8.08 ms             | 2.53 ms                              | 2.47 ms                 | 0.703 ms                                     | 0.00963 ms          | 0.00612 ms                | 0.00559 ms            | 0.00100 ms                          |
| 10Threads    | 9.44 ms             | 2.82 ms                              | 2.71 ms                 | 0.667 ms                                     | 0.0107 ms           | 0.00641 ms                | 0.00632 ms            | 0.00102 ms                          |
| 12Threads    | 9.49 ms             | 2.69 ms                              | 2.25 ms                 | 0.660 ms                                     | 0.0107 ms           | 0.00616 ms                | 0.00610 ms            | 0.000967 ms                         |

从实验数据可知，设置不同的参数时，各种指标变化不大，没有明显的趋势，说明在当前程序环境下，设置不同的ParallelGCThreads对ZGC的垃圾回收影响不大。可以直接使用默认值。

## ZGC 参数调优

### -XX:ZAllocationSpikeTolerance

在应用程序运行过程中，可能会出现短时间内大量内存分配的情况，这被称为分配峰值或分配突发。此时内存需求急剧增加，可能会导致垃圾收集器需要迅速释放内存。-XX:ZAllocationSpikeTolerance 参数用于设定 ZGC 对这种分配峰值的容忍度，即 ZGC 在遇到内存分配突发时可以承受的分配压力。-XX:ZAllocationSpikeTolerance官方的解释是 ZGC 的分配尖峰容忍度。其实就是数值越大，越早触发回收。可以适当调大该配置，更早触发回收，提升垃圾回收速度，但这会提升应用CPU占用。
实验中-XX:ZAllocationSpikeTolerance取值分别为1，2，3，5，7

> 日志分析地址：
> 
> 1：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xNy9kNTkxNjJlOC02ZTA0LTQxMjEtOWZmMS0wNzAwZDE5MDM0OWUudHh0LS0xNS0xMC0yMQ==&channel=WEB](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xNy9kNTkxNjJlOC02ZTA0LTQxMjEtOWZmMS0wNzAwZDE5MDM0OWUudHh0LS0xNS0xMC0yMQ==&channel=WEB)
> 
> 2：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xNy80NGNjY2IyMS04MTBjLTRjODItYWM2Ny00MDg3MTJjOGVlYjMudHh0LS0xNS0xMC0yOQ==&channel=WEB](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xNy80NGNjY2IyMS04MTBjLTRjODItYWM2Ny00MDg3MTJjOGVlYjMudHh0LS0xNS0xMC0yOQ==&channel=WEB)
> 
> 3：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xNy8zYzNlZTY0MS1mYTdlLTQxZmYtYjFlZi1iMjlkMGI1YzllYmEudHh0LS0xNS0xMC0zNg==&channel=WEB](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xNy8zYzNlZTY0MS1mYTdlLTQxZmYtYjFlZi1iMjlkMGI1YzllYmEudHh0LS0xNS0xMC0zNg==&channel=WEB)
> 
> 5：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xNy84OGFjNTJiMy1mZGI5LTRhYWYtYjQxMC01MDA0MWQyYTBjOWYudHh0LS0xNS0xMC00Mw==&channel=WEB](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xNy84OGFjNTJiMy1mZGI5LTRhYWYtYjQxMC01MDA0MWQyYTBjOWYudHh0LS0xNS0xMC00Mw==&channel=WEB)
> 
> 7：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xNy8yNWJmZTQ2ZS05ZWE0LTQxZjEtOTNmNi01ZjFkOGEwNTg2NzMudHh0LS0xNS0xMC00OA==&channel=WEB](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xNy8yNWJmZTQ2ZS05ZWE0LTQxZjEtOTNmNi01ZjFkOGEwNTg2NzMudHh0LS0xNS0xMC00OA==&channel=WEB)

实验结果如下：

| **参数**           | Tolerance1 | Tolerance2 | Tolerance3 | Tolerance5 | Tolerance7 |
| ------------------ | ---------- | ---------- | ---------- | ---------- | ---------- |
| 申请堆空间大小     | 4G         | 4G         | 4G         | 4G         | 4G         |
| 使用堆空间峰值大小 | 2.96G      | 2.96G      | 2.98G      | 2.97G      | 2.96G      |
| GC平均暂停时间     | 0.00705 ms | 0.00684 ms | 0.00698 ms | 0.00701 ms | 0.00693 ms |
| GC暂停总时间       | 0.719 ms   | 0.684 ms   | 0.733 ms   | 0.736 ms   | 0.748 ms   |
| 并发总时间         | 714 ms     | 802 ms     | 851 ms     | 793 ms     | 842 ms     |
| 开始GC时刻         | 0.547 s    | 0.470 s    | 0.393 s    | 0.377 s    | 0.392 s    |

各阶段对比：

| **平均时间** | **Concurrent Mark** | **Concurrent Select Relocation Set** | **Concurrent Relocate** | **Concurrent Process Non-Strong References** | **Pause Mark End ** | **Pause Relocate Start ** | **Pause Mark Start ** | **Concurrent Reset Relocation Set** |
| ------------ | ------------------- | ------------------------------------ | ----------------------- | -------------------------------------------- | ------------------- | ------------------------- | --------------------- | ----------------------------------- |
| Tolerance1   | 8.34 ms             | 2.47 ms                              | 1.09 ms                 | 0.742 ms                                     | 0.00979 ms          | 0.00597 ms                | 0.00538 ms            | 0.000912 ms                         |
| Tolerance2   | 10.0 ms             | 2.41 ms                              | 1.13 ms                 | 0.751 ms                                     | 0.00958 ms          | 0.00568 ms                | 0.00530 ms            | 0.000970 ms                         |
| Tolerance3   | 9.84 ms             | 2.54 ms                              | 1.31 ms                 | 0.785 ms                                     | 0.00989 ms          | 0.00563 ms                | 0.00543 ms            | 0.00100 ms                          |
| Tolerance5   | 9.21 ms             | 2.46 ms                              | 1.01 ms                 | 0.763 ms                                     | 0.00983 ms          | 0.00591 ms                | 0.00529 ms            | 0.000971 ms                         |
| Tolerance7   | 9.52 ms             | 2.45 ms                              | 1.16 ms                 | 0.762 ms                                     | 0.00967 ms          | 0.00567 ms                | 0.00544 ms            | 0.000972 ms                         |

通过分析上述表格可知，当设置-XX:ZAllocationSpikeTolerance分别为1，2，3，5，7时，GC暂停时间，平均暂停时间，并发标记时间，并发总时间等指标变化不大，没有明显上升或者下降的趋势，可知**GC暂停时间和并发时间与-XX:ZAllocationSpikeTolerance没有关系**。分析GC开始时间可知，随着-XX:ZAllocationSpikeTolerance的值增大，GC开始的时间有明显的变小的趋势，当取值为357时变化不明显，说明已经达到了瓶颈，因此得出结论，**-XX:ZAllocationSpikeTolerance设置的值越大，触发的GC时间越早，对内存分配突发时可以承受的分配压力越大**。

### **-XX:ZCollectionInterval**

 -XX:ZCollectionInterval：ZGC发生的最小时间间隔，单位秒。对于一些对实时性要求较高的应用场景，可能需要将间隔设置得相对短一些，以确保内存能够及时回收，减少因内存不足导致的延迟。在实验中设置的参数分别为0.05，0.10，0.15，0.20，0.30，0.50，分别对应50ms，100ms，150ms，200ms，300ms，500ms。

> 日志分析地址：
> 
> 50ms：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC84MTJlZGE0ZC0wZjdlLTQ1ZGYtOTRmMy0yYzBjODY3YmI0ZGUudHh0LS0yLTIyLTA=&channel=API](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC84MTJlZGE0ZC0wZjdlLTQ1ZGYtOTRmMy0yYzBjODY3YmI0ZGUudHh0LS0yLTIyLTA=&channel=API)
> 
> 100ms：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC8yOTQwNDU1MS1lOTFmLTQ4YjYtYTk2YS03MGQ3YTljMzFiYTcudHh0LS0yLTIyLTc=&channel=API](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC8yOTQwNDU1MS1lOTFmLTQ4YjYtYTk2YS03MGQ3YTljMzFiYTcudHh0LS0yLTIyLTc=&channel=API)
> 
> 150ms：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC8yZmU2NWYxYi1mZTUzLTQ4NjItYTdmNS1kZGU1OGZiMWQ5NzkudHh0LS0yLTIyLTE0&channel=API](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC8yZmU2NWYxYi1mZTUzLTQ4NjItYTdmNS1kZGU1OGZiMWQ5NzkudHh0LS0yLTIyLTE0&channel=API)
> 
> 200ms：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC8yOWVkOGFlOC03NWE1LTQ1ZmQtYmFhZC00ODlkNWY1ZGI2YjkudHh0LS0yLTIyLTIx&channel=API](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC8yOWVkOGFlOC03NWE1LTQ1ZmQtYmFhZC00ODlkNWY1ZGI2YjkudHh0LS0yLTIyLTIx&channel=API)
> 
> 300ms：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC9lYjg5ZGU2MS1hZDMwLTRiNmYtODBjNC0wODMzYmZlNWU4ZTIudHh0LS0yLTI0LTUw&channel=API](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC9lYjg5ZGU2MS1hZDMwLTRiNmYtODBjNC0wODMzYmZlNWU4ZTIudHh0LS0yLTI0LTUw&channel=API)
> 
> 500ms：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC8xMzQ4NDgzYi02YjE0LTQwNzEtYjc3MC1mZjg1ZmNlYTg1ODEudHh0LS0yLTI4LTQz&channel=API](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC8xMzQ4NDgzYi02YjE0LTQwNzEtYjc3MC1mZjg1ZmNlYTg1ODEudHh0LS0yLTI4LTQz&channel=API)

实验结果如下：

| **参数**           | **50ms**   | **100ms**  | **150ms**  | **200ms**  | **300ms**  | **500ms**  |
| ------------------ | ---------- | ---------- | ---------- | ---------- | ---------- | ---------- |
| 申请堆空间大小     | 4G         | 4G         | 4G         | 4G         | 4G         | 4G         |
| 使用堆空间峰值大小 | 2.97G      | 2.97G      | 2.96G      | 2.97G      | 2.96G      | 2.98G      |
| GC平均暂停时间     | 0.00707 ms | 0.00731 ms | 0.00720 ms | 0.00702 ms | 0.00710 ms | 0.00725 ms |
| GC暂停总时间       | 0.700 ms   | 0.768 ms   | 0.756 ms   | 0.688 ms   | 0.682 ms   | 0.739 ms   |
| 并发总时间         | 759 ms     | 741 ms     | 759 ms     | 802 ms     | 736ms      | 924 ms     |
| 吞吐量             | 99.993%    | 99.993%    | 99.993%    | 99.993%    | 99.993%    | 99.993%    |

各阶段对比：

| **平均时间** | **Concurrent Mark** | **Concurrent Select Relocation Set** | **Concurrent Relocate** | **Concurrent Process Non-Strong References** | **Pause Mark End ** | **Pause Relocate Start ** | **Pause Mark Start ** | **Concurrent Reset Relocation Set** |
| ------------ | ------------------- | ------------------------------------ | ----------------------- | -------------------------------------------- | ------------------- | ------------------------- | --------------------- | ----------------------------------- |
| 50ms         | 9.45 ms             | 2.49 ms                              | 0.898 ms                | 0.723 ms                                     | 0.00964 ms          | 0.00603 ms                | 0.00555 ms            | 0.000939 ms                         |
| 100ms        | 8.45 ms             | 2.47 ms                              | 1.04 ms                 | 0.760 ms                                     | 0.0101 ms           | 0.00623 ms                | 0.00566 ms            | 0.00100 ms                          |
| 150ms        | 8.70 ms             | 2.55 ms                              | 0.963 ms                | 0.767 ms                                     | 0.0100 ms           | 0.00609 ms                | 0.00551 ms            | 0.000971 ms                         |
| 200ms        | 9.87 ms             | 2.56 ms                              | 1.37 ms                 | 0.758 ms                                     | 0.00973 ms          | 0.00582 ms                | 0.00547 ms            | 0.00100 ms                          |
| 300ms        | 9.30 ms             | 2.49 ms                              | 1.12 ms                 | 0.797 ms                                     | 0.0100 ms           | 0.00603 ms                | 0.00525 ms            | 0.00100 ms                          |
| 500ms        | 11.2 ms             | 2.56 ms                              | 1.36 ms                 | 0.783 ms                                     | 0.0102 ms           | 0.00588 ms                | 0.00562 ms            | 0.00100 ms                          |

从表中数据分析可知，在并发时间和暂停时间等指标方面，设置不同的 -XX:ZCollectionInterval参数对暂停时间等方面影响不大。分析该参数应该和程序的运行特性有关。另外在吞吐量方面，各参数的吞吐量都达到了99.993%，表明ZGC的吞吐量方面做的优化已经很好了。

### **-XX:+UnlockDiagnosticVMOptions -XX:-ZProactive**

该参数表示是否启用主动回收，默认开启，这里的配置表示关闭。ZProactive 允许垃圾收集器在检测到即将到来的内存压力时，提前主动进行垃圾回收，而不是等到内存使用接近极限时才触发回收。禁用此功能垃圾回收只会在必要时进行，而不提前。
如果启用 -XX:+ZProactive，ZGC 会更加积极地管理内存，提前进行回收，可能有助于在高负载下保持更稳定的内存使用，减少内存使用高峰。
如果禁用 -XX:-ZProactive，ZGC 会采取更保守的策略，只在需要时进行回收，这可能会导致在内存压力下出现延迟或抖动。

> 日志分析地址：
> 
> 关闭主动回收：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC81YTM2MmI0Zi00Y2NjLTQ3MmMtYWNmNy0xODY4YzMzY2E0NTQudHh0LS0xMy0yMC00Mg==&channel=API](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC81YTM2MmI0Zi00Y2NjLTQ3MmMtYWNmNy0xODY4YzMzY2E0NTQudHh0LS0xMy0yMC00Mg==&channel=API)
> 
> 开启主动回收：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC9hOGU1MDQ1Zi0yZDdlLTQxZGEtOWEwYi0wOTRhNjA2YTgxYzgudHh0LS0xMy0yMC01Nw==&channel=API](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC9hOGU1MDQ1Zi0yZDdlLTQxZGEtOWEwYi0wOTRhNjA2YTgxYzgudHh0LS0xMy0yMC01Nw==&channel=API)

实验结果如下：

| **参数**           | **close**  | **open**   |
| ------------------ | ---------- | ---------- |
| 申请堆空间大小     | 4G         | 4G         |
| 使用堆空间峰值大小 | 2.97G      | 2.97G      |
| GC平均暂停时间     | 0.00735 ms | 0.00720 ms |
| GC暂停总时间       | 1.04 ms    | 1.06 ms    |
| 并发总时间         | 1126 ms    | 1168 ms    |
| 吞吐量             | 99.993%    | 99.993%    |
| GC开始时间         | 0.543 s    | 0.412 s    |

各阶段对比：

| **平均时间** | **Concurrent Mark** | **Concurrent Select Relocation Set** | **Concurrent Relocate** | **Concurrent Process Non-Strong References** | **Pause Mark End ** | **Pause Relocate Start ** | **Pause Mark Start ** | **Concurrent Reset Relocation Set** |
| ------------ | ------------------- | ------------------------------------ | ----------------------- | -------------------------------------------- | ------------------- | ------------------------- | --------------------- | ----------------------------------- |
| close        | 9.11 ms             | 2.60 ms                              | 2.45 ms                 | 0.675 ms                                     | 0.00974 ms          | 0.00698 ms                | 0.00532 ms            | 0.000979 ms                         |
| open         | 9.19 ms             | 2.53 ms                              | 2.14 ms                 | 0.780 ms                                     | 0.00973 ms          | 0.00645 ms                | 0.00543 ms            | 0.000980 m                          |

从表中分析可知，在打开主动回收和关闭主动回收时，两个日志的并发时间，暂停时间都十分接近，可见该参数对GC暂停时间以及并发时间没有影响。从开始GC时间来看，关闭主动回收的GC开始时间（0.543s）要明显晚于开启主动回收的GC开始时间（0.412s），**表明启用 -XX:+ZProactive，ZGC 会提前进行回收**，可能有助于在高负载下保持更稳定的内存使用，减少内存使用高峰。

### -XX:ZFragmentationLimit

该参数用于设置内存碎片的阈值（以百分比为单位）。当内存碎片达到这个阈值时，ZGC 会尝试进行压缩。默认-XX:ZFragmentationLimit=25 表示当内存碎片化程度超过这个百分比时，ZGC 会强制进行一次垃圾回收，以减少碎片化。 为了能够将内存分配的更多（接近4G），增加了2500次迭代
实验中设置该参数的取值分别为5，20，35，50，65，80

> 日志分析地址：
> 
> 5%：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC83NzQyZDJiMC0yZTU0LTQ0MWEtODRjNi1iYTQ5ZDIyZmEzOTIudHh0LS0xNC00NC00Ng==&channel=API](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC83NzQyZDJiMC0yZTU0LTQ0MWEtODRjNi1iYTQ5ZDIyZmEzOTIudHh0LS0xNC00NC00Ng==&channel=API)
> 
> 20%：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC8yODdhYTAzZC1hNmU0LTQ3NDktOGI0ZC1mOGUyODEyOGQ3OGEudHh0LS0xNC00NC01Ng==&channel=API](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC8yODdhYTAzZC1hNmU0LTQ3NDktOGI0ZC1mOGUyODEyOGQ3OGEudHh0LS0xNC00NC01Ng==&channel=API)
> 
> 35%：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC8yMGE2MTE4OS1jNGRiLTQyZTAtYjE4NC1lYzcxN2EyMjM1MWQudHh0LS0xNC00NS01&channel=API](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC8yMGE2MTE4OS1jNGRiLTQyZTAtYjE4NC1lYzcxN2EyMjM1MWQudHh0LS0xNC00NS01&channel=API)
> 
> 50%：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC81NGY0YjMyYy1jMTQxLTRhMDgtYTE2OC0xODViZjQxODFmMzQudHh0LS0xNC00NS0xMw==&channel=API](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC81NGY0YjMyYy1jMTQxLTRhMDgtYTE2OC0xODViZjQxODFmMzQudHh0LS0xNC00NS0xMw==&channel=API)
> 
> 65%：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC9mZjY0ZGVjMy04MTEwLTQ4ODYtODcyNi1jMDNkZGM5YzVkY2QudHh0LS0xNC00NS0yNw==&channel=API](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC9mZjY0ZGVjMy04MTEwLTQ4ODYtODcyNi1jMDNkZGM5YzVkY2QudHh0LS0xNC00NS0yNw==&channel=API)
> 
> 80%：[https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC9mZWNlNzRjOC1lYzA2LTQ5YjMtOGU0Ni0xMWMwNTg0YTQ0ZDcudHh0LS0xNC00NS0zNQ==&channel=API](https://gceasy.io/my-gc-report.jsp?p=YXJjaGl2ZWQvMjAyNC8wOC8xOC9mZWNlNzRjOC1lYzA2LTQ5YjMtOGU0Ni0xMWMwNTg0YTQ0ZDcudHh0LS0xNC00NS0zNQ==&channel=API)

实验结果如下：

| **参数**           | **5%**    | **20%**   | **35%**    | **50%**    | **65%**    | **80%**    |
| ------------------ | --------- | --------- | ---------- | ---------- | ---------- | ---------- |
| 申请堆空间大小     | 4G        | 4G        | 4G         | 4G         | 4G         | 4G         |
| 使用堆空间峰值大小 | 3.32G     | 3.34G     | 3.34G      | 3.34G      | 3.36G      | 3.35G      |
| GC平均暂停时间     | 0.00688ms | 0.00666ms | 0.00708 ms | 0.00688 ms | 0.00719 ms | 0.00685 ms |
| GC暂停总时间       | 1.12 ms   | 1.02 ms   | 1.17 ms    | 1.11 ms    | 1.14 ms    | 1.07 ms    |
| 并发总时间         | 1230 ms   | 1200 ms   | 1286 ms    | 1198 ms    | 1172 ms    | 1098 ms    |
| 开始GC时刻         | 0.387 s   | 0.387 s   | 0.387 s    | 0.389 s    | 0.386 s    | 0.388 s    |
| GC次数             | 54        | 50        | 54         | 53         | 52         | 51         |

各阶段对比：

| **平均时间** | **Concurrent Mark** | **Concurrent Select Relocation Set** | **Concurrent Relocate** | **Concurrent Process Non-Strong References** | **Pause Mark End ** | **Pause Relocate Start ** | **Pause Mark Start ** | **Concurrent Reset Relocation Set** |
| ------------ | ------------------- | ------------------------------------ | ----------------------- | -------------------------------------------- | ------------------- | ------------------------- | --------------------- | ----------------------------------- |
| 5%           | 8.26 ms             | 3.11 ms                              | 2.48 ms                 | 0.681 ms                                     | 0.00969 ms          | 0.00585 ms                | 0.00513 ms            | 0.000981 ms                         |
| 20%          | 8.89 ms             | 2.68 ms                              | 2.39 ms                 | 0.682 ms                                     | 0.00941 ms          | 0.00557 ms                | 0.00500 ms            | 0.00104 ms                          |
| 35%          | 9.09 ms             | 2.43 ms                              | 2.10 ms                 | 0.674 ms                                     | 0.00978 ms          | 0.00605 ms                | 0.00540 ms            | 0.000982 ms                         |
| 50%          | 9.02 ms             | 2.40 ms                              | 1.10 ms                 | 0.647 ms                                     | 0.00965 ms          | 0.00570 ms                | 0.00530 ms            | 0.000852 ms                         |
| 65%          | 8.81 ms             | 2.48 ms                              | 1.31 ms                 | 0.704 ms                                     | 0.0100 ms           | 0.00591 ms                | 0.00562 ms            | 0.000906 ms                         |
| 80%          | 8.96 ms             | 2.36 ms                              | 0.673 ms                | 0.159 ms                                     | 0.00963 ms          | 0.00577 ms                | 0.00513 ms            | 0.000462 ms                         |

通过分析实验数据可知，当设置内存碎片阈值分别为5%，20%，35%，50%，65%，80%时，各项指标的变化均不明显，理论上回收次数应该随着内存碎片阈值的增大而减少，实验数据有极不明显的下降趋势，**考虑到这可能是ZGC的压缩做的优化，将所有活动对象移动到内存的一端，从而消除内存碎片的过程**。这有助于提高内存分配的效率。所以实验上的结果不明显。 

# 参考

jdk17官方源码：[https://github.com/openjdk/jdk17/tree/master/src/hotspot/cpu/aarch64/gc/z](https://github.com/openjdk/jdk17/tree/master/src/hotspot/cpu/aarch64/gc/z)

OpenJDK官方文档：[https://openjdk.org/groups/build/doc/building.html](https://openjdk.org/groups/build/doc/building.html)

美团新一代垃圾回收器ZGC的探索与实践：[https://tech.meituan.com/2020/08/06/new-zgc-practice-in-meituan.html](https://tech.meituan.com/2020/08/06/new-zgc-practice-in-meituan.html)

Z GC 官方PDF：[https://cr.openjdk.java.net/~pliden/slides/ZGC-OracleDevLive-2022.pdf](https://link.zhihu.com/?target=https%3A//cr.openjdk.java.net/~pliden/slides/ZGC-OracleDevLive-2022.pdf)

jdk17zgc特性介绍：[https://malloc.se/blog/zgc-jdk17](https://malloc.se/blog/zgc-jdk17)

zgc调优：[https://roll.sohu.com/a/750990972_121124363](https://roll.sohu.com/a/750990972_121124363)

图解ZGC：[https://mp.weixin.qq.com/s?__biz=MzAwNTQ4MTQ4NQ==&mid=2453586896&idx=1&sn=cf74a9f6c4e2686093224574e952f352&chksm=8cd196b2bba61fa45db6317600d200b94b885365bb1b1bbea478929138e0862cd5a7cccc00e4&scene=27]
(https://mp.weixin.qq.com/s?__biz=MzAwNTQ4MTQ4NQ==&mid=2453586896&idx=1&sn=cf74a9f6c4e2686093224574e952f352&chksm=8cd196b2bba61fa45db6317600d200b94b885365bb1b1bbea478929138e0862cd5a7cccc00e4&scene=27)

zgc详细解析：[https://zhuanlan.zhihu.com/p/585254683](https://zhuanlan.zhihu.com/p/585254683)

zgc详细解析及GC调优：[https://www.163.com/dy/article/J662AQIQ0511D3QS.html](https://www.163.com/dy/article/J662AQIQ0511D3QS.html)

GC日志分析工具：[https://gceasy.io/](https://gceasy.io/)

Oracle JVM 规范：[https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.2](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.2)

腾讯云：[https://cloud.tencent.com.cn/developer/article/2296563](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.2)































