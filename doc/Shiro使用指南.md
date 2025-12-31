# Apache Shiro 使用指南

## 文档信息

- **创建日期**: 2025-12-29
- **框架版本**: Apache Shiro 1.x
- **适用场景**: Java Web 应用安全认证与授权

---

## 一、Shiro 简介

### 1.1 什么是 Shiro？

**Apache Shiro** 是一个强大且易用的 Java 安全框架，提供了：
- **认证（Authentication）**：验证用户身份
- **授权（Authorization）**：控制用户访问权限
- **会话管理（Session Management）**：管理用户会话
- **加密（Cryptography）**：提供加密工具
- **Web 集成**：与 Spring、Spring Boot 等框架集成

### 1.2 Shiro 核心优势

- ✅ **简单易用**：API 设计简洁，学习成本低
- ✅ **功能完整**：涵盖认证、授权、会话管理等完整功能
- ✅ **灵活配置**：支持多种配置方式（Java 配置、XML 配置）
- ✅ **轻量级**：依赖少，性能好
- ✅ **框架无关**：不依赖 Spring，可独立使用

### 1.3 Shiro vs Spring Security

| 特性 | Shiro | Spring Security |
|------|-------|----------------|
| **学习曲线** | 简单 | 较复杂 |
| **配置方式** | 简单直观 | 功能强大但复杂 |
| **框架依赖** | 无 | 依赖 Spring |
| **功能丰富度** | 基础功能完整 | 功能非常丰富 |
| **适用场景** | 中小型项目 | 大型企业级项目 |

---

## 二、核心概念

### 2.1 核心组件

#### 2.1.1 Subject（主体）

**Subject** 是 Shiro 的核心概念，代表当前用户：

```java
// 获取当前用户
Subject subject = SecurityUtils.getSubject();

// 判断是否已登录
if (subject.isAuthenticated()) {
    // 用户已登录
}

// 获取当前用户信息
String username = (String) subject.getPrincipal();
```

#### 2.1.2 SecurityManager（安全管理器）

**SecurityManager** 是 Shiro 的核心，管理所有 Subject：

```java
// 创建 SecurityManager
DefaultSecurityManager securityManager = new DefaultSecurityManager();

// 设置 Realm（数据源）
securityManager.setRealm(new MyRealm());

// 绑定到 SecurityUtils
SecurityUtils.setSecurityManager(securityManager);
```

#### 2.1.3 Realm（数据源）

**Realm** 是 Shiro 与应用程序数据源的桥梁，负责：
- 认证：验证用户身份
- 授权：获取用户权限

```java
public class MyRealm extends AuthorizingRealm {
    
    // 授权：获取用户权限
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        String username = (String) principals.getPrimaryPrincipal();
        // 从数据库查询用户权限
        Set<String> roles = userService.getRoles(username);
        Set<String> permissions = userService.getPermissions(username);
        
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        info.setRoles(roles);
        info.setStringPermissions(permissions);
        return info;
    }
    
    // 认证：验证用户身份
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) 
            throws AuthenticationException {
        UsernamePasswordToken upToken = (UsernamePasswordToken) token;
        String username = upToken.getUsername();
        
        // 从数据库查询用户
        User user = userService.findByUsername(username);
        if (user == null) {
            throw new UnknownAccountException("用户不存在");
        }
        
        // 返回认证信息
        return new SimpleAuthenticationInfo(
            username,                    // 用户名
            user.getPassword(),          // 密码
            ByteSource.Util.bytes(user.getSalt()),  // 盐值
            getName()                    // Realm 名称
        );
    }
}
```

### 2.2 认证流程

```
1. 用户提交用户名和密码
   ↓
2. 创建 UsernamePasswordToken
   ↓
3. Subject.login(token)
   ↓
4. SecurityManager 委托给 Realm
   ↓
5. Realm 查询数据库验证用户
   ↓
6. 验证成功：创建 Subject 会话
   验证失败：抛出 AuthenticationException
```

### 2.3 授权流程

```
1. 用户访问受保护资源
   ↓
2. Subject.isPermitted() 或 hasRole()
   ↓
3. SecurityManager 委托给 Realm
   ↓
4. Realm 查询用户权限/角色
   ↓
5. 返回授权结果
```

