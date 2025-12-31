# Spring Security 使用指南

## 文档信息

- **创建日期**: 2025-12-29
- **框架版本**: Spring Security 5.x / 6.x
- **适用场景**: Spring Boot 应用安全认证与授权

---

## 一、Spring Security 简介

### 1.1 什么是 Spring Security？

**Spring Security** 是 Spring 生态系统中的安全框架，提供了：
- **认证（Authentication）**：验证用户身份
- **授权（Authorization）**：控制用户访问权限
- **防护攻击**：CSRF、XSS、SQL 注入等
- **会话管理**：管理用户会话
- **OAuth2 / JWT**：支持现代认证方式

### 1.2 Spring Security 核心优势

- ✅ **功能强大**：提供完整的企业级安全解决方案
- ✅ **深度集成**：与 Spring 框架无缝集成
- ✅ **高度可配置**：支持多种认证和授权方式
- ✅ **安全防护**：内置多种安全防护机制
- ✅ **社区活跃**：Spring 官方维护，文档完善

### 1.3 Spring Security vs Shiro

| 特性 | Spring Security | Shiro |
|------|----------------|-------|
| **学习曲线** | 较复杂 | 简单 |
| **配置复杂度** | 功能强大但复杂 | 简单直观 |
| **框架依赖** | 依赖 Spring | 无 |
| **功能丰富度** | 非常丰富 | 基础功能完整 |
| **适用场景** | 大型企业级项目 | 中小型项目 |

---

## 二、核心概念

### 2.1 核心组件

#### 2.1.1 SecurityContext（安全上下文）

**SecurityContext** 存储当前认证用户信息：

```java
// 获取当前认证用户
Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

if (authentication != null && authentication.isAuthenticated()) {
    String username = authentication.getName();
    Object principal = authentication.getPrincipal();
    Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
}
```

#### 2.1.2 Authentication（认证对象）

**Authentication** 代表认证信息：

```java
// 创建认证对象
UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
    username,
    password
);

// 执行认证
Authentication authentication = authenticationManager.authenticate(token);

// 设置到安全上下文
SecurityContextHolder.getContext().setAuthentication(authentication);
```

#### 2.1.3 UserDetails（用户详情）

**UserDetails** 封装用户信息：

```java
// 实现 UserDetails
public class UserDetailsImpl implements UserDetails {
    private String username;
    private String password;
    private Collection<? extends GrantedAuthority> authorities;
    private boolean enabled;
    private boolean accountNonExpired;
    private boolean accountNonLocked;
    private boolean credentialsNonExpired;
    
    // 实现接口方法
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
    
    @Override
    public String getPassword() {
        return password;
    }
    
    @Override
    public String getUsername() {
        return username;
    }
    
    // ... 其他方法
}
```

#### 2.1.4 UserDetailsService（用户服务）

**UserDetailsService** 负责加载用户信息：

```java
@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    
    @Autowired
    private UserService userService;
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userService.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在：" + username);
        }
        
        // 查询用户权限
        List<GrantedAuthority> authorities = userService.getAuthorities(user.getId());
        
        return User.builder()
            .username(user.getUsername())
            .password(user.getPassword())
            .authorities(authorities)
            .enabled(user.getEnabled())
            .accountNonExpired(true)
            .accountNonLocked(!user.getLocked())
            .credentialsNonExpired(true)
            .build();
    }
}
```

### 2.2 认证流程

```
1. 用户提交用户名和密码
   ↓
2. 创建 UsernamePasswordAuthenticationToken
   ↓
3. AuthenticationManager.authenticate(token)
   ↓
4. 委托给 ProviderManager
   ↓
5. ProviderManager 委托给 DaoAuthenticationProvider
   ↓
6. DaoAuthenticationProvider 调用 UserDetailsService
   ↓
7. UserDetailsService 查询数据库
   ↓
8. PasswordEncoder 验证密码
   ↓
9. 验证成功：创建 Authentication 对象
   验证失败：抛出 AuthenticationException
```

