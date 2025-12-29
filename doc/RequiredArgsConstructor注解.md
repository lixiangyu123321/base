这个`@RequiredArgsConstructor`注解是**Lombok框架**提供的核心注解之一，核心作用是：**自动为类中所有「必须初始化的字段」生成对应的构造方法**，无需手动编写重复的构造器代码。

### 一、核心概念：「必须初始化的字段」
Lombok判定“必须初始化”的字段包含两类：
1. `final`修饰的字段（Java语法要求必须初始化，否则编译报错）；
2. 被`@NonNull`注解标记的字段（Lombok约定必须初始化，否则抛空指针）。

简单说：这个注解会扫描类中上述两类字段，自动生成构造方法，参数列表就是这些字段（顺序与字段声明顺序一致）。

### 二、注解属性详解
你贴出的注解定义中，几个核心属性的作用如下：

| 属性名         | 类型                | 默认值       | 作用                                                                 |
|----------------|---------------------|--------------|----------------------------------------------------------------------|
| `staticName`   | `String`            | 空字符串     | 若指定值（如`of`），则生成**静态工厂方法**（而非普通构造器），例如`public static Xxx of(字段1, 字段2) { ... }` |
| `onConstructor`| `AnyAnnotation[]`   | 空数组       | 用于给生成的构造方法添加自定义注解（如`@Autowired`），语法是`@RequiredArgsConstructor(onConstructor_ = {@Autowired})`（注意下划线，是Lombok的语法兼容处理） |
| `access`       | `AccessLevel`       | `PUBLIC`     | 指定生成构造方法的访问权限，可选值：`PUBLIC`/`PROTECTED`/`PACKAGE`/`PRIVATE` |
| `AnyAnnotation`| 内部注解（已废弃）  | -            | 早期用于兼容注解声明的占位注解，现已废弃，无需关注                     |

### 三、使用示例（直观理解）
#### 1. 基础用法（生成普通构造器）
```java
import lombok.RequiredArgsConstructor;
import lombok.NonNull;

@RequiredArgsConstructor // 自动生成构造器
public class User {
    private final Long id; // final字段（必须初始化）
    @NonNull private String name; // @NonNull字段（必须初始化）
    private Integer age; // 普通字段（不会加入构造器）
}

// Lombok自动生成的代码（编译期生成，源码中不可见）：
// public User(Long id, String name) {
//     if (name == null) {
//         throw new NullPointerException("name is marked non-null but is null");
//     }
//     this.id = id;
//     this.name = name;
// }
```

#### 2. 进阶用法（静态工厂方法）
```java
@RequiredArgsConstructor(staticName = "of") // 指定静态工厂方法名
public class User {
    private final Long id;
    @NonNull private String name;
}

// 自动生成：
// private User(Long id, String name) { // 构造器变为private
//     if (name == null) throw new NullPointerException("name");
//     this.id = id;
//     this.name = name;
// }
// 
// public static User of(Long id, String name) { // 静态工厂方法
//     return new User(id, name);
// }

// 使用时：User user = User.of(1L, "张三");
```

#### 3. 自定义访问权限
```java
@RequiredArgsConstructor(access = AccessLevel.PRIVATE) // 构造器私有
public class User {
    private final Long id;
}

// 生成：private User(Long id) { this.id = id; }
```

### 四、关键注意事项
1. **仅处理final/@NonNull字段**：普通字段不会出现在构造器参数中；
2. **与其他构造器注解的区别**：
    - `@NoArgsConstructor`：生成无参构造器；
    - `@AllArgsConstructor`：生成包含所有字段的构造器；
    - `@RequiredArgsConstructor`：仅生成“必须初始化字段”的构造器（最常用）；
3. **空指针防护**：对`@NonNull`字段，生成的构造器会自动添加`null`检查，避免传入`null`；
4. **源码级注解**：注解的`@Retention(RetentionPolicy.SOURCE)`表示仅在源码阶段生效，编译后字节码中无该注解（Lombok在编译时完成代码生成）。

### 五、核心价值
省去手动编写构造器的重复代码，尤其在Spring依赖注入、领域模型（如不可变对象）等场景中，大幅简化代码，提升可读性。例如：
```java
// 手动编写（繁琐）
@Service
public class UserService {
    private final UserMapper userMapper;
    @NonNull private RedisTemplate redisTemplate;

    public UserService(UserMapper userMapper, RedisTemplate redisTemplate) {
        if (redisTemplate == null) {
            throw new NullPointerException("redisTemplate");
        }
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
    }
}

// 使用注解（简洁）
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;
    @NonNull private RedisTemplate redisTemplate;
}
```

总结：`@RequiredArgsConstructor`是Lombok简化构造器编写的核心注解，聚焦“必须初始化的字段”，兼顾语法合规性（final）和空指针防护（@NonNull），是日常开发中最常用的Lombok注解之一。