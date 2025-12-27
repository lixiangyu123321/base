你想了解 Java 中的 `java.lang.System` 类的使用方法，这是 Java 核心内置类，提供了大量与系统环境、输入输出、资源管理相关的核心功能。下面从**核心使用场景、常用方法详解、实战示例**三个方面，全面讲解 `System` 类的使用。

### 一、`System` 类的基础认知
1.  **类特性**：`java.lang.System` 是一个**最终类（`final`）**，无法被继承；所有可用方法和属性都是**静态（`static`）**的，无需实例化（也无法实例化，构造方法被私有化），直接通过 `System.xxx` 调用即可。
2.  **核心功能领域**：
    - 系统属性的获取与设置（你代码中用到的 `System.getProperty()`/`System.setProperty()`）；
    - 标准输入/输出/错误流（`System.in`/`System.out`/`System.err`）；
    - 系统时间与纳秒计数（`System.currentTimeMillis()`/`System.nanoTime()`）；
    - 数组拷贝（`System.arraycopy()`）；
    - 虚拟机退出（`System.exit()`）。

### 二、`System` 类的核心使用场景与常用方法详解
#### 场景 1：系统属性（JVM 环境变量）的获取与设置（你代码的核心场景）
JVM 运行时会维护一组**系统属性（键值对，均为 `String` 类型）**，包含 JVM 版本、操作系统信息、用户自定义配置等。`System` 提供了两个核心方法操作系统属性。

##### 1. `System.setProperty(String key, String value)`：设置系统属性
- 功能：向 JVM 系统属性中添加/修改一个键值对，`key` 为属性名，`value` 为属性值；
- 特点：若 `key` 已存在，会覆盖原有属性值；若不存在，会新增该属性；
- 你的代码示例：
  ```java
  // 新增/覆盖系统属性 app.name，值为 tiangong-api
  System.setProperty("app.name", "tiangong-api");
  // 禁用 Dubbo 协议注册，设置 dubbo.protocol.register 为 false
  System.setProperty("dubbo.protocol.register", "false");
  ```
- 注意：设置的系统属性仅在**当前 JVM 进程生命周期内有效**，进程退出后失效，不会修改操作系统的环境变量。

##### 2. `System.getProperty(String key)`：获取系统属性
- 功能：根据属性名 `key`，获取对应的系统属性值；
- 特点：若 `key` 不存在，返回 `null`；可搭配默认值重载方法 `System.getProperty(String key, String defaultValue)`（若 `key` 不存在，返回指定默认值，更安全）；
- 你的代码示例：
  ```java
  // 获取 app.loc 属性值，不存在返回 null
  return System.getProperty("app.loc");
  
  // 重载方法示例：获取 app.env，不存在返回 "dev"（更推荐，避免空指针）
  String appEnv = System.getProperty("app.env", "dev");
  ```

##### 3. 补充：常用内置系统属性（无需手动设置，JVM 自动提供）
| 内置属性 key | 含义 | 示例值 |
|--------------|------|--------|
| `java.version` | Java 运行时版本 | 1.8.0_301 |
| `os.name` | 操作系统名称 | Windows 10 / Linux |
| `user.dir` | 当前工作目录（应用启动路径） | D:\projects\tiangong-api |
| `user.name` | 当前登录用户名称 | admin |

#### 场景 2：标准输入/输出/错误流（日常开发最常用）
`System` 提供了三个静态成员变量，对应系统的标准流，用于数据的输入输出。

##### 1. `System.out`：标准输出流（控制台打印）
- 功能：向默认输出设备（通常是控制台）输出数据，最常用方法 `System.out.println()`（打印并换行）、`System.out.print()`（打印不换行）；
- 示例：
  ```java
  // 控制台打印日志（你的配置变更监听可使用）
  System.out.println("应用名称：" + System.getProperty("app.name", "tiangong-api"));
  ```

##### 2. `System.err`：标准错误流（控制台打印错误信息）
- 功能：向默认错误输出设备（通常是控制台）输出错误信息，打印内容通常为红色（IDE 中），用于区分普通日志和错误日志；
- 示例：
  ```java
  try {
      // 模拟异常
      Integer.parseInt("abc");
  } catch (Exception e) {
      // 打印错误信息
      System.err.println("数据转换失败：" + e.getMessage());
  }
  ```

##### 3. `System.in`：标准输入流（读取控制台输入）
- 功能：从默认输入设备（通常是键盘）读取输入数据，需搭配 `Scanner` 或 `BufferedReader` 使用；
- 示例（搭配 `Scanner` 读取用户输入）：
  ```java
  import java.util.Scanner;

  public class SystemInDemo {
      public static void main(String[] args) {
          Scanner scanner = new Scanner(System.in);
          System.out.print("请输入你的姓名：");
          String name = scanner.nextLine();
          System.out.println("你好，" + name);
          scanner.close();
      }
  }
  ```