### 2.3 授权流程

```
1. 用户访问受保护资源
   ↓
2. FilterSecurityInterceptor 拦截请求
   ↓
3. 从 SecurityContext 获取 Authentication
   ↓
4. 获取用户权限（GrantedAuthority）
   ↓
5. 与资源所需权限对比
   ↓
6. 有权限：允许访问
   无权限：抛出 AccessDeniedException
```

---

## 三、Spring Boot 集成

### 3.1 添加依赖

**pom.xml**：

```xml
<dependencies>
    <!-- Spring Security -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    
    <!-- Spring Security OAuth2（可选） -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-client</artifactId>
    </dependency>
    
    <!-- JWT 支持（可选） -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt</artifactId>
        <version>0.9.1</version>
    </dependency>
</dependencies>
```

### 3.2 基础配置

#### 3.2.1 Spring Boot 2.x 配置方式

```java
package com.lixiangyu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    
    /**
     * 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    /**
     * 配置 HTTP 安全
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（API 开发时通常禁用）
            .csrf().disable()
            // 配置请求授权
            .authorizeRequests()
                // 允许匿名访问的接口
                .antMatchers("/api/public/**").permitAll()
                .antMatchers("/api/login").permitAll()
                // 需要认证的接口
                .antMatchers("/api/user/**").authenticated()
                // 需要特定角色的接口
                .antMatchers("/api/admin/**").hasRole("ADMIN")
                // 需要特定权限的接口
                .antMatchers("/api/system/**").hasAuthority("system:manage")
                // 其他请求需要认证
                .anyRequest().authenticated()
            .and()
            // 配置表单登录
            .formLogin()
                .loginPage("/login")
                .loginProcessingUrl("/api/login")
                .successHandler(authenticationSuccessHandler())
                .failureHandler(authenticationFailureHandler())
                .permitAll()
            .and()
            // 配置登出
            .logout()
                .logoutUrl("/api/logout")
                .logoutSuccessHandler(logoutSuccessHandler())
                .permitAll();
    }
    
    /**
     * 认证成功处理器
     */
    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":200,\"message\":\"登录成功\"}");
        };
    }
    
    /**
     * 认证失败处理器
     */
    @Bean
    public AuthenticationFailureHandler authenticationFailureHandler() {
        return (request, response, exception) -> {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"登录失败：" + exception.getMessage() + "\"}");
        };
    }
    
    /**
     * 登出成功处理器
     */
    @Bean
    public LogoutSuccessHandler logoutSuccessHandler() {
        return (request, response, authentication) -> {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":200,\"message\":\"登出成功\"}");
        };
    }
}
```

#### 3.2.2 Spring Boot 3.x 配置方式（推荐）

```java
package com.lixiangyu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    /**
     * 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    /**
     * 安全过滤器链（Spring Boot 3.x 推荐方式）
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF
            .csrf(csrf -> csrf.disable())
            // 配置请求授权
            .authorizeHttpRequests(authorize -> authorize
                // 允许匿名访问
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/login").permitAll()
                // 需要认证
                .requestMatchers("/api/user/**").authenticated()
                // 需要角色
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // 需要权限
                .requestMatchers("/api/system/**").hasAuthority("system:manage")
                // 其他请求需要认证
                .anyRequest().authenticated()
            )
            // 配置表单登录
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/api/login")
                .permitAll()
            )
            // 配置登出
            .logout(logout -> logout
                .logoutUrl("/api/logout")
                .permitAll()
            )
            // 配置会话管理（无状态，适用于 JWT）
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );
        
        return http.build();
    }
}
```

### 3.3 配置 UserDetailsService

