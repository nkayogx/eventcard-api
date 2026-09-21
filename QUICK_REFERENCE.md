## Quick Reference Guide - JWT Auth System

### 📋 Files at a Glance

**Core Security:**
- `security/JwtUtil.java` - Token generation & validation
- `security/JwtAuthFilter.java` - Request interceptor & authentication setup
- `config/SecurityConfig.java` - Spring Security configuration

**User Management:**
- `entity/User.java` - User entity implementing UserDetails
- `entity/Role.java` - Role entity with permissions
- `entity/Permission.java` - Permission entity
- `service/AuthService.java` - Register & login logic
- `service/CustomUserDetailsService.java` - User loader for Spring Security

**APIs:**
- `controller/AuthController.java` - Register & login endpoints
- `controller/UserManagementController.java` - User CRUD with authorization
- `controller/StudentController.java` - Student endpoints (already existed)

**Database:**
- `repository/UserRepository.java`, `RoleRepository.java`, `PermissionRepository.java`

**Configuration:**
- `seeder/DataSeeder.java` - Auto-creates roles & permissions
- `application.properties` - Database & JWT config
- `application-dev.properties` - Development configuration
- `pom.xml` - Maven dependencies

---

### 🔑 Key Concepts

| Concept | Explanation | Example |
|---------|-------------|---------|
| **JWT Token** | Stateless authentication credential | `eyJhbGci...` |
| **Authority** | Individual permission or role | `user:read` or `ROLE_USER` |
| **Permission** | Granular action right | `user:delete`, `admin:all` |
| **Role** | Collection of permissions | `ROLE_ADMIN` has multiple permissions |
| **Subject** | Token owner's identifier | Email: `john@example.com` |
| **Claims** | Data embedded in token | subject, authorities, expiration |
| **Signature** | Ensures token wasn't tampered | HMAC-SHA256 signature |

---

### 🔐 Authorization Flow

```
Request comes in
    ↓
JwtAuthFilter extracts token from "Authorization: Bearer {token}"
    ↓
JwtUtil validates token signature & expiration
    ↓
Extract username & authorities from token
    ↓
Create UsernamePasswordAuthenticationToken
    ↓
Set in SecurityContextHolder
    ↓
Controller method checks @PreAuthorize expression
    ↓
If authorized → Execute method
If not → Return 403 Forbidden
```

---

### 📝 API Quick Reference

#### Register
```bash
POST /api/auth/register
{
  "name": "John",
  "email": "john@example.com",
  "password": "pass123"
}
```

#### Login
```bash
POST /api/auth/login
{
  "email": "john@example.com",
  "password": "pass123"
}

Response:
{
  "token": "eyJhb...",
  "email": "john@example.com",
  "roles": ["ROLE_USER"]
}
```

#### Protected Request (All Endpoints)
```bash
GET /api/users
Authorization: Bearer eyJhb...
```

---

### 🎯 Default Roles & Permissions

```
ROLE_USER → [user:read]
ROLE_ADMIN → [user:read, user:create, user:delete, admin:all]
```

Create admin user manually:
```sql
INSERT INTO users VALUES (nextval('users_id_seq'), 'Admin', 'admin@test.com', '$2a$10$...hashed...', true);
INSERT INTO user_roles SELECT currval('users_id_seq'), id FROM roles WHERE name='ROLE_ADMIN';
```

---

### 🧪 Test Endpoints

```bash
# 1. Register
curl -X POST http://localhost:8181/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"John","email":"john@test.com","password":"test123"}'

# Save token from response as: TOKEN="eyJhb..."

# 2. Access Protected Endpoint
curl -X GET http://localhost:8181/api/users \
  -H "Authorization: Bearer $TOKEN"

# 3. Try Admin Endpoint (Should fail with 403)
curl -X GET http://localhost:8181/api/users/admin/statistics \
  -H "Authorization: Bearer $TOKEN"
```

---

### 🔧 Configuration Reference

**application.properties:**
```properties
# Must set these
spring.datasource.url=jdbc:postgresql://localhost:5432/demo_db
spring.datasource.username=postgres
spring.datasource.password=password

# JWT
jwt.secret=YourSecretKeyMinimum32CharactersLongForHS256
jwt.expiration=3600000  # 1 hour
```

**Environment Variables (Optional):**
```bash
export DB_URL=jdbc:postgresql://localhost:5432/demo_db
export DB_USER=postgres
export DB_PASSWORD=password
export JWT_SECRET=YourSecretKey
export JWT_EXPIRATION=3600000
```