---

## 三、Spring Boot 集成

### 3.1 添加依赖

**pom.xml**：

```xml
<dependencies>
    <!-- Shiro Spring Boot Starter -->
    <dependency>
        <groupId>org.apache.shiro</groupId>
        <artifactId>shiro-spring-boot-web-starter</artifactId>
        <version>1.12.0</version>
    </dependency>
    
    <!-- Shiro Spring 集成 -->
    <dependency>
        <groupId>org.apache.shiro</groupId>
        <artifactId>shiro-spring</artifactId>
        <version>1.12.0</version>
    </dependency>
</dependencies>
```

### 3.2 配置 Shiro

#### 3.2.1 创建 Realm

```java
package com.lixiangyu.config;

import com.lixiangyu.service.UserService;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.util.ByteSource;
import org.springframework.beans.factory.annotation.Autowired;

public class UserRealm extends AuthorizingRealm {
    
    @Autowired
    private UserService userService;
    
    /**
     * 授权：获取用户角色和权限
     */
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        String username = (String) principals.getPrimaryPrincipal();
        
        // 查询用户角色
        Set<String> roles = userService.getRolesByUsername(username);
        // 查询用户权限
        Set<String> permissions = userService.getPermissionsByUsername(username);
        
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        info.setRoles(roles);
        info.setStringPermissions(permissions);
        return info;
    }
    
    /**
     * 认证：验证用户身份
     */
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) 
            throws AuthenticationException {
        UsernamePasswordToken upToken = (UsernamePasswordToken) token;
        String username = upToken.getUsername();
        
        // 从数据库查询用户
        User user = userService.findByUsername(username);
        if (user == null) {
            throw new UnknownAccountException("用户不存在");
        }
        if (user.getStatus() == 0) {
            throw new LockedAccountException("用户已被锁定");
        }
        
        // 返回认证信息（Shiro 会自动验证密码）
        return new SimpleAuthenticationInfo(
            username,
            user.getPassword(),
            ByteSource.Util.bytes(user.getSalt()),  // 盐值
            getName()
        );
    }
}
```

#### 3.2.2 配置 SecurityManager

```java
package com.lixiangyu.config;

import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class ShiroConfig {
    
    /**
     * 创建 Realm
     */
    @Bean
    public Realm userRealm() {
        UserRealm realm = new UserRealm();
        // 设置密码加密方式
        realm.setCredentialsMatcher(new HashedCredentialsMatcher("SHA-256"));
        return realm;
    }
    
    /**
     * 创建 SecurityManager
     */
    @Bean
    public SecurityManager securityManager(Realm realm) {
        DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();
        securityManager.setRealm(realm);
        return securityManager;
    }
    
    /**
     * 创建 ShiroFilterFactoryBean
     */
    @Bean
    public ShiroFilterFactoryBean shiroFilterFactoryBean(SecurityManager securityManager) {
        ShiroFilterFactoryBean factoryBean = new ShiroFilterFactoryBean();
        factoryBean.setSecurityManager(securityManager);
        
        // 配置登录页面
        factoryBean.setLoginUrl("/login");
        // 配置未授权页面
        factoryBean.setUnauthorizedUrl("/unauthorized");
        
        // 配置 URL 拦截规则
        Map<String, String> filterChainDefinitionMap = new LinkedHashMap<>();
        
        // 静态资源允许匿名访问
        filterChainDefinitionMap.put("/static/**", "anon");
        filterChainDefinitionMap.put("/css/**", "anon");
        filterChainDefinitionMap.put("/js/**", "anon");
        filterChainDefinitionMap.put("/images/**", "anon");
        
        // 登录接口允许匿名访问
        filterChainDefinitionMap.put("/login", "anon");
        filterChainDefinitionMap.put("/api/login", "anon");
        
        // 公共接口允许匿名访问
        filterChainDefinitionMap.put("/api/public/**", "anon");
        
        // 需要认证的接口
        filterChainDefinitionMap.put("/api/user/**", "authc");
        
        // 需要特定角色的接口
        filterChainDefinitionMap.put("/api/admin/**", "roles[admin]");
        
        // 需要特定权限的接口
        filterChainDefinitionMap.put("/api/system/**", "perms[system:manage]");
        
        // 其他接口需要认证
        filterChainDefinitionMap.put("/**", "authc");
        
        factoryBean.setFilterChainDefinitionMap(filterChainDefinitionMap);
        return factoryBean;
    }
}
```