```java
@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    
    @Autowired
    private UserService userService;
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 查询用户
        User user = userService.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在：" + username);
        }
        
        // 查询用户权限
        List<GrantedAuthority> authorities = userService.getAuthorities(user.getId())
            .stream()
            .map(permission -> new SimpleGrantedAuthority(permission))
            .collect(Collectors.toList());
        
        // 构建 UserDetails
        return User.builder()
            .username(user.getUsername())
            .password(user.getPassword())
            .authorities(authorities)
            .enabled(user.getEnabled())
            .accountNonExpired(true)
            .accountNonLocked(!user.getLocked())
            .credentialsNonExpired(true)
            .build();
    }
}
```

---

## 四、常用功能

### 4.1 用户登录

#### 4.1.1 表单登录

```java
@RestController
@RequestMapping("/api")
public class LoginController {
    
    @Autowired
    private AuthenticationManager authenticationManager;
    
    @PostMapping("/login")
    public Result login(@RequestBody LoginRequest request) {
        try {
            // 创建认证对象
            UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                request.getUsername(),
                request.getPassword()
            );
            
            // 执行认证
            Authentication authentication = authenticationManager.authenticate(token);
            
            // 设置到安全上下文
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // 登录成功，返回用户信息
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return Result.success("登录成功", userDetails);
        } catch (BadCredentialsException e) {
            return Result.error("用户名或密码错误");
        } catch (DisabledException e) {
            return Result.error("账户已被禁用");
        } catch (LockedException e) {
            return Result.error("账户已被锁定");
        } catch (AuthenticationException e) {
            return Result.error("认证失败：" + e.getMessage());
        }
    }
}
```

#### 4.1.2 JWT Token 登录

```java
@RestController
@RequestMapping("/api")
public class JwtLoginController {
    
    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    
    @PostMapping("/login")
    public Result login(@RequestBody LoginRequest request) {
        try {
            // 执行认证
            UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                request.getUsername(),
                request.getPassword()
            );
            Authentication authentication = authenticationManager.authenticate(token);
            
            // 生成 JWT Token
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String jwtToken = jwtTokenUtil.generateToken(userDetails);
            
            // 返回 Token
            Map<String, Object> data = new HashMap<>();
            data.put("token", jwtToken);
            data.put("username", userDetails.getUsername());
            return Result.success("登录成功", data);
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
    SecurityContextHolder.clearContext();
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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // 检查是否有特定权限
        if (authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("user:list"))) {
            return Result.success(userService.list());
        } else {
            return Result.error("无权限访问");
        }
    }
    
    @PostMapping("/add")
    public Result add(@RequestBody User user) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // 检查是否有特定角色
        if (authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
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
@RestController
@RequestMapping("/api/user")
@PreAuthorize("hasRole('USER')")  // 类级别：需要 USER 角色
public class UserController {
    
    // 需要 user:list 权限
    @PreAuthorize("hasAuthority('user:list')")
    @GetMapping("/list")
    public Result list() {
        return Result.success(userService.list());
    }
    
    // 需要 admin 角色
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/add")
    public Result add(@RequestBody User user) {
        userService.save(user);
        return Result.success("添加成功");
    }
    
    // 需要多个权限之一
    @PreAuthorize("hasAnyAuthority('user:edit', 'user:admin')")
    @PutMapping("/update")
    public Result update(@RequestBody User user) {
        userService.update(user);
        return Result.success("更新成功");
    }
    
    // 方法参数权限检查
    @PreAuthorize("hasAuthority('user:delete') and #id > 0")
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success("删除成功");
    }
}

// 启用方法安全
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class MethodSecurityConfig {
}
```

### 4.4 获取当前用户

```java
@RestController
@RequestMapping("/api")
public class UserController {
    
    @GetMapping("/current")
    public Result getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return Result.error("未登录");
        }
        
        // 获取用户名
        String username = authentication.getName();
        
        // 获取用户详情
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        
        // 获取权限
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("username", username);
        userInfo.put("authorities", authorities);
        
        return Result.success(userInfo);
    }
    
    // 使用 @AuthenticationPrincipal 注解
    @GetMapping("/profile")
    public Result getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails.getUsername();
        return Result.success(userService.findByUsername(username));
    }
}
```