#### 场景 3：获取系统时间（计时、日志打标常用）
##### 1. `System.currentTimeMillis()`：获取当前系统时间戳（毫秒级）
- 功能：返回从「1970年1月1日 00:00:00 GMT」（Unix 纪元时间）到当前时刻的毫秒数（`long` 类型）；
- 特点：受系统时间影响（若手动修改系统时间，返回值会变化），精度为毫秒，适合用于**日志打标、普通计时、日期转换**；
- 示例：
  ```java
  // 1. 日志打标
  long currentTime = System.currentTimeMillis();
  System.out.println("[" + currentTime + "] 应用启动中...");
  
  // 2. 计算方法执行耗时
  long start = System.currentTimeMillis();
  // 模拟耗时操作
  Thread.sleep(1000);
  long end = System.currentTimeMillis();
  System.out.println("方法执行耗时：" + (end - start) + " 毫秒");
  ```

##### 2. `System.nanoTime()`：获取纳秒级计时值（高精度计时）
- 功能：返回一个相对的纳秒数（`long` 类型），基于 JVM 启动后的内部时钟，**不是绝对时间**；
- 特点：不受系统时间影响，精度为纳秒（1 纳秒 = 10^-9 秒），适合用于**高精度计时（如算法性能测试）**，不适合用于日期转换；
- 示例：
  ```java
  long startNano = System.nanoTime();
  // 模拟高精度耗时操作
  for (int i = 0; i < 100000; i++) {
      Math.sqrt(i);
  }
  long endNano = System.nanoTime();
  System.out.println("循环执行耗时：" + (endNano - startNano) + " 纳秒");
  ```

#### 场景 4：数组拷贝（高效拷贝数组，比手动循环更优）
`System.arraycopy(Object src, int srcPos, Object dest, int destPos, int length)`
- 功能：将源数组 `src` 中的数据，拷贝到目标数组 `dest` 中，是 Java 中最高效的数组拷贝方法之一（底层为本地方法，性能优于手动 for 循环）；
- 参数说明：
    - `src`：源数组（要拷贝的数组）；
    - `srcPos`：源数组的起始拷贝索引（从第几个元素开始拷贝）；
    - `dest`：目标数组（接收拷贝数据的数组）；
    - `destPos`：目标数组的起始存放索引（从第几个元素开始存放）；
    - `length`：要拷贝的元素个数；
- 示例：
  ```java
  public class SystemArrayCopyDemo {
      public static void main(String[] args) {
          int[] srcArray = {1, 2, 3, 4, 5};
          int[] destArray = new int[10]; // 目标数组长度 10
  
          // 拷贝 srcArray 的全部元素到 destArray，从 destArray 索引 2 开始存放
          System.arraycopy(srcArray, 0, destArray, 2, srcArray.length);
  
          // 打印目标数组
          for (int num : destArray) {
              System.out.print(num + " "); // 输出：0 0 1 2 3 4 5 0 0 0
          }
      }
  }
  ```

#### 场景 5：退出当前 JVM 进程（`System.exit(int status)`）
- 功能：强制终止当前正在运行的 JVM 进程，`status` 为退出状态码（`int` 类型）；
- 状态码约定：`0` 表示正常退出（无错误），非 0 表示异常退出（如 `1` 表示通用错误，`2` 表示参数错误，可自定义）；
- 特点：调用后会立即终止 JVM，后续代码不会执行（即使在 `try-finally` 中，`finally` 块也可能无法完全执行，需谨慎使用）；
- 示例：
  ```java
  public class SystemExitDemo {
      public static void main(String[] args) {
          try {
              String appName = System.getProperty("app.name");
              if (appName == null) {
                  System.err.println("应用名称未配置，异常退出！");
                  System.exit(1); // 非 0 状态码，异常退出
              }
              System.out.println("应用正常运行，名称：" + appName);
          } catch (Exception e) {
              System.exit(2); // 其他异常，退出状态码 2
          }
          // 正常退出
          System.exit(0);
      }
  }
  ```

### 三、`System` 类在你代码中的整体使用总结
在你的项目启动代码中，`System` 类的核心作用是**配置 JVM 系统属性，实现环境自适应**：
1.  通过 `System.setProperty()` 设置应用名称、环境、配置中心地址等自定义属性，为应用启动提供必要配置；
2.  通过 `System.getProperty()` 读取 `app.loc` 属性，判断是否为本地实例，进而决定是否禁用 Dubbo 注册；
3.  这些属性仅在当前 JVM 进程中有效，保证了本地开发环境与服务器部署环境的配置隔离，不互相干扰。

### 总结
1.  `System` 是 Java 核心静态类，无需实例化，直接通过 `System.xxx` 调用方法/属性；
2.  核心使用场景：系统属性操作（`setProperty()`/`getProperty()`）、标准流输出（`System.out`）、时间获取（`currentTimeMillis()`）、数组拷贝（`arraycopy()`）、JVM 退出（`exit()`）；
3.  你代码中主要使用了「系统属性操作」场景，用于应用启动前的环境配置，是 Java 项目中配置隔离的常用实践；
4.  注意：`System` 类的部分方法（如 `exit()`、`arraycopy()`）需谨慎使用，避免造成意外后果（如进程强制终止、数组索引越界）。