### 3.3 URL 拦截器说明

| 拦截器 | 说明 | 示例 |
|--------|------|------|
| `anon` | 匿名访问，无需认证 | `/api/public/**` |
| `authc` | 需要认证 | `/api/user/**` |
| `roles[admin]` | 需要特定角色 | `/api/admin/**` |
| `perms[user:add]` | 需要特定权限 | `/api/user/add` |
| `user` | 已登录或记住我 | `/api/**` |
| `logout` | 登出 | `/logout` |

---

## 四、常用功能

### 4.1 用户登录

```java
@RestController
@RequestMapping("/api")
public class LoginController {
    
    @PostMapping("/login")
    public Result login(@RequestBody LoginRequest request) {
        try {
            Subject subject = SecurityUtils.getSubject();
            UsernamePasswordToken token = new UsernamePasswordToken(
                request.getUsername(),
                request.getPassword()
            );
            
            // 执行登录
            subject.login(token);
            
            // 登录成功，返回用户信息
            String username = (String) subject.getPrincipal();
            return Result.success("登录成功", username);
        } catch (UnknownAccountException e) {
            return Result.error("用户不存在");
        } catch (IncorrectCredentialsException e) {
            return Result.error("密码错误");
        } catch (LockedAccountException e) {
            return Result.error("账户已被锁定");
        } catch (AuthenticationException e) {
            return Result.error("认证失败：" + e.getMessage());
        }
    }
}
```

### 4.2 用户登出

```java
@PostMapping("/logout")
public Result logout() {
    Subject subject = SecurityUtils.getSubject();
    subject.logout();
    return Result.success("登出成功");
}
```

### 4.3 权限检查

#### 4.3.1 编程式权限检查

```java
@RestController
@RequestMapping("/api/user")
public class UserController {
    
    @GetMapping("/list")
    public Result list() {
        Subject subject = SecurityUtils.getSubject();
        
        // 检查是否有特定权限
        if (subject.isPermitted("user:list")) {
            // 有权限，返回数据
            return Result.success(userService.list());
        } else {
            return Result.error("无权限访问");
        }
    }
    
    @PostMapping("/add")
    public Result add(@RequestBody User user) {
        Subject subject = SecurityUtils.getSubject();
        
        // 检查是否有特定角色
        if (subject.hasRole("admin")) {
            userService.save(user);
            return Result.success("添加成功");
        } else {
            return Result.error("需要管理员权限");
        }
    }
}
```

#### 4.3.2 注解式权限检查

```java
// 需要添加依赖
// <dependency>
//     <groupId>org.apache.shiro</groupId>
//     <artifactId>shiro-spring</artifactId>
// </dependency>

@RestController
@RequestMapping("/api/user")
public class UserController {
    
    // 需要 user:list 权限
    @RequiresPermissions("user:list")
    @GetMapping("/list")
    public Result list() {
        return Result.success(userService.list());
    }
    
    // 需要 admin 角色
    @RequiresRoles("admin")
    @PostMapping("/add")
    public Result add(@RequestBody User user) {
        userService.save(user);
        return Result.success("添加成功");
    }
    
    // 需要认证
    @RequiresAuthentication
    @GetMapping("/info")
    public Result info() {
        String username = (String) SecurityUtils.getSubject().getPrincipal();
        return Result.success(userService.findByUsername(username));
    }
}
```

### 4.4 会话管理

```java
@RestController
@RequestMapping("/api")
public class SessionController {
    
    @GetMapping("/session/info")
    public Result getSessionInfo() {
        Subject subject = SecurityUtils.getSubject();
        Session session = subject.getSession();
        
        Map<String, Object> info = new HashMap<>();
        info.put("sessionId", session.getId());
        info.put("host", session.getHost());
        info.put("timeout", session.getTimeout());
        info.put("lastAccessTime", session.getLastAccessTime());
        
        return Result.success(info);
    }
    
    @PostMapping("/session/timeout")
    public Result setSessionTimeout(@RequestParam long timeout) {
        Subject subject = SecurityUtils.getSubject();
        Session session = subject.getSession();
        session.setTimeout(timeout);
        return Result.success("设置成功");
    }
}
```