### 4.5 密码加密

```java
@Service
public class PasswordService {
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    /**
     * 加密密码
     */
    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
    
    /**
     * 验证密码
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}

// 配置密码编码器
@Configuration
public class SecurityConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        // 使用 BCrypt（推荐）
        return new BCryptPasswordEncoder();
        
        // 或使用其他编码器
        // return new Argon2PasswordEncoder();
        // return new Pbkdf2PasswordEncoder();
    }
}
```

---

## 五、JWT Token 认证

### 5.1 JWT 工具类

```java
@Component
public class JwtTokenUtil {
    
    private static final String SECRET = "your-secret-key";
    private static final long EXPIRATION = 86400000; // 24小时
    
    /**
     * 生成 Token
     */
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", userDetails.getUsername());
        claims.put("authorities", userDetails.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList()));
        claims.put("created", new Date());
        
        return Jwts.builder()
            .setClaims(claims)
            .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
            .signWith(SignatureAlgorithm.HS512, SECRET)
            .compact();
    }
    
    /**
     * 从 Token 中获取用户名
     */
    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }
    
    /**
     * 验证 Token
     */
    public Boolean validateToken(String token, UserDetails userDetails) {
        String username = getUsernameFromToken(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
    
    private Boolean isTokenExpired(String token) {
        Date expiration = getClaimFromToken(token, Claims::getExpiration);
        return expiration.before(new Date());
    }
    
    private <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }
    
    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser().setSigningKey(SECRET).parseClaimsJws(token).getBody();
    }
}
```

### 5.2 JWT 认证过滤器

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    
    @Autowired
    private UserDetailsService userDetailsService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain chain) throws ServletException, IOException {
        String token = getTokenFromRequest(request);
        
        if (token != null && jwtTokenUtil.validateToken(token, null)) {
            String username = jwtTokenUtil.getUsernameFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            
            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        
        chain.doFilter(request, response);
    }
    
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

### 5.3 配置 JWT 过滤器

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/login").permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
```

---

## 六、高级功能

### 6.1 自定义认证提供者

```java
@Component
public class CustomAuthenticationProvider implements AuthenticationProvider {
    
    @Autowired
    private UserDetailsService userDetailsService;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();
        
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        
        if (passwordEncoder.matches(password, userDetails.getPassword())) {
            return new UsernamePasswordAuthenticationToken(
                userDetails,
                password,
                userDetails.getAuthorities()
            );
        } else {
            throw new BadCredentialsException("密码错误");
        }
    }
    
    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
```

### 6.2 异常处理

```java
@ControllerAdvice
public class SecurityExceptionHandler {
    
    @ExceptionHandler(AuthenticationException.class)
    public Result handleAuthentication(AuthenticationException e) {
        return Result.error(401, "认证失败：" + e.getMessage());
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public Result handleAccessDenied(AccessDeniedException e) {
        return Result.error(403, "无权限访问");
    }
    
    @ExceptionHandler(BadCredentialsException.class)
    public Result handleBadCredentials(BadCredentialsException e) {
        return Result.error(401, "用户名或密码错误");
    }
}
```

### 6.3 跨域配置

```java
@Configuration
public class CorsConfig {
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

// 在 SecurityConfig 中配置
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        // ... 其他配置
    return http.build();
}
```

---

## 七、项目实际配置

### 7.1 当前项目配置

根据项目中的 `SecurityConfig.java`，当前配置如下：

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（跨站请求伪造）保护，方便 API 测试
            .csrf().disable()
            // 配置请求授权
            .authorizeRequests()
                // 允许所有请求无需认证
                .anyRequest().permitAll();
    }
}
```

**配置说明**：
- ✅ 所有接口允许匿名访问（开发测试阶段）
- ✅ 禁用了 CSRF 保护（API 开发时通常禁用）
- ✅ 保留了 Spring Security 依赖，便于后续扩展

### 7.2 生产环境配置建议

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    
    @Autowired
    private UserDetailsService userDetailsService;
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService)
            .passwordEncoder(passwordEncoder());
    }
    
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .authorizeRequests()
                // 公共接口
                .antMatchers("/api/public/**").permitAll()
                .antMatchers("/api/login").permitAll()
                // 用户接口需要认证
                .antMatchers("/api/user/**").authenticated()
                // 管理员接口需要 ADMIN 角色
                .antMatchers("/api/admin/**").hasRole("ADMIN")
                // 其他接口需要认证
                .anyRequest().authenticated()
            .and()
            // 配置 JWT 过滤器
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            // 无状态会话（JWT）
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }
}
```

---

## 八、最佳实践

### 8.1 密码加密

**推荐使用 BCrypt**：

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);  // 强度参数（4-31，默认10）
}
```