---

### 🚨 Common Errors & Fixes

| Error | Cause | Fix |
|-------|-------|-----|
| `No qualifying bean of type 'AuthenticationManager'` | Missing @Bean | Add @Bean in SecurityConfig |
| `JWT validation failed: Signature verification failed` | Wrong jwt.secret | Ensure same secret in app.properties |
| `Token is invalid or expired` | Token expired or malformed | Regenerate token |
| `403 Forbidden` | Missing authority | Check @PreAuthorize matches user's permissions |
| `Database connection refused` | PostgreSQL not running | Start PostgreSQL server |

---

### 📚 Important Methods

**JwtUtil:**
- `generateToken(Authentication)` - Create JWT
- `validateToken(String)` - Check if valid
- `extractUsername(String)` - Get email from token
- `extractAuthorities(String)` - Get permissions from token

**AuthService:**
- `register(RegisterRequest)` - Create new user
- `login(LoginRequest)` - Authenticate user

**CustomUserDetailsService:**
- `loadUserByUsername(String)` - Load user from DB

**JwtAuthFilter:**
- `doFilterInternal()` - Validate request token

---

### 🔐 Security Best Practices

✅ **DO:**
- Use HTTPS in production
- Store JWT secret in environment variables
- Use BCrypt for passwords
- Set appropriate token expiration
- Validate token on every request
- Use @PreAuthorize for method security

❌ **DON'T:**
- Expose JWT secret in code
- Store sensitive data in JWT claims
- Use weak JWT secret (< 32 chars)
- Trust unvalidated tokens
- Store password in plain text
- Use HTTP (always HTTPS)

---

### 📊 Database Schema Quick View

```
Users (1) ──┬── User_Roles ──┬─── (Many) Roles (1) ──┬─── Role_Permissions ──┬─── (Many) Permissions
            │                │                        │                      │
            └─ id            ├─ user_id              └─ id                  └─ permission_id
               name          └─ role_id                 name                    name
               email                                                             
               password                                                          
               enabled                                                           
```

---

### ⚙️ Maven Build

```bash
# Clean and compile
mvn clean compile

# Build JAR
mvn clean package

# Run application
mvn spring-boot:run

# Run with dev profile
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"

# Skip tests
mvn clean package -DskipTests
```

---

### 🎨 @PreAuthorize Examples

```java
// Requires specific permission
@PreAuthorize("hasAuthority('user:read')")

// Requires specific role
@PreAuthorize("hasRole('ROLE_ADMIN')")

// Either permission OR role
@PreAuthorize("hasAuthority('user:delete') or hasRole('ROLE_ADMIN')")

// Both permissions AND role
@PreAuthorize("hasAuthority('user:read') and hasRole('ROLE_ADMIN')")

// NOT logic
@PreAuthorize("!hasRole('ROLE_USER')")

// Complex expression
@PreAuthorize("hasRole('ROLE_ADMIN') or (hasAuthority('user:read') and hasAuthority('user:delete'))")
```

---

### 🔄 Request Lifecycle

```
1. Client sends request
   GET /api/users
   Authorization: Bearer {token}

2. JwtAuthFilter intercepts
   ├─ Extract token from header
   ├─ Validate signature & expiration
   ├─ Extract username & authorities
   └─ Set SecurityContextHolder

3. DispatcherServlet routes to controller

4. Controller method checks @PreAuthorize
   ├─ Get authentication from SecurityContextHolder
   ├─ Evaluate expression
   └─ Allow or deny access

5. If allowed, execute method
   ├─ Query database
   └─ Return response

6. Response sent to client
```

---

### 📦 Dependencies Added

```xml
<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>

<!-- Lombok -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

---

### 🎯 Next Steps

1. **Update PostgreSQL credentials** in application.properties
2. **Change JWT secret** to strong random value
3. **Run the application** - DataSeeder will auto-create roles & permissions
4. **Test endpoints** using Postman collection
5. **Create admin user** for testing admin endpoints
6. **Review code** and customize as needed
7. **Deploy** with HTTPS and environment variables

---

### 📞 Support Resources

- **JWT Debugger:** https://jwt.io/
- **Spring Security Docs:** https://spring.io/projects/spring-security
- **JJWT GitHub:** https://github.com/jwtk/jjwt
- **Spring Boot Guide:** https://spring.io/guides/gs/securing-web/

---

**System Status: ✅ READY FOR USE**

All components implemented and tested. Ready for production deployment with proper configuration and security best practices.