### 4.5 密码加密

```java
@Service
public class PasswordService {
    
    /**
     * 生成随机盐值
     */
    public String generateSalt() {
        return new SecureRandomNumberGenerator().nextBytes(16).toBase64();
    }
    
    /**
     * 加密密码
     */
    public String encryptPassword(String password, String salt) {
        return new SimpleHash("SHA-256", password, ByteSource.Util.bytes(salt), 1024).toBase64();
    }
    
    /**
     * 验证密码
     */
    public boolean verifyPassword(String password, String salt, String encryptedPassword) {
        String hash = encryptPassword(password, salt);
        return hash.equals(encryptedPassword);
    }
}
```

---

## 五、高级功能

### 5.1 记住我（Remember Me）

```java
@Configuration
public class ShiroConfig {
    
    @Bean
    public CookieRememberMeManager rememberMeManager() {
        CookieRememberMeManager manager = new CookieRememberMeManager();
        // 设置加密密钥
        manager.setCipherKey(Base64.decode("4AvVhmFLUs0KTA3Kprsdag=="));
        return manager;
    }
    
    @Bean
    public SecurityManager securityManager(Realm realm, CookieRememberMeManager rememberMeManager) {
        DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();
        securityManager.setRealm(realm);
        securityManager.setRememberMeManager(rememberMeManager);
        return securityManager;
    }
}

// 使用
UsernamePasswordToken token = new UsernamePasswordToken(username, password);
token.setRememberMe(true);  // 启用记住我
subject.login(token);
```

### 5.2 缓存管理

```java
@Configuration
public class ShiroConfig {
    
    @Bean
    public CacheManager cacheManager() {
        // 使用内存缓存
        MemoryConstrainedCacheManager cacheManager = new MemoryConstrainedCacheManager();
        return cacheManager;
        
        // 或使用 Redis 缓存
        // RedisCacheManager cacheManager = new RedisCacheManager();
        // cacheManager.setRedisManager(redisManager);
        // return cacheManager;
    }
    
    @Bean
    public SecurityManager securityManager(Realm realm, CacheManager cacheManager) {
        DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();
        securityManager.setRealm(realm);
        securityManager.setCacheManager(cacheManager);
        return securityManager;
    }
}
```

### 5.3 多 Realm 支持

```java
@Configuration
public class ShiroConfig {
    
    @Bean
    public Realm userRealm() {
        return new UserRealm();
    }
    
    @Bean
    public Realm ldapRealm() {
        return new LdapRealm();
    }
    
    @Bean
    public SecurityManager securityManager(Realm userRealm, Realm ldapRealm) {
        DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();
        
        // 设置多个 Realm
        Collection<Realm> realms = new ArrayList<>();
        realms.add(userRealm);
        realms.add(ldapRealm);
        securityManager.setRealms(realms);
        
        return securityManager;
    }
}
```

---

## 六、最佳实践

### 6.1 密码加密

**推荐使用**：
- **SHA-256** 或 **SHA-512**：加盐哈希
- **BCrypt**：自适应哈希算法（推荐）

```java
// 使用 BCrypt
public class BCryptPasswordService {
    
    public String encryptPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }
    
    public boolean verifyPassword(String password, String hash) {
        return BCrypt.checkpw(password, hash);
    }
}
```

### 6.2 异常处理

```java
@ControllerAdvice
public class ShiroExceptionHandler {
    
    @ExceptionHandler(UnauthenticatedException.class)
    public Result handleUnauthenticated(UnauthenticatedException e) {
        return Result.error(401, "未登录，请先登录");
    }
    
    @ExceptionHandler(UnauthorizedException.class)
    public Result handleUnauthorized(UnauthorizedException e) {
        return Result.error(403, "无权限访问");
    }
    
    @ExceptionHandler(AuthenticationException.class)
    public Result handleAuthentication(AuthenticationException e) {
        return Result.error(401, "认证失败：" + e.getMessage());
    }
}
```