### 8.2 安全配置建议

1. **生产环境启用 CSRF**：
```java
.csrf(csrf -> csrf
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
)
```

2. **配置安全响应头**：
```java
.headers(headers -> headers
    .contentSecurityPolicy("default-src 'self'")
    .frameOptions().deny()
)
```

3. **限制登录尝试次数**：
```java
// 使用 Spring Security 的 Remember Me 或自定义过滤器
```

### 8.3 性能优化

1. **使用缓存**：
```java
@Bean
public UserDetailsService userDetailsService() {
    return new CachingUserDetailsService(new UserDetailsServiceImpl());
}
```

2. **无状态会话（JWT）**：
```java
.sessionManagement(session -> session
    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
)
```

---

## 九、常见问题

### Q1: WebSecurityConfigurerAdapter 已废弃怎么办？

**A**: Spring Boot 3.x 使用 `SecurityFilterChain` Bean：

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(authorize -> authorize
            .anyRequest().permitAll()
        );
    return http.build();
}
```

### Q2: 如何实现动态权限控制？

**A**: 实现 `FilterInvocationSecurityMetadataSource`：

```java
@Component
public class DynamicSecurityMetadataSource implements FilterInvocationSecurityMetadataSource {
    
    @Autowired
    private PermissionService permissionService;
    
    @Override
    public Collection<ConfigAttribute> getAttributes(Object object) throws IllegalArgumentException {
        String url = ((FilterInvocation) object).getRequestUrl();
        // 从数据库查询 URL 对应的权限
        List<String> permissions = permissionService.getPermissionsByUrl(url);
        return permissions.stream()
            .map(permission -> new SecurityConfig(permission))
            .collect(Collectors.toList());
    }
}
```

### Q3: 如何实现分布式会话？

**A**: 使用 Spring Session：

```xml
<dependency>
    <groupId>org.springframework.session</groupId>
    <artifactId>spring-session-data-redis</artifactId>
</dependency>
```

### Q4: 如何实现 OAuth2 认证？

**A**: 使用 Spring Security OAuth2：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

---

## 十、总结

### 10.1 Spring Security 优势

- ✅ **功能强大**：提供完整的企业级安全解决方案
- ✅ **深度集成**：与 Spring 框架无缝集成
- ✅ **高度可配置**：支持多种认证和授权方式
- ✅ **安全防护**：内置多种安全防护机制

### 10.2 适用场景

- ✅ 大型企业级项目
- ✅ 需要复杂权限控制的系统
- ✅ 需要 OAuth2 / JWT 的项目
- ✅ Spring 生态系统项目

### 10.3 学习资源

- [Spring Security 官方文档](https://spring.io/projects/spring-security)
- [Spring Security 参考文档](https://docs.spring.io/spring-security/reference/)
- [Spring Boot Security 指南](https://spring.io/guides/topicals/spring-security-architecture)

---

**文档版本**: 1.0  
**最后更新**: 2025-12-29