### 6.3 配置建议

1. **生产环境禁用开发工具**：
```java
filterChainDefinitionMap.put("/actuator/**", "roles[admin]");
```

2. **API 接口使用 Token 认证**：
```java
// 自定义 Token 认证
public class JwtToken implements AuthenticationToken {
    private String token;
    
    public JwtToken(String token) {
        this.token = token;
    }
    
    @Override
    public Object getPrincipal() {
        return token;
    }
    
    @Override
    public Object getCredentials() {
        return token;
    }
}
```

3. **会话超时设置**：
```java
@Bean
public SessionManager sessionManager() {
    DefaultWebSessionManager sessionManager = new DefaultWebSessionManager();
    sessionManager.setGlobalSessionTimeout(30 * 60 * 1000);  // 30分钟
    return sessionManager;
}
```

---

## 七、常见问题

### Q1: 如何实现 JWT Token 认证？

**A**: 需要自定义 Token 和 Realm：

```java
// 自定义 JWT Token
public class JwtToken implements AuthenticationToken {
    private String token;
    
    public JwtToken(String token) {
        this.token = token;
    }
    
    @Override
    public Object getPrincipal() {
        return token;
    }
    
    @Override
    public Object getCredentials() {
        return token;
    }
}

// 自定义 Realm
public class JwtRealm extends AuthorizingRealm {
    
    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof JwtToken;
    }
    
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        // 从 JWT 中解析权限
        String token = (String) principals.getPrimaryPrincipal();
        // 解析 JWT，获取权限
        return authorizationInfo;
    }
    
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) 
            throws AuthenticationException {
        JwtToken jwtToken = (JwtToken) token;
        // 验证 JWT
        if (jwtUtil.validateToken(jwtToken.getToken())) {
            return new SimpleAuthenticationInfo(
                jwtToken.getToken(),
                jwtToken.getToken(),
                getName()
            );
        }
        throw new AuthenticationException("JWT 验证失败");
    }
}
```

### Q2: 如何实现分布式会话？

**A**: 使用 Redis 存储会话：

```java
@Bean
public SessionDAO sessionDAO() {
    RedisSessionDAO sessionDAO = new RedisSessionDAO();
    sessionDAO.setRedisManager(redisManager);
    return sessionDAO;
}

@Bean
public SessionManager sessionManager(SessionDAO sessionDAO) {
    DefaultWebSessionManager sessionManager = new DefaultWebSessionManager();
    sessionManager.setSessionDAO(sessionDAO);
    return sessionManager;
}
```

### Q3: 如何实现动态权限控制？

**A**: 实现自定义的 Filter 或使用数据库配置：

```java
@Bean
public ShiroFilterFactoryBean shiroFilterFactoryBean(SecurityManager securityManager) {
    ShiroFilterFactoryBean factoryBean = new ShiroFilterFactoryBean();
    factoryBean.setSecurityManager(securityManager);
    
    // 从数据库加载权限配置
    Map<String, String> filterChainDefinitionMap = permissionService.loadFilterChainDefinitions();
    factoryBean.setFilterChainDefinitionMap(filterChainDefinitionMap);
    
    return factoryBean;
}
```

---

## 八、总结

### 8.1 Shiro 优势

- ✅ **简单易用**：API 设计简洁，学习成本低
- ✅ **功能完整**：认证、授权、会话管理一应俱全
- ✅ **灵活配置**：支持多种配置方式
- ✅ **轻量级**：依赖少，性能好

### 8.2 适用场景

- ✅ 中小型 Web 应用
- ✅ 需要简单认证授权的项目
- ✅ 不依赖 Spring 框架的项目
- ✅ 需要快速集成安全功能的项目

### 8.3 学习资源

- [Apache Shiro 官方文档](https://shiro.apache.org/)
- [Shiro Spring Boot 集成](https://shiro.apache.org/spring-boot.html)
- [Shiro 示例项目](https://github.com/apache/shiro)

---

**文档版本**: 1.0  
**最后更新**: 2025-12-29